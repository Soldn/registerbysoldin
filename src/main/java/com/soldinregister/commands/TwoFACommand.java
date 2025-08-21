
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

public class TwoFACommand implements CommandExecutor {
    private final SoldinRegister plugin;
    private final UserService users;

    public TwoFACommand(SoldinRegister plugin) {
        this.plugin = plugin;
        this.users = plugin.getUserService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players"); return true; }
        String prefix = plugin.getConfig().getString("messages.prefix","");
        if (args.length == 0) {
            p.sendMessage(SessionManager.color(prefix + ChatColor.YELLOW + "Исп: /twofa enable|confirm <код>|disable|unbind|url"));
            return true;
        }
        String sub = args[0].toLowerCase();
        Optional<User> uo = users.get(p.getName());
        if (sub.equals("enable")) {
            String secret = TOTPUtil.generateSecret();
            users.setTwoFASecret(p.getName(), secret);
            String otp = TOTPUtil.buildOtpAuthURL(plugin.getConfig().getString("twofa.issuer","SoldinRegister"), p.getName(), secret);
            String url = TOTPUtil.buildQRUrl(otp);
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.twofa_enabled_step1")));
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.twofa_url").replace("%url%", url)));
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.qr_hint")));
            return true;
        }
        if (sub.equals("url")) {
            if (uo.isEmpty() || uo.get().twofaSecret == null) {
                p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "2FA ещё не включена."));
                return true;
            }
            String otp = TOTPUtil.buildOtpAuthURL(plugin.getConfig().getString("twofa.issuer","SoldinRegister"), p.getName(), uo.get().twofaSecret);
            String url = TOTPUtil.buildQRUrl(otp);
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.twofa_url").replace("%url%", url)));
            return true;
        }
        if (sub.equals("confirm")) {
            if (args.length < 2) {
                p.sendMessage(SessionManager.color(prefix + ChatColor.YELLOW + "Исп: /twofa confirm <код>"));
                return true;
            }
            if (uo.isEmpty() || uo.get().twofaSecret == null) {
                p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Сначала /twofa enable"));
                return true;
            }
            try {
                int code = Integer.parseInt(args[1]);
                if (TOTPUtil.verifyCode(uo.get().twofaSecret, code, System.currentTimeMillis())) {
                    p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.twofa_confirm_ok")));
                } else {
                    p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Код неверный."));
                }
            } catch (NumberFormatException ex) {
                p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Неверный формат кода."));
            }
            return true;
        }
        if (sub.equals("disable") || sub.equals("unbind")) {
            users.resetTwoFA(p.getName());
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.twofa_unbind_ok")));
            return true;
        }
        p.sendMessage(SessionManager.color(prefix + ChatColor.YELLOW + "Исп: /twofa enable|confirm <код>|disable|unbind|url"));
        return true;
    }
}
