
package com.soldinregister.listeners;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.UserService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final SoldinRegister plugin;
    private final SessionManager sessions;
    private final UserService users;

    public JoinListener(SoldinRegister plugin) {
        this.plugin = plugin;
        this.sessions = plugin.getSessionManager();
        this.users = plugin.getUserService();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        sessions.clearSession(e.getPlayer());
        sessions.startSession(e.getPlayer());
    }
}
