package com.soldinregister.listeners;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.Session;
import com.soldinregister.core.SessionManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Player;

public class RestrictListener implements Listener {
    private final SessionManager sessions;
    private final SoldinRegister plugin;

    public RestrictListener(SessionManager sessions, SoldinRegister plugin) {
        this.sessions = sessions;
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("soldinregister.bypass")) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.isAuthenticated()) return;
        if (e.getFrom().distanceSquared(e.getTo()) == 0) return;
        e.setTo(e.getFrom());
        p.sendMessage(plugin.msg("no-move"));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("soldinregister.bypass")) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.isAuthenticated()) return;
        e.setCancelled(true);
        p.sendMessage(plugin.msg("no-chat-no-cmd"));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("soldinregister.bypass")) return;
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.isAuthenticated()) return;
        String cmd = e.getMessage().toLowerCase();
        if (cmd.startsWith("/register") || cmd.startsWith("/reg") || cmd.startsWith("/login") || cmd.startsWith("/l")) {
            return;
        }
        e.setCancelled(true);
        p.sendMessage(plugin.msg("no-chat-no-cmd"));
    }
}
