package com.soldin.register;

import com.soldin.register.commands.AuthCommands;
import com.soldin.register.listeners.AuthListener;
import com.soldin.register.storage.SqlStorage;
import com.soldin.register.storage.Storage;
import com.soldin.register.discord.DiscordBotManager;
import com.soldin.register.telegram.TelegramBotManager;
import com.soldin.register.model.DsLink;
import com.soldin.register.model.TgLink;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SoldinRegister extends JavaPlugin {
    private static SoldinRegister instance;
    private Storage storage;
    private TelegramBotManager telegramBot;
    private DiscordBotManager discordBot;

    private YamlConfiguration messages;

    private final Map<UUID, String> pendingTelegramIp = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingDiscordIp = new ConcurrentHashMap<>();
    // authenticated players and optional session expiry
    private final Map<UUID, Long> authenticated = new ConcurrentHashMap<>();
    // login attempts and auth deadline
    private final Map<UUID, Integer> attemptsLeft = new ConcurrentHashMap<>();
    private final Map<UUID, Long> authDeadline = new ConcurrentHashMap<>();

    public static SoldinRegister get() { return instance; }
    public Storage storage() { return storage; }

    public String msg(String key) {
        String v = null;
        if (messages != null) v = messages.getString(key);
        if (v == null) v = getConfig().getString("messages." + key);
        if (v == null) v = key;
        return ChatColor.translateAlternateColorCodes('&', v);
    }

    public String msg(String key, java.util.Map<String, String> ph) {
        String s = msg(key);
        if (ph != null) {
            for (java.util.Map.Entry<String, String> e : ph.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue());
                s = s.replace("%" + e.getKey() + "%", e.getValue());
            }
        }
        return s;
    }

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
        // Telegram + Discord configs
        saveResource("tg.yml", false);
        saveResource("ds.yml", false);

        // load base config + messages first
        reloadConfig();
        java.io.File msgFile = new java.io.File(getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(msgFile);

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

        // start bots after storage is ready
        reloadBots();

        getLogger().info("SoldinRegister enabled.");
    }

    @Override public void onDisable() {
        try { if (storage != null) storage.close(); } catch (Exception ignored) {}
        try { if (telegramBot != null) telegramBot.shutdown(); } catch (Exception ignored) {}
        try { if (discordBot != null) discordBot.shutdown(); } catch (Exception ignored) {}
        authenticated.clear();
        attemptsLeft.clear();
        authDeadline.clear();
    }

    public TelegramBotManager getTelegramBot() {
        return telegramBot;
    }

    public DiscordBotManager getDiscordBot() {
        return discordBot;
    }

    /** Reloads config + message file (messages.yml). */
    public void reloadTexts() {
        reloadConfig();
        java.io.File msgFile = new java.io.File(getDataFolder(), "messages.yml");
        this.messages = YamlConfiguration.loadConfiguration(msgFile);
    }

    /** Restarts Telegram/Discord bots (tg.yml + ds.yml). Call only after storage is ready. */
    public void reloadBots() {
        // (re)start Telegram
        java.io.File tgFile = new java.io.File(getDataFolder(), "tg.yml");
        if (telegramBot != null) telegramBot.shutdown();
        telegramBot = new TelegramBotManager(this, tgFile);
        if (telegramBot.isEnabled()) telegramBot.start();

        // (re)start Discord
        java.io.File dsFile = new java.io.File(getDataFolder(), "ds.yml");
        if (discordBot != null) discordBot.shutdown();
        discordBot = new DiscordBotManager(this, dsFile);
        if (discordBot.isEnabled()) discordBot.start();
    }

    // Вызов из AuthCommands после проверки пароля/2FA.
    // Возвращает true, если требуется подтверждение через Telegram и игрок ещё НЕ авторизован.
    public boolean handleTelegramLogin(Player player, com.soldin.register.model.UserRecord user, String ip) {
        if (telegramBot == null || !telegramBot.isEnabled()) return false;
        if (storage == null) return false;

        TgLink link = storage.getTgLinkByUUID(user.uuid);
        if (link == null) return false;

        long now = System.currentTimeMillis();
        boolean needConfirm = link.lastIp == null
                || !ip.equals(link.lastIp)
                || (now - link.lastConfirm) > (4L * 60L * 60L * 1000L); // 4 часа

        if (!needConfirm) {
            // просто обновим IP и время подтверждения
            link.lastIp = ip;
            link.lastConfirm = now;
            storage.saveOrUpdateTgLink(link);
            return false;
        }

        // Требуется подтверждение: запомним IP и отправим запрос в Telegram.
        pendingTelegramIp.put(user.uuid, ip);
        telegramBot.sendLoginRequest(user.uuid, player.getName(), ip);
        player.sendMessage(msg("telegram_login_pending"));
        return true;
    }

    public boolean handleDiscordLogin(Player player, com.soldin.register.model.UserRecord user, String ip) {
        if (discordBot == null || !discordBot.isEnabled()) return false;
        if (storage == null) return false;

        DsLink link = storage.getDsLinkByUUID(user.uuid);
        if (link == null) return false;

        long now = System.currentTimeMillis();
        boolean needConfirm = link.lastIp == null
                || !ip.equals(link.lastIp)
                || (now - link.lastConfirm) > (4L * 60L * 60L * 1000L);

        if (!needConfirm) {
            link.lastIp = ip;
            link.lastConfirm = now;
            storage.saveOrUpdateDsLink(link);
            return false;
        }

        pendingDiscordIp.put(user.uuid, ip);
        discordBot.sendLoginRequest(user.uuid, player.getName(), ip);
        player.sendMessage(msg("discord_login_pending"));
        return true;
    }

    // Telegram нажал "Разрешить"
    public void approveTelegramLogin(UUID uuid) {
        String ip = pendingTelegramIp.remove(uuid);
        long now = System.currentTimeMillis();
        if (storage != null) {
            TgLink link = storage.getTgLinkByUUID(uuid);
            if (link != null) {
                if (ip != null) {
                    link.lastIp = ip;
                }
                link.lastConfirm = now;
                storage.saveOrUpdateTgLink(link);
            }
        }
        Player p = getServer().getPlayer(uuid);
        if (p != null && p.isOnline() && !isAuthenticated(uuid)) {
            setAuthenticated(uuid);
            p.sendMessage(msg("telegram_login_approved_ingame"));
        }
    }

    // Telegram нажал "Отклонить"
    public void denyTelegramLogin(UUID uuid) {
        pendingTelegramIp.remove(uuid);
        Player p = getServer().getPlayer(uuid);
        if (p != null && p.isOnline() && !isAuthenticated(uuid)) {
            p.kickPlayer(msg("telegram_login_denied_kick"));
        }
    }

    public void approveDiscordLogin(UUID uuid) {
        String ip = pendingDiscordIp.remove(uuid);
        long now = System.currentTimeMillis();
        if (storage != null) {
            DsLink link = storage.getDsLinkByUUID(uuid);
            if (link != null) {
                if (ip != null) link.lastIp = ip;
                link.lastConfirm = now;
                storage.saveOrUpdateDsLink(link);
            }
        }
        Player p = getServer().getPlayer(uuid);
        if (p != null && p.isOnline() && !isAuthenticated(uuid)) {
            setAuthenticated(uuid);
            p.sendMessage(msg("discord_login_approved_ingame"));
        }
    }

    public void denyDiscordLogin(UUID uuid) {
        pendingDiscordIp.remove(uuid);
        Player p = getServer().getPlayer(uuid);
        if (p != null && p.isOnline() && !isAuthenticated(uuid)) {
            p.kickPlayer(msg("discord_login_denied_kick"));
        }
    }

    // Сообщение о защите аккаунта после регистрации/входа
    public void sendProtectAccountMessage(Player player) {
        if (storage == null) return;
        if (storage.getTgLinkByUUID(player.getUniqueId()) != null) return;
        if (storage.getDsLinkByUUID(player.getUniqueId()) != null) return;
        player.sendMessage(msg("protect_account"));
    }

}
