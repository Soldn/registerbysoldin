package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CmdChangePass implements CommandExecutor {
    private final UserService users;
    private final SessionManager sessions;
    private final SoldinRegister plugin;

    public CmdChangePass(UserService users, SessionManager sessions, SoldinRegister plugin) {
        this.users = users; this.sessions = sessions; this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        if (args.length < 2) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /changepass <old> <new>");
            return true;
        }
        if (users.changePassword(p, args[0], args[1])) {
            p.sendMessage(plugin.msg("pass-changed"));
        } else {
            p.sendMessage(plugin.msg("wrong-password").replace("%attempts%", "?"));
        }
        return true;
    }
}
