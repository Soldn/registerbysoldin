package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.QRUtil;
import com.soldinregister.core.TOTPUtil;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CmdTwoFA implements CommandExecutor {
    private final UserService users;
    private final com.soldinregister.core.SessionManager sessions;
    private final SoldinRegister plugin;

    public CmdTwoFA(UserService users, com.soldinregister.core.SessionManager sessions, SoldinRegister plugin) {
        this.users = users; this.sessions = sessions; this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (args.length < 1) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /twofa <enable|confirm|disable|unbind|url> [...]");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "enable": {
                String secret = users.get2FASecret(p.getUniqueId());
                if (secret == null || secret.isEmpty()) {
                    secret = TOTPUtil.generateBase32Secret();
                    users.update2FASecret(p.getUniqueId(), secret, false);
                }
                String issuer = plugin.getConfig().getString("security.twofactor.issuer", "SoldinRegister");
                String url = TOTPUtil.buildOtpAuthURL(issuer, p.getName(), secret);
                QRUtil.sendQRLink(plugin, p, url);
                p.sendMessage(plugin.msg("twofa-enabled"));
                break;
            }
            case "confirm": {
                if (args.length < 2) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /twofa confirm <code>");
                    break;
                }
                try {
                    int code = Integer.parseInt(args[1]);
                    if (users.confirm2FA(p, code)) {
                        p.sendMessage(plugin.msg("twofa-confirmed"));
                    } else {
                        p.sendMessage(plugin.msg("twofa-badcode"));
                    }
                } catch (NumberFormatException e) {
                    p.sendMessage(plugin.msg("twofa-badcode"));
                }
                break;
            }
            case "disable": {
                if (args.length < 2) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /twofa disable <code>");
                    break;
                }
                try {
                    int code = Integer.parseInt(args[1]);
                    if (users.disable2FA(p, code)) {
                        p.sendMessage(plugin.msg("twofa-disabled"));
                    } else {
                        p.sendMessage(plugin.msg("twofa-badcode"));
                    }
                } catch (NumberFormatException e) {
                    p.sendMessage(plugin.msg("twofa-badcode"));
                }
                break;
            }
            case "unbind": { // отвязать по паролю
                if (args.length < 2) {
                    p.sendMessage(ChatColor.YELLOW + "Usage: /twofa unbind <password>");
                    break;
                }
                boolean ok = users.login(p, args[1]); // проверим пароль
                if (!ok) {
                    p.sendMessage(plugin.msg("wrong-password").replace("%attempts%", "?"));
                    break;
                }
                users.update2FASecret(p.getUniqueId(), null, false);
                p.sendMessage(plugin.msg("twofa-unbound"));
                break;
            }
            case "url": {
                String secret = users.get2FASecret(p.getUniqueId());
                if (secret == null || secret.isEmpty()) {
                    p.sendMessage(plugin.msg("twofa-unbound"));
                    break;
                }
                String issuer = plugin.getConfig().getString("security.twofactor.issuer", "SoldinRegister");
                String url = TOTPUtil.buildOtpAuthURL(issuer, p.getName(), secret);
                QRUtil.sendQRLink(plugin, p, url);
                break;
            }
            default:
                p.sendMessage(ChatColor.YELLOW + "Usage: /twofa <enable|confirm|disable|unbind|url> [...]");
        }
        return true;
    }
}
