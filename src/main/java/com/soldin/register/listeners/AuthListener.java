package com.soldin.register.listeners;

import com.soldin.register.SoldinRegister;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class AuthListener implements Listener {
    private final SoldinRegister plugin;
    public AuthListener(SoldinRegister plugin) { this.plugin = plugin; }

    private boolean shouldBlock(Player p) { return !plugin.isAuthenticated(p.getUniqueId()); }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.revokeAuthenticated(p.getUniqueId());
        long timeoutMs = plugin.getConfig().getLong("timeouts.auth_seconds", 60) * 1000L;
        long deadline = System.currentTimeMillis() + timeoutMs;
        plugin.setAuthDeadline(p.getUniqueId(), deadline);
        p.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.on_join", "&eВведи &a/register <пароль> &eили &a/login <пароль> [код2FA]")));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isAuthenticated(p.getUniqueId()) && p.isOnline()) {
                p.kickPlayer(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.auth_time_expired", "&cВремя на авторизацию истекло.")));
            }
        }, timeoutMs / 50L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        plugin.revokeAuthenticated(e.getPlayer().getUniqueId());
        plugin.clearAuthDeadline(e.getPlayer().getUniqueId());
        plugin.resetAttempts(e.getPlayer().getUniqueId());
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        if (plugin.getConfig().getBoolean("locks.block_chat", true) && shouldBlock(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.must_login", "&cСначала войди: /login <пароль>")));
        }
    }

    @EventHandler public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("locks.block_commands", true)) return;
        if (!shouldBlock(e.getPlayer())) return;
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/login") || msg.startsWith("/l ") || msg.equals("/l") || msg.startsWith("/register") || msg.startsWith("/reg") || msg.startsWith("/soldinregister")) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.must_login", "&cСначала войди: /login <пароль>")));
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (plugin.getConfig().getBoolean("locks.block_move", false) && shouldBlock(e.getPlayer())) e.setTo(e.getFrom());
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (plugin.getConfig().getBoolean("locks.block_interact", true) && shouldBlock(e.getPlayer())) e.setCancelled(true);
    }
}