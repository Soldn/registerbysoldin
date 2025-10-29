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
    private final Map<UUID, Long> lastTry = new HashMap<>();

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
        String currentIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "0.0.0.0";

        // КД на попытки (2 сек)
        long last = lastTry.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() - last < 2000) {
            player.sendMessage(ChatColor.RED + "Слишком часто! Подожди пару секунд.");
            return true;
        }
        lastTry.put(uuid, System.currentTimeMillis());

        switch (name) {
            // -------------------- РЕГИСТРАЦИЯ --------------------
            case "register":
            case "reg": {
                if (plugin.storage().getByUUID(uuid) != null) {
                    player.sendMessage(ChatColor.RED + "Вы уже зарегистрированы!");
                    return true;
                }

                // лимит аккаунтов на IP (0 = без лимита)
                int maxPerIp = plugin.getConfig().getInt("limits.max_accounts_per_ip", 3);
                if (maxPerIp > 0) {
                    int count = plugin.storage().countByIP(currentIp);
                    if (count >= maxPerIp) {
                        player.sendMessage(ChatColor.RED + "Превышен лимит аккаунтов на IP.");
                        return true;
                    }
                }

                try {
                    String salt = generateSalt();
                    String hash = hashPassword(password, salt);

                    UserRecord record = new UserRecord(
                            uuid,
                            player.getName(),
                            hash,
                            salt,
                            10000, // iterations
                            currentIp,
                            System.currentTimeMillis(), // registeredAt
                            0L,                         // lastLogin
                            null                        // twoFASecret
                    );

                    // ВАЖНО: у Storage интерфейса create/update, нет save()
                    plugin.storage().create(record);

                    // Авторизуем
                    plugin.setAuthenticated(uuid);
                    player.sendMessage(ChatColor.GREEN + "Регистрация успешна! Вы вошли в аккаунт.");
                } catch (Exception e) {
                    e.printStackTrace();
                    player.sendMessage(ChatColor.RED + "Ошибка при регистрации.");
                }
                return true;
            }

            // -------------------- ВХОД --------------------
            case "login": {
                UserRecord record = plugin.storage().getByUUID(uuid);
                if (record == null) {
                    player.sendMessage(ChatColor.RED + "Вы не зарегистрированы. Используй /register <пароль>");
                    return true;
                }

                // Проверка IP при входе (запрещаем логин с другого IP)
                if (record.ip != null && !record.ip.equals(currentIp)) {
                    player.sendMessage(ChatColor.RED + "Вход с другого IP запрещён. Зарегистрируйтесь заново.");
                    return true;
                }

                try {
                    String inputHash = hashPassword(password, record.salt);
                    if (!inputHash.equals(record.hash)) {
                        player.sendMessage(ChatColor.RED + "Неверный пароль!");
                        return true;
                    }

                    // Обновим дату входа
                    record.lastLogin = System.currentTimeMillis();
                    plugin.storage().update(record);

                    plugin.setAuthenticated(uuid);
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

    // ---- Хелперы для паролей ----

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
