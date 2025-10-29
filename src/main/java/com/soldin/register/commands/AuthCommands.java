package com.soldin.register.commands;

import com.soldin.register.SoldinRegister;
import com.soldin.register.model.UserRecord;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
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

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Используй: /" + name + " <пароль>");
            return true;
        }

        String password = args[0];
        UUID uuid = player.getUniqueId();

        // --- Кулдаун ---
        long last = lastLoginTry.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - last < 2000) {
            player.sendMessage(ChatColor.RED + "Слишком часто! Подожди пару секунд.");
            return true;
        }
        lastLoginTry.put(uuid, System.currentTimeMillis());

        switch (name) {
            case "register":
            case "reg": {
                if (plugin.storage().getByUUID(uuid) != null) {
                    player.sendMessage(ChatColor.RED + "Вы уже зарегистрированы!");
                    return true;
                }

                try {
                    String salt = generateSalt();
                    String hash = hashPassword(password, salt);
                    UserRecord record = new UserRecord(uuid, player.getName(), hash, salt, 10000,
                            player.getAddress().getAddress().getHostAddress(),
                            System.currentTimeMillis(), 0L, null);
                    plugin.storage().createUser(record);
                    plugin.setAuthenticated(uuid, true);
                    player.sendMessage(ChatColor.GREEN + "Регистрация успешна! Теперь вы вошли.");
                } catch (Exception e) {
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "Ошибка при регистрации.");
                }
                return true;
            }

            case "login": {
                UserRecord record = plugin.storage().getByUUID(uuid);
                if (record == null) {
                    player.sendMessage(ChatColor.RED + "Вы не зарегистрированы. Используй /register <пароль>");
                    return true;
                }

                try {
                    String inputHash = hashPassword(password, record.salt);
                    if (!inputHash.equals(record.hash)) {
                        player.sendMessage(ChatColor.RED + "Неверный пароль!");
                        return true;
                    }

                    plugin.setAuthenticated(uuid, true);
                    player.sendMessage(ChatColor.GREEN + "Успешный вход!");
                } catch (Exception e) {
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "Ошибка при входе.");
                }
                return true;
            }

            default:
                player.sendMessage(ChatColor.RED + "Неизвестная команда.");
                return true;
        }
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Base64.getDecoder().decode(salt));
        byte[] hash = digest.digest(password.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(hash);
    }
}
