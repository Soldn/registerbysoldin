package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.Session;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.TOTPUtil;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CmdLogin implements CommandExecutor {
    private final UserService users;
    private final SessionManager sessions;
    private final SoldinRegister plugin;

    public CmdLogin(UserService users, SessionManager sessions, SoldinRegister plugin) {
        this.users = users; this.sessions = sessions; this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.isAuthenticated()) {
            p.sendMessage(plugin.msg("already-logged"));
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /login <password> [2faCode]");
            return true;
        }
        if (!users.login(p, args[0])) {
            if (s != null) {
                s.decAttempt();
                if (s.getAttemptsLeft() <= 0) {
                    p.kickPlayer(plugin.getConfig().getString("messages.attempts-exhausted", "Too many attempts"));
                    return true;
                }
            }
            p.sendMessage(plugin.msg("wrong-password").replace("%attempts%", String.valueOf(s != null ? s.getAttemptsLeft() : 0)));
            return true;
        }
        // password OK
        boolean requires2FA = users.has2FA(p.getUniqueId());
        if (requires2FA) {
            if (args.length < 2) {
                p.sendMessage(plugin.msg("need-login-2fa"));
                return true;
            }
            try {
                int code = Integer.parseInt(args[1]);
                String secret = users.get2FASecret(p.getUniqueId());
                int window = plugin.getConfig().getInt("security.twofactor.window-steps", 1);
                if (secret == null || !TOTPUtil.verifyCode(secret, code, window)) {
                    p.sendMessage(plugin.msg("twofa-badcode"));
                    return true;
                }
            } catch (NumberFormatException ex) {
                p.sendMessage(plugin.msg("twofa-badcode"));
                return true;
            }
        }
        if (s != null) {
            s.setAuthenticated(true);
        }
        p.sendMessage(plugin.msg("login-success"));
        return true;
    }
}
