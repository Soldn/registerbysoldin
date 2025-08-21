package com.soldinregister;

import com.soldinregister.commands.*;
import com.soldinregister.core.*;
import com.soldinregister.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class SoldinRegister extends JavaPlugin {

    private static SoldinRegister instance;
    private UserService userService;
    private SessionManager sessionManager;
    private ReminderTask reminderTask;

    public static SoldinRegister getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getLogger().info("SoldinRegister enabling...");
        Database db = new Database(this);
        db.init();
        this.userService = new UserService(db, this);
        this.sessionManager = new SessionManager(this);
        this.reminderTask = new ReminderTask(this, sessionManager);

        // Commands
        getCommand("register").setExecutor(new CmdRegister(userService, sessionManager, this));
        getCommand("login").setExecutor(new CmdLogin(userService, sessionManager, this));
        getCommand("changepass").setExecutor(new CmdChangePass(userService, sessionManager, this));
        getCommand("twofa").setExecutor(new CmdTwoFA(userService, sessionManager, this));
        getCommand("soldinregister").setExecutor(new CmdAdmin(userService, this));

        // Listeners
        Bukkit.getPluginManager().registerEvents(new JoinListener(sessionManager, userService, this), this);
        Bukkit.getPluginManager().registerEvents(new RestrictListener(sessionManager, this), this);

        // Start reminder loop
        reminderTask.runTaskTimer(this, 0L, getConfig().getLong("session.reminder-interval-ticks", 20L));
        getLogger().info("SoldinRegister enabled!");
    }

    @Override
    public void onDisable() {
        if (reminderTask != null) reminderTask.cancel();
        if (userService != null) userService.shutdown();
        getLogger().info("SoldinRegister disabled!");
    }

    public UserService getUserService() {
        return userService;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public String msg(String key) {
        String p = getConfig().getString("messages.prefix", "");
        String v = getConfig().getString("messages." + key, key);
        return ChatColor.translateAlternateColorCodes('&', p + v);
    }

    // Task to remind users every second
    static class ReminderTask extends BukkitRunnable {
        private final SoldinRegister plugin;
        private final SessionManager sessions;

        ReminderTask(SoldinRegister plugin, SessionManager sessions) {
            this.plugin = plugin;
            this.sessions = sessions;
        }

        @Override
        public void run() {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("soldinregister.bypass")) continue;
                Session s = sessions.get(p.getUniqueId());
                if (s == null || s.isAuthenticated()) continue;

                if (!s.isRegistered()) {
                    p.sendMessage(plugin.msg("need-register"));
                } else if (s.isRegistered() && !s.requires2FA()) {
                    p.sendMessage(plugin.msg("need-login"));
                } else {
                    p.sendMessage(plugin.msg("need-login-2fa"));
                }

                // timeout
                if (s.getRemainingSeconds() <= 0) {
                    p.kickPlayer(plugin.getConfig().getString("messages.timeout-kick", "Timeout"));
                } else {
                    s.tickSecond();
                }
            }
        }
    }
}
