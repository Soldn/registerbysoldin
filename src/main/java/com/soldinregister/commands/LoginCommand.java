
package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.TOTPUtil;
import com.soldinregister.core.User;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class LoginCommand implements CommandExecutor {
    private final SoldinRegister plugin;
    private final UserService users;

    public LoginCommand(SoldinRegister plugin) {
        this.plugin = plugin;
        this.users = plugin.getUserService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players"); return true; }
        String prefix = plugin.getConfig().getString("messages.prefix","");
        if (!users.isRegistered(p.getName())) {
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.not_registered")));
            return true;
        }
        if (args.length < 1) {
            boolean has2fa = users.get(p.getName()).map(u -> u.twofaSecret != null).orElse(false);
            if (has2fa) {
                p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.need_login_2fa")));
            } else {
                p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.need_login")));
            }
            return true;
        }
        if (!users.validatePassword(p.getName(), args[0])) {
            int left = plugin.getSessionManager().incAttempt(p);
            String msg = plugin.getConfig().getString("messages.wrong_password").replace("%attempts%", String.valueOf(left));
            p.sendMessage(SessionManager.color(prefix + msg));
            return true;
        }
        Optional<User> uo = users.get(p.getName());
        if (uo.isPresent() && uo.get().twofaSecret != null && plugin.getConfig().getBoolean("twofa.enabled", true)) {
            if (args.length < 2) {
                p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.need_login_2fa")));
                return true;
            }
            try {
                int code = Integer.parseInt(args[1]);
                if (!TOTPUtil.verifyCode(uo.get().twofaSecret, code, System.currentTimeMillis())) {
                    int left = plugin.getSessionManager().incAttempt(p);
                    String msg = plugin.getConfig().getString("messages.wrong_password").replace("%attempts%", String.valueOf(left));
                    p.sendMessage(SessionManager.color(prefix + msg));
                    return true;
                }
            } catch (NumberFormatException ex) {
                p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Неверный формат кода 2FA."));
                return true;
            }
        }
        plugin.getSessionManager().setAuthenticated(p);
        p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.login_success")));
        return true;
    }
}
