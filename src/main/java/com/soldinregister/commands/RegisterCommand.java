
package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {
    private final SoldinRegister plugin;
    private final UserService users;

    public RegisterCommand(SoldinRegister plugin) {
        this.plugin = plugin;
        this.users = plugin.getUserService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players"); return true; }
        String prefix = plugin.getConfig().getString("messages.prefix","");
        if (users.isRegistered(p.getName())) {
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.already_registered")));
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.need_register")));
            return true;
        }
        String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "0.0.0.0";
        int max = plugin.getConfig().getInt("settings.max-accounts-per-ip", 3);
        if (users.countByIP(ip) >= max) {
            p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Достигнут лимит аккаунтов на IP."));
            return true;
        }
        if (users.create(p.getName(), args[0], ip)) {
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.registered")));
        } else {
            p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Ошибка регистрации."));
        }
        return true;
    }
}
