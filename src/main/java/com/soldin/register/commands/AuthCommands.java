package com.soldin.register.commands;

import com.soldin.register.SoldinRegister;
import com.soldin.register.model.UserRecord;
import com.soldin.register.util.PasswordUtil;
import com.soldin.register.util.TOTPUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AuthCommands implements CommandExecutor {
    private final SoldinRegister plugin;
    public AuthCommands(SoldinRegister plugin) { this.plugin = plugin; }

    private String msg(String path) {
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages." + path, path));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        if (name.equals("soldinregister")) {
            return handleAdminRoot(sender, args);
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Только игрок.");
            return true;
        }

        Player p = (Player) sender;
        UUID uuid = p.getUniqueId();

        switch (name) {

            // ========================= REGISTER =========================

            case "register":
            case "reg":
                if (plugin.storage().getByUUID(uuid) != null) {
                    p.sendMessage(msg("already_registered"));
                    return true;
                }
                if (expired(uuid)) {
                    kickTimeout(p);
                    return true;
                }
                if (args.length < 1) {
                    p.sendMessage(msg("usage_register"));
                    return true;
                }

                String pass = args[0];
                if (pass.length() < plugin.getConfig().getInt("security.min_password_length", 5)) {
                    p.sendMessage(msg("password_too_short"));
                    return true;
                }

                String regIP = p.getAddress() != null
                        ? p.getAddress().getAddress().getHostAddress()
                        : "unknown";

                int count = plugin.storage().countByIP(regIP);
                int max = plugin.getConfig().getInt("limits.max_accounts_per_ip", 3);
                if (max > 0 && count >= max) {
                    p.sendMessage(msg("ip_limit_reached"));
                    return true;
                }

                PasswordUtil.HashPack hp = PasswordUtil.hashPassword(pass);
                UserRecord r = new UserRecord(uuid, p.getName(), hp.hash, hp.salt,
                        hp.iterations, regIP, System.currentTimeMillis(), 0L, null);

                plugin.storage().create(r);
                plugin.setAuthenticated(uuid);
                p.sendMessage(msg("registered_ok"));
                return true;

            // ========================= LOGIN =========================

            case "login":
            case "l":
                UserRecord u = plugin.storage().getByUUID(uuid);
                if (u == null) {
                    p.sendMessage(msg("not_registered"));
                    return true;
                }
                if (expired(uuid)) {
                    kickTimeout(p);
                    return true;
                }
                if (args.length < 1) {
                    p.sendMessage(msg("usage_login"));
                    return true;
                }

                // Проверка пароля
                if (!PasswordUtil.verify(args[0], u)) {
                    plugin.decAttempt(uuid);
                    int left = plugin.getAttemptsLeft(uuid);

                    if (left <= 0) {
                        p.kickPlayer(msg("login_attempts_exceeded"));
                        plugin.resetAttempts(uuid);
                        return true;
                    } else {
                        p.sendMessage(msg("login_fail_with_left").replace("{left}", String.valueOf(left)));
                        return true;
                    }
                }

                // Проверка TOTP, если включен
                if (plugin.getConfig().getBoolean("enable-2fa", true)
                        && plugin.getConfig().getBoolean("twofa.require_if_enabled", true)
                        && u.twoFASecret != null) {

                    if (args.length < 2) {
                        p.sendMessage(msg("twofa_required"));
                        return true;
                    }

                    String totp = args[1];

                    if (!TOTPUtil.verifyCode(u.twoFASecret, totp,
                            plugin.getConfig().getInt("twofa.totp_window", 1))) {

                        plugin.decAttempt(uuid);
                        int left = plugin.getAttemptsLeft(uuid);

                        if (left <= 0) {
                            p.kickPlayer(msg("login_attempts_exceeded"));
                            plugin.resetAttempts(uuid);
                            return true;
                        } else {
                            p.sendMessage(msg("twofa_wrong_with_left").replace("{left}", String.valueOf(left)));
                            return true;
                        }
                    }
                }

                // --- ВАЖНО: заменили ip → ipAddress (исправление ошибки) ---
                String ipAddress = p.getAddress() != null
                        ? p.getAddress().getAddress().getHostAddress()
                        : "unknown";

                // Telegram 2FA
                if (plugin.handleTelegramLogin(p, u, ipAddress)) {
                    // Требуется подтверждение — НЕ авторизуем, НЕ кикаем
                    return true;
                }

                // Если подтверждение не нужно → обычный логин
                u.lastLogin = System.currentTimeMillis();
                u.ip = ipAddress;
                plugin.storage().update(u);
                plugin.setAuthenticated(uuid);
                p.sendMessage(msg("login_ok"));
                plugin.sendProtectAccountMessage(p);
                return true;

            // ========================= CHANGE PASSWORD =========================

            case "changepass":
                UserRecord cu = plugin.storage().getByUUID(uuid);
                if (cu == null) {
                    p.sendMessage(msg("not_registered"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(msg("usage_changepass"));
                    return true;
                }

                if (!PasswordUtil.verify(args[0], cu)) {
                    p.sendMessage(msg("wrong_old_pass"));
                    return true;
                }

                if (args[1].length() < plugin.getConfig().getInt("security.min_password_length", 5)) {
                    p.sendMessage(msg("password_too_short"));
                    return true;
                }

                PasswordUtil.HashPack nh = PasswordUtil.hashPassword(args[1]);
                cu.hash = nh.hash;
                cu.salt = nh.salt;
                cu.iterations = nh.iterations;

                plugin.storage().update(cu);
                p.sendMessage(msg("changepass_ok"));
                return true;
        }

        return false;
    }

    // ========================= UTILS =========================

    private boolean expired(UUID uuid) {
        long dl = plugin.getAuthDeadline(uuid);
        return dl > 0 && System.currentTimeMillis() > dl;
    }

    private void kickTimeout(Player p) {
        p.kickPlayer(msg("auth_time_expired"));
    }

    // ========================= ADMIN =========================

    private boolean handleAdminRoot(CommandSender sender, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW +
                    "/soldinregister reload | adminchangepass <nick> <new> | admindelete <nick> | "
                    + "2fa <tg|enable|confirm|disable|unbind|reset> [args] | 2faurl");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload":
                if (!sender.hasPermission("soldinregister.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен.");
                return true;

            case "adminchangepass":
                if (!sender.hasPermission("soldinregister.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.YELLOW + "Исп: /soldinregister adminchangepass <ник> <новый>");
                    return true;
                }
                UserRecord ru = plugin.storage().getByName(args[1]);
                if (ru == null) {
                    sender.sendMessage(ChatColor.RED + "Игрок не найден.");
                    return true;
                }
                PasswordUtil.HashPack hp2 = PasswordUtil.hashPassword(args[2]);
                ru.hash = hp2.hash;
                ru.salt = hp2.salt;
                ru.iterations = hp2.iterations;
                plugin.storage().update(ru);
                sender.sendMessage(ChatColor.GREEN + "Пароль обновлён для " + ru.name);
                return true;

            case "admindelete":
                if (!sender.hasPermission("soldinregister.admin")) {
                    sender.sendMessage(ChatColor.RED + "Нет прав.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Исп: /soldinregister admindelete <ник>");
                    return true;
                }
                UserRecord du = plugin.storage().getByName(args[1]);
                if (du == null) {
                    sender.sendMessage(ChatColor.RED + "Игрок не найден.");
                    return true;
                }
                plugin.storage().delete(du.uuid);
                sender.sendMessage(ChatColor.GREEN + "Аккаунт удалён: " + du.name);
                return true;

            // ========================= 2FA & Telegram =========================

            case "2fa":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только игрок.");
                    return true;
                }

                Player pl2 = (Player) sender;
                UserRecord u2 = plugin.storage().getByUUID(pl2.getUniqueId());

                if (u2 == null) {
                    pl2.sendMessage("Сначала зарегистрируйся.");
                    return true;
                }

                if (args.length < 2) {
                    pl2.sendMessage(ChatColor.YELLOW + "/soldinregister 2fa <tg|enable|confirm|disable|unbind|reset>");
                    return true;
                }

                // --- Привязка Telegram ---
                if (args[1].equalsIgnoreCase("tg")) {
                    if (plugin.getTelegramBot() == null || !plugin.getTelegramBot().isEnabled()) {
                        pl2.sendMessage(ChatColor.RED + "Telegram-бот 2FA не настроен.");
                        return true;
                    }

                    String code = com.soldin.register.telegram.LinkCodeManager.generate(pl2.getUniqueId());
                    pl2.sendMessage(ChatColor.AQUA + "Код для привязки Telegram: " + ChatColor.YELLOW + code);
                    pl2.sendMessage(ChatColor.GRAY + "Отправь этот код боту в Telegram командой: /code " + code);
                    return true;
                }

                switch (args[1].toLowerCase()) {

                    case "enable":
                        if (u2.twoFASecret != null) {
                            pl2.sendMessage(ChatColor.RED + "2FA уже включена.");
                            return true;
                        }
                        String secret = TOTPUtil.generateSecret();
                        String issuer = plugin.getConfig().getString("twofa.issuer", "SoldinServer");
                        String account = pl2.getName();
                        String otpauth = TOTPUtil.buildOtpAuthURL(issuer, account, secret);

                        plugin.getConfig().set("runtime.tmp2fa." + pl2.getUniqueId(), secret);
                        plugin.saveConfig();

                        pl2.sendMessage(ChatColor.GREEN + "Секрет сгенерирован. Добавь в Google Authenticator:");
                        pl2.sendMessage(ChatColor.AQUA + otpauth);
                        pl2.sendMessage(ChatColor.YELLOW + "Теперь введи: /soldinregister 2fa confirm <код>");
                        return true;

                    case "confirm":
                        if (args.length < 3) {
                            pl2.sendMessage(ChatColor.YELLOW + "/soldinregister 2fa confirm <код>");
                            return true;
                        }

                        String pending =
                                plugin.getConfig().getString("runtime.tmp2fa." + pl2.getUniqueId());

                        if (pending == null) {
                            pl2.sendMessage(ChatColor.RED + "Секрет не сгенерирован. /soldinregister 2fa enable");
                            return true;
                        }

                        if (!TOTPUtil.verifyCode(pending, args[2],
                                plugin.getConfig().getInt("twofa.totp_window", 1))) {
                            pl2.sendMessage(ChatColor.RED + "Неверный код.");
                            return true;
                        }

                        u2.twoFASecret = pending;
                        plugin.storage().update(u2);

                        plugin.getConfig().set("runtime.tmp2fa." + pl2.getUniqueId(), null);
                        plugin.saveConfig();

                        pl2.sendMessage(ChatColor.GREEN + "2FA включена!");
                        return true;

                    case "disable":
                        if (u2.twoFASecret == null) {
                            pl2.sendMessage(ChatColor.RED + "2FA не включена.");
                            return true;
                        }
                        if (args.length < 3) {
                            pl2.sendMessage(ChatColor.YELLOW + "/soldinregister 2fa disable <код>");
                            return true;
                        }

                        if (!TOTPUtil.verifyCode(u2.twoFASecret, args[2],
                                plugin.getConfig().getInt("twofa.totp_window", 1))) {
                            pl2.sendMessage(ChatColor.RED + "Неверный код.");
                            return true;
                        }

                        u2.twoFASecret = null;
                        plugin.storage().update(u2);

                        pl2.sendMessage(ChatColor.GREEN + "2FA выключена.");
                        return true;

                    case "unbind":
                        if (args.length < 3) {
                            pl2.sendMessage(ChatColor.YELLOW + "/soldinregister 2fa unbind <пароль>");
                            return true;
                        }

                        if (u2.twoFASecret == null) {
                            pl2.sendMessage(ChatColor.RED + "2FA не включена.");
                            return true;
                        }

                        if (!PasswordUtil.verify(args[2], u2)) {
                            pl2.sendMessage(ChatColor.RED + "Пароль неверен.");
                            return true;
                        }

                        u2.twoFASecret = null;
                        plugin.storage().update(u2);

                        pl2.sendMessage(ChatColor.GREEN + "2FA отвязана.");
                        return true;

                    case "reset":
                        if (!pl2.hasPermission("soldinregister.admin")) {
                            pl2.sendMessage(ChatColor.RED + "Нет прав.");
                            return true;
                        }

                        if (args.length < 3) {
                            pl2.sendMessage(ChatColor.YELLOW + "/soldinregister 2fa reset <ник>");
                            return true;
                        }

                        UserRecord tu = plugin.storage().getByName(args[2]);
                        if (tu == null) {
                            pl2.sendMessage(ChatColor.RED + "Игрок не найден.");
                            return true;
                        }

                        tu.twoFASecret = null;
                        plugin.storage().update(tu);

                        pl2.sendMessage(ChatColor.GREEN + "2FA сброшена у " + tu.name);
                        return true;

                    default:
                        pl2.sendMessage(ChatColor.YELLOW +
                                "/soldinregister 2fa <tg|enable|confirm|disable|unbind|reset>");
                        return true;
                }

            // ========================= 2FA URL =========================

            case "2faurl":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Только игрок.");
                    return true;
                }

                Player pl3 = (Player) sender;
                UserRecord ur = plugin.storage().getByUUID(pl3.getUniqueId());

                if (ur == null || ur.twoFASecret == null) {
                    pl3.sendMessage(ChatColor.RED + "2FA не включена.");
                    return true;
                }

                String issuer3 = plugin.getConfig()
                        .getString("twofa.issuer", "SoldinServer");

                String otpauth3 =
                        TOTPUtil.buildOtpAuthURL(issuer3, pl3.getName(), ur.twoFASecret);

                pl3.sendMessage(ChatColor.AQUA + otpauth3);
                return true;
        }

        return true;
    }
}
