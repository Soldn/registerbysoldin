package com.soldin.register.commands;

import com.soldin.register.SoldinRegister;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthCommands implements CommandExecutor {

    private final SoldinRegister plugin;
    private final Map<UUID, Long> lastLoginTry = new HashMap<>();

    public AuthCommands(SoldinRegister plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду можно использовать только в игре.");
            return true;
        }

        Player player = (Player) sender;
        String name = cmd.getName().toLowerCase();

        switch (name) {
            case "login":
            case "register":
            case "reg": {
                // --- Кулдаун между попытками ---
                long last = lastLoginTry.getOrDefault(player.getUniqueId(), 0L);
                if (System.currentTimeMillis() - last < 2000) {
                    player.sendMessage(ChatColor.RED + "Слишком часто! Подожди пару секунд.");
                    return true;
                }
                lastLoginTry.put(player.getUniqueId(), System.currentTimeMillis());
                break;
            }
        }

        // --- Вызов стандартной логики авторизации/регистрации ---
        return plugin.handleAuthCommand(player, name, args);
    }
}
