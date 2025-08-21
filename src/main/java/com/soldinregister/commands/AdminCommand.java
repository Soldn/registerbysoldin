
package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AdminCommand implements CommandExecutor {
    private final SoldinRegister plugin;
    private final UserService users;

    public AdminCommand(SoldinRegister plugin) {
        this.plugin = plugin;
        this.users = plugin.getUserService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = plugin.getConfig().getString("messages.prefix","");
        if (!sender.hasPermission("soldinregister.admin")) {
            sender.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Нет прав."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(SessionManager.color(prefix + ChatColor.YELLOW + "Исп: /soldinregister reload|changepass <ник> <новый>|delete <ник>|reset2fa <ник>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                sender.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.reloaded")));
                return true;
            case "changepass":
                if (args.length < 3) { sender.sendMessage("/soldinregister changepass <ник> <новый>"); return true; }
                if (users.changePassword(args[1], args[2])) {
                    sender.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.admin_changed").replace("%player%", args[1])));
                } else {
                    sender.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Не удалось изменить пароль."));
                }
                return true;
            case "delete":
                if (args.length < 2) { sender.sendMessage("/soldinregister delete <ник>"); return true; }
                if (users.delete(args[1])) {
                    sender.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.admin_deleted").replace("%player%", args[1])));
                } else {
                    sender.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Не удалось удалить."));
                }
                return true;
            case "reset2fa":
                if (args.length < 2) { sender.sendMessage("/soldinregister reset2fa <ник>"); return true; }
                if (users.resetTwoFA(args[1])) {
                    sender.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.admin_reset2fa").replace("%player%", args[1])));
                } else {
                    sender.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Не удалось сбросить 2FA."));
                }
                return true;
            default:
                sender.sendMessage(SessionManager.color(prefix + ChatColor.YELLOW + "Исп: /soldinregister reload|changepass <ник> <новый>|delete <ник>|reset2fa <ник>"));
                return true;
        }
    }
}
