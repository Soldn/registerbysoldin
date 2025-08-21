
package com.soldinregister.listeners;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.SessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class RestrictListener implements Listener {
    private final SoldinRegister plugin;
    private final SessionManager sessions;

    public RestrictListener(SoldinRegister plugin) {
        this.plugin = plugin;
        this.sessions = plugin.getSessionManager();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getConfig().getBoolean("settings.block-move-until-auth", true)) return;
        if (!sessions.isAuthenticated(e.getPlayer())) {
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ()) {
                e.setTo(e.getFrom());
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.getConfig().getBoolean("settings.block-chat-until-auth", true)) return;
        if (!sessions.isAuthenticated(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("settings.block-commands-until-auth", true)) return;
        if (sessions.isAuthenticated(e.getPlayer())) return;
        String msg = e.getMessage().toLowerCase();
        for (String allow : plugin.getConfig().getStringList("settings.allowed-commands-before-auth")) {
            if (msg.startsWith(allow.toLowerCase()+" ")) return;
            if (msg.equalsIgnoreCase(allow)) return;
        }
        e.setCancelled(true);
    }
}
