package com.soldin.register;

import com.soldin.register.commands.AuthCommands;
import com.soldin.register.listeners.AuthListener;
import com.soldin.register.storage.SqlStorage;
import com.soldin.register.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SoldinRegister extends JavaPlugin {
    private static SoldinRegister instance;
    private Storage storage;
    // authenticated players and optional session expiry
    private final Map<UUID, Long> authenticated = new ConcurrentHashMap<>();
    // login attempts and auth deadline
    private final Map<UUID, Integer> attemptsLeft = new ConcurrentHashMap<>();
    private final Map<UUID, Long> authDeadline = new ConcurrentHashMap<>();

    public static SoldinRegister get() { return instance; }
    public Storage storage() { return storage; }

    public int getAttemptsLeft(UUID u){
        return attemptsLeft.getOrDefault(u, getConfig().getInt("security.max_login_attempts", 3));
    }
    public void resetAttempts(UUID u){ attemptsLeft.remove(u); }
    public void decAttempt(UUID u){
        int left = getAttemptsLeft(u) - 1; attemptsLeft.put(u, left);
    }
    public long getAuthDeadline(UUID u){ return authDeadline.getOrDefault(u, 0L); }
    public void setAuthDeadline(UUID u, long when){ authDeadline.put(u, when); }
    public void clearAuthDeadline(UUID u){ authDeadline.remove(u); }

    public boolean isAuthenticated(UUID uuid) {
        if (!getConfig().getBoolean("session.enable", true)) return authenticated.containsKey(uuid);
        Long exp = authenticated.get(uuid);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) { authenticated.remove(uuid); return false; }
        return true;
    }
    public void setAuthenticated(UUID uuid) {
        long ttlMs = getConfig().getLong("session.ttl_ms", 3600_000L);
        long exp = getConfig().getBoolean("session.enable", true) ? (System.currentTimeMillis() + ttlMs) : Long.MAX_VALUE;
        authenticated.put(uuid, exp);
        resetAttempts(uuid);
        clearAuthDeadline(uuid);
    }
    public void revokeAuthenticated(UUID uuid) { authenticated.remove(uuid); }

    @Override public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);

        storage = new SqlStorage(this);
        try {
            storage.connect();
            storage.init();
        } catch (Exception e) {
            getLogger().severe("[SoldinRegister] DB connect failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        AuthCommands handler = new AuthCommands(this);
        Objects.requireNonNull(getCommand("register")).setExecutor(handler);
        Objects.requireNonNull(getCommand("reg")).setExecutor(handler);
        Objects.requireNonNull(getCommand("login")).setExecutor(handler);
        Objects.requireNonNull(getCommand("l")).setExecutor(handler);
        Objects.requireNonNull(getCommand("changepass")).setExecutor(handler);
        Objects.requireNonNull(getCommand("soldinregister")).setExecutor(handler);

        Bukkit.getPluginManager().registerEvents(new AuthListener(this), this);
        getLogger().info("SoldinRegister enabled.");
    }

    @Override public void onDisable() {
        try { if (storage != null) storage.close(); } catch (Exception ignored) {}
        authenticated.clear();
        attemptsLeft.clear();
        authDeadline.clear();
    }
}