package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.Session;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CmdRegister implements CommandExecutor {
    private final UserService users;
    private final SessionManager sessions;
    private final SoldinRegister plugin;

    public CmdRegister(UserService users, SessionManager sessions, SoldinRegister plugin) {
        this.users = users; this.sessions = sessions; this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.isRegistered()) {
            p.sendMessage(plugin.msg("already-registered"));
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(ChatColor.YELLOW + "Usage: /register <password>");
            return true;
        }
        String pass = args[0];
        boolean ok = users.register(p, pass);
        if (!ok) {
            p.sendMessage(plugin.msg("ip-limit-hit"));
            return true;
        }
        if (s != null) {
            s.setRegistered(true);
        }
        p.sendMessage(plugin.msg("registered"));
        return true;
    }
}
