
package com.soldinregister.commands;

import com.soldinregister.SoldinRegister;
import com.soldinregister.core.SessionManager;
import com.soldinregister.core.UserService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChangePassCommand implements CommandExecutor {
    private final SoldinRegister plugin;
    private final UserService users;

    public ChangePassCommand(SoldinRegister plugin) {
        this.plugin = plugin;
        this.users = plugin.getUserService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players"); return true; }
        String prefix = plugin.getConfig().getString("messages.prefix","");
        if (args.length < 2) {
            p.sendMessage(SessionManager.color(prefix + ChatColor.YELLOW + "Использование: /changepass <старый> <новый>"));
            return true;
        }
        if (!users.validatePassword(p.getName(), args[0])) {
            p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Старый пароль неверен."));
            return true;
        }
        if (users.changePassword(p.getName(), args[1])) {
            p.sendMessage(SessionManager.color(prefix + plugin.getConfig().getString("messages.change_pass_ok")));
        } else {
            p.sendMessage(SessionManager.color(prefix + ChatColor.RED + "Не удалось сменить пароль."));
        }
        return true;
    }
}
