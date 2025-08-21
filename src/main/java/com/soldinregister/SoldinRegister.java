
package com.soldinregister;

import com.soldinregister.core.*;
import com.soldinregister.listeners.JoinListener;
import com.soldinregister.listeners.RestrictListener;
import com.soldinregister.commands.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SoldinRegister extends JavaPlugin {

    private static SoldinRegister instance;
    private Database database;
    private UserService userService;
    private SessionManager sessionManager;
    private PromptTask promptTask;

    public static SoldinRegister getInstance() { return instance; }
    public UserService getUserService() { return userService; }
    public SessionManager getSessionManager() { return sessionManager; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // DB init
        database = new Database(this);
        database.init();
        userService = new UserService(this, database);

        // session manager
        sessionManager = new SessionManager(this, userService);

        // listeners
        Bukkit.getPluginManager().registerEvents(new JoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RestrictListener(this), this);

        // commands
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("changepass").setExecutor(new ChangePassCommand(this));
        getCommand("twofa").setExecutor(new TwoFACommand(this));
        getCommand("soldinregister").setExecutor(new AdminCommand(this));

        // reminder every second
        promptTask = new PromptTask(this, sessionManager, userService);
        promptTask.runTaskTimer(this, 20L, Math.max(1, getConfig().getInt("settings.reminder-interval-seconds",1)) * 20L);

        getLogger().info("SoldinRegister enabled.");
    }

    @Override
    public void onDisable() {
        if (promptTask != null) promptTask.cancel();
        if (database != null) database.shutdown();
        getLogger().info("SoldinRegister disabled.");
    }
}
