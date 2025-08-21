package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CmdAdmin implements CommandExecutor {
    private final UserService users;
    private final SoldinRegister plugin;

    public CmdAdmin(UserService users, SoldinRegister plugin) {
        this.users = users; this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("soldinregister.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "/soldinregister reload | changepass <player> <pass> | delete <player> | reset2fa <player>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                SoldinRegister.getInstance().reloadConfig();
                sender.sendMessage(SoldinRegister.getInstance().msg("reloaded"));
                break;
            case "changepass":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "/soldinregister changepass <player> <pass>");
                } else {
                    boolean ok = users.adminChangePassword(args[1], args[2]);
                    sender.sendMessage(SoldinRegister.getInstance().msg("admin-pass-changed").replace("%player%", args[1]) + (ok ? "" : ChatColor.RED + " (not found)"));
                }
                break;
            case "delete":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/soldinregister delete <player>");
                } else {
                    boolean ok = users.adminDelete(args[1]);
                    sender.sendMessage(SoldinRegister.getInstance().msg("admin-deleted").replace("%player%", args[1]) + (ok ? "" : ChatColor.RED + " (not found)"));
                }
                break;
            case "reset2fa":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/soldinregister reset2fa <player>");
                } else {
                    boolean ok = users.reset2FA(args[1]);
                    sender.sendMessage((ok ? ChatColor.GREEN + "2FA reset for " : ChatColor.RED + "Not found: ") + args[1]);
                }
                break;
            default:
                sender.sendMessage(ChatColor.YELLOW + "/soldinregister reload | changepass <player> <pass> | delete <player> | reset2fa <player>");
        }
        return true;
    }
}
