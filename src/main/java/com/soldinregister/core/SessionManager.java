package com.soldinregister.core;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public SessionManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public Session get(UUID id) { return sessions.get(id); }
    public void remove(UUID id) { sessions.remove(id); }

    public Session initFor(Player p, boolean registered, boolean requires2FA) {
        int attempts = plugin.getConfig().getInt("session.max-login-attempts", 3);
        int timeout = plugin.getConfig().getInt("session.login-timeout-seconds", 60);
        Session s = new Session(p.getUniqueId(), registered, requires2FA, attempts, timeout);
        sessions.put(p.getUniqueId(), s);
        return s;
    }
}
