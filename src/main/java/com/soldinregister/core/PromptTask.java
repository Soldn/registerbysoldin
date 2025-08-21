
package com.soldinregister.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PromptTask extends BukkitRunnable {
    private final Plugin plugin;
    private final SessionManager sessions;
    private final UserService users;

    public PromptTask(Plugin plugin, SessionManager sessions, UserService users) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sessions.isAuthenticated(p)) continue;
            boolean registered = users.isRegistered(p.getName());
            String msg;
            if (!registered) {
                msg = plugin.getConfig().getString("messages.need_register");
            } else {
                boolean has2fa = users.get(p.getName()).map(u -> u.twofaSecret != null).orElse(false);
                if (has2fa) {
                    msg = plugin.getConfig().getString("messages.need_login_2fa");
                } else {
                    msg = plugin.getConfig().getString("messages.need_login");
                }
            }
            p.sendMessage(SessionManager.color(plugin.getConfig().getString("messages.prefix","") + msg));
        }
    }
}
