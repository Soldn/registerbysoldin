package com.soldinregister.listeners;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.Session;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.UserService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final SessionManager sessions;
    private final UserService users;
    private final SoldinRegister plugin;

    public JoinListener(SessionManager sessions, UserService users, SoldinRegister plugin) {
        this.sessions = sessions;
        this.users = users;
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean registered = users.isRegistered(p.getUniqueId());
        boolean requires2FA = registered && users.has2FA(p.getUniqueId());
        Session s = sessions.initFor(p, registered, requires2FA);
        if (!registered) {
            p.sendMessage(plugin.msg("need-register"));
        } else if (!requires2FA) {
            p.sendMessage(plugin.msg("need-login"));
        } else {
            p.sendMessage(plugin.msg("need-login-2fa"));
        }
    }
}
