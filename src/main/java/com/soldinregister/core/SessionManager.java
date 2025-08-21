
package com.soldinregister.core;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SessionManager {
    private final Plugin plugin;
    private final UserService userService;
    private final Map<UUID, Integer> attempts = new HashMap<>();
    private final Set<UUID> authenticated = new HashSet<>();
    private final Map<UUID, BukkitRunnable> timeouts = new HashMap<>();

    public SessionManager(Plugin plugin, UserService userService) {
        this.plugin = plugin;
        this.userService = userService;
    }

    public boolean isAuthenticated(Player p) {
        return authenticated.contains(p.getUniqueId());
    }

    public void startSession(Player p) {
        attempts.put(p.getUniqueId(), 0);
        int timeout = plugin.getConfig().getInt("settings.login-timeout-seconds", 60);
        BukkitRunnable task = new BukkitRunnable() {
            @Override public void run() {
                if (!isAuthenticated(p)) {
                    p.kickPlayer(color(getMsg("login_timeout")));
                }
            }
        };
        task.runTaskLater(plugin, timeout * 20L);
        timeouts.put(p.getUniqueId(), task);
    }

    public void clearSession(Player p) {
        authenticated.remove(p.getUniqueId());
        attempts.remove(p.getUniqueId());
        BukkitRunnable t = timeouts.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    public void setAuthenticated(Player p) {
        authenticated.add(p.getUniqueId());
        BukkitRunnable t = timeouts.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    public int incAttempt(Player p) {
        int leftAll = plugin.getConfig().getInt("settings.max-login-attempts", 3);
        int cur = attempts.getOrDefault(p.getUniqueId(), 0) + 1;
        attempts.put(p.getUniqueId(), cur);
        int left = Math.max(0, leftAll - cur);
        if (left <= 0) {
            p.kickPlayer(color(getMsg("kicked_after_attempts")));
        }
        return left;
    }

    public static String color(String s) {
        return s.replace("&", "§");
    }

    private String getMsg(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return prefix + plugin.getConfig().getString("messages." + key, key);
    }
}
