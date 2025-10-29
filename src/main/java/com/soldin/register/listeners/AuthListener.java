package com.soldin.register.listeners;

import com.soldin.register.SoldinRegister;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class AuthListener implements Listener {

    private final Map<UUID, Integer> reminderTasks = new HashMap<>();
    private final Map<UUID, Integer> barTasks = new HashMap<>();
    private final SoldinRegister plugin;

    public AuthListener(SoldinRegister plugin) {
        this.plugin = plugin;
    }

    private boolean shouldBlock(Player p) {
        return !plugin.isAuthenticated(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.revokeAuthenticated(p.getUniqueId());

        long timeoutMs = plugin.getConfig().getLong("timeouts.auth_seconds", 60) * 1000L;
        long deadline = System.currentTimeMillis() + timeoutMs;
        plugin.setAuthDeadline(p.getUniqueId(), deadline);

        // Отправляем сообщение
        p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.on_join",
                        "&eВведи &a/register <пароль> &eили &a/login <пароль> [код2FA]")));

        // Периодическое напоминание
        if (plugin.getConfig().getBoolean("reminder.enabled", true)) {
            int intervalTicks = plugin.getConfig().getInt("reminder.interval_seconds", 2) * 20;
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!plugin.isAuthenticated(p.getUniqueId()) && p.isOnline()) {
                    if (plugin.storage().getByUUID(p.getUniqueId()) == null) {
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cЗарегистрируйтесь: /reg <пароль>"));
                    } else {
                        p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eВойдите: /login <пароль>"));
                    }
                }
            }, 0L, intervalTicks).getTaskId();
            reminderTasks.put(p.getUniqueId(), taskId);
        }

        // ⏱ Отображение времени над хп/едой (action bar)
        int barTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.isAuthenticated(p.getUniqueId()) && p.isOnline()) {
                long left = (plugin.getAuthDeadline(p.getUniqueId()) - System.currentTimeMillis()) / 1000L;
                if (left < 0) left = 0;
                String msg = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.auth_timer", "&eОсталось &c{time}s для авторизации"))
                        .replace("{time}", String.valueOf(left));
                p.sendActionBar(msg);
            }
        }, 0L, 20L).getTaskId();
        barTasks.put(p.getUniqueId(), barTask);

        // Кик после таймаута
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isAuthenticated(p.getUniqueId()) && p.isOnline()) {
                p.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.auth_time_expired", "&cВремя на авторизацию истекло.")));
            }
        }, timeoutMs / 50L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.revokeAuthenticated(e.getPlayer().getUniqueId());
        plugin.clearAuthDeadline(e.getPlayer().getUniqueId());
        plugin.resetAttempts(e.getPlayer().getUniqueId());

        UUID u = e.getPlayer().getUniqueId();
        if (reminderTasks.containsKey(u)) Bukkit.getScheduler().cancelTask(reminderTasks.remove(u));
        if (barTasks.containsKey(u)) Bukkit.getScheduler().cancelTask(barTasks.remove(u));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (plugin.getConfig().getBoolean("locks.block_chat", true) && shouldBlock(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.must_login",
                            "&cСначала войди: /login <пароль>")));
        }
    }

    // 🔒 Блокировка всех команд, кроме разрешённых
    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("locks.block_commands", true)) return;
        if (!shouldBlock(e.getPlayer())) return;

        String msg = e.getMessage().toLowerCase().trim();
        List<String> blocked = plugin.getConfig().getStringList("locks.blocked_commands");

        // Разрешённые команды
        if (msg.startsWith("/login") || msg.startsWith("/l ") || msg.equals("/l")
                || msg.startsWith("/register") || msg.startsWith("/reg") || msg.equals("/reg")
                || msg.startsWith("/soldinregister") || msg.startsWith("/changepass")
                || msg.startsWith("/2fa") || msg.startsWith("/2faurl")) {
            return;
        }

        // Если команда входит в список блокируемых из config.yml — запрещаем
        for (String c : blocked) {
            if (msg.startsWith("/" + c.toLowerCase())) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.must_login", "&cСначала войди: /login <пароль>")));
                return;
            }
        }

        // Блокируем всё остальное
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.must_login",
                        "&cСначала войди: /login <пароль>")));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (plugin.getConfig().getBoolean("locks.block_move", false) && shouldBlock(e.getPlayer()))
            e.setTo(e.getFrom());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (plugin.getConfig().getBoolean("locks.block_interact", true) && shouldBlock(e.getPlayer()))
            e.setCancelled(true);
    }
}
