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

public \1private final java.util.Map<java.util.UUID, Long> lastLoginTry = new java.util.HashMap<>();
private final SoldinRegister plugin;
    public AuthCommands(SoldinRegister plugin) { this.plugin = plugin; }

    private String msg(String path) { return ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages."+path, path)); }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();
        if (name.equals("soldinregister")) {
            return handleAdminRoot(sender, args);
        }
        if (!(sender instanceof Player)) { sender.sendMessage("Только игрок."); return true; }
        Player p = (Player) sender;
        UUID uuid = p.getUniqueId();

        switch (name) {
            \1{
    org.bukkit.entity.Player pCooldown = (sender instanceof org.bukkit.entity.Player) ? (org.bukkit.entity.Player)sender : null;
    if (pCooldown != null) {
        java.util.UUID uuidCooldown = pCooldown.getUniqueId();
        long last = lastLoginTry.getOrDefault(uuidCooldown, 0L);
        if (System.currentTimeMillis() - last < 2000) {
            pCooldown.sendMessage(org.bukkit.ChatColor.RED + "Слишком часто! Подожди пару секунд.");
            return true;
        }
        lastLoginTry.put(uuidCooldown, System.currentTimeMillis());
    }
}
\1{
    org.bukkit.entity.Player pCooldown = (sender instanceof org.bukkit.entity.Player) ? (org.bukkit.entity.Player)sender : null;
    if (pCooldown != null) {
        java.util.UUID uuidCooldown = pCooldown.getUniqueId();
        long last = lastLoginTry.getOrDefault(uuidCooldown, 0L);
        if (System.currentTimeMillis() - last < 2000) {
            pCooldown.sendMessage(org.bukkit.ChatColor.RED + "Слишком часто! Подожди пару секунд.");
            return true;
        }
        lastLoginTry.put(uuidCooldown, System.currentTimeMillis());
    }
}
if (plugin.storage().getByUUID(uuid) != null) { p.sendMessage(msg("already_registered")); return true; }
                if (expired(uuid)) { kickTimeout(p); return true; }
                if (args.length < 1) { p.sendMessage(msg("usage_register")); return true; }
                String pass = args[0];
                if (pass.length() < plugin.getConfig().getInt("security.min_password_length", 5)) { p.sendMessage(msg("password_too_short")); return true; }
                String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
                int count = plugin.storage().countByIP(ip);
                int max = plugin.getConfig().getInt("limits.max_accounts_per_ip", 3);
                if (max > 0 && count >= max) { p.sendMessage(msg("ip_limit_reached")); return true; }
                PasswordUtil.HashPack hp = PasswordUtil.hashPassword(pass);
                UserRecord r = new UserRecord(uuid, p.getName(), hp.hash, hp.salt, hp.iterations, ip, System.currentTimeMillis(), 0L, null);
                plugin.storage().create(r);
                plugin.setAuthenticated(uuid);
                p.sendMessage(msg("registered_ok"));
                return true;
            \1{
    org.bukkit.entity.Player pCooldown = (sender instanceof org.bukkit.entity.Player) ? (org.bukkit.entity.Player)sender : null;
    if (pCooldown != null) {
        java.util.UUID uuidCooldown = pCooldown.getUniqueId();
        long last = lastLoginTry.getOrDefault(uuidCooldown, 0L);
        if (System.currentTimeMillis() - last < 2000) {
            pCooldown.sendMessage(org.bukkit.ChatColor.RED + "Слишком часто! Подожди пару секунд.");
            return true;
        }
        lastLoginTry.put(uuidCooldown, System.currentTimeMillis());
    }
}
case "l":
                UserRecord u = plugin.storage().getByUUID(uuid);
                if (u == null) { p.sendMessage(msg("not_registered")); return true; }
                if (expired(uuid)) { kickTimeout(p); return true; }
                if (args.length < 1) { p.sendMessage(msg("usage_login")); return true; }
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
                if (plugin.getConfig().getBoolean("enable-2fa", true) && plugin.getConfig().getBoolean("twofa.require_if_enabled", true) && u.twoFASecret != null) {
                    if (args.length < 2) { p.sendMessage(msg("twofa_required")); return true; }
                    String code = args[1];
                    if (!TOTPUtil.verifyCode(u.twoFASecret, code, plugin.getConfig().getInt("twofa.totp_window", 1))) {
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
                u.lastLogin = System.currentTimeMillis();
                plugin.storage().update(u);
                plugin.setAuthenticated(uuid);
                p.sendMessage(msg("login_ok"));
                return true;
            case "changepass":
                UserRecord cu = plugin.storage().getByUUID(uuid);
                if (cu == null) { p.sendMessage(msg("not_registered")); return true; }
                if (args.length < 2) { p.sendMessage(msg("usage_changepass")); return true; }
                if (!PasswordUtil.verify(args[0], cu)) { p.sendMessage(msg("wrong_old_pass")); return true; }
                if (args[1].length() < plugin.getConfig().getInt("security.min_password_length", 5)) { p.sendMessage(msg("password_too_short")); return true; }
                PasswordUtil.HashPack nh = PasswordUtil.hashPassword(args[1]);
                cu.hash = nh.hash; cu.salt = nh.salt; cu.iterations = nh.iterations;
                plugin.storage().update(cu);
                p.sendMessage(msg("changepass_ok"));
                return true;
        }
        return false;
    }

    private boolean expired(UUID uuid){
        long dl = plugin.getAuthDeadline(uuid);
        return dl > 0 && System.currentTimeMillis() > dl;
    }
    private void kickTimeout(Player p){ p.kickPlayer(msg("auth_time_expired")); }

    private boolean handleAdminRoot(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW+"/soldinregister reload | adminchangepass <nick> <new> | admindelete <nick> | 2fa <enable|confirm|disable|unbind|reset> [args] | 2faurl");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("soldinregister.admin")) { sender.sendMessage(ChatColor.RED+"Нет прав."); return true; }
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN+"Конфиг перезагружен.");
                return true;
            case "adminchangepass":
                if (!sender.hasPermission("soldinregister.admin")) { sender.sendMessage(ChatColor.RED+"Нет прав."); return true; }
                if (args.length < 3) { sender.sendMessage(ChatColor.YELLOW+"Исп: /soldinregister adminchangepass <ник> <новый>"); return true; }
                UserRecord ru = plugin.storage().getByName(args[1]);
                if (ru == null) { sender.sendMessage(ChatColor.RED+"Игрок не найден."); return true; }
                PasswordUtil.HashPack hp = PasswordUtil.hashPassword(args[2]);
                ru.hash = hp.hash; ru.salt = hp.salt; ru.iterations = hp.iterations;
                plugin.storage().update(ru);
                sender.sendMessage(ChatColor.GREEN+"Пароль обновлён для "+ru.name);
                return true;
            case "admindelete":
                if (!sender.hasPermission("soldinregister.admin")) { sender.sendMessage(ChatColor.RED+"Нет прав."); return true; }
                if (args.length < 2) { sender.sendMessage(ChatColor.YELLOW+"Исп: /soldinregister admindelete <ник>"); return true; }
                UserRecord du = plugin.storage().getByName(args[1]);
                if (du == null) { sender.sendMessage(ChatColor.RED+"Игрок не найден."); return true; }
                plugin.storage().delete(du.uuid);
                sender.sendMessage(ChatColor.GREEN+"Аккаунт удалён: "+du.name);
                return true;
            case "2fa":
                if (!(sender instanceof Player)) { sender.sendMessage("Только игрок."); return true; }
                Player p = (Player) sender;
                UserRecord u = plugin.storage().getByUUID(p.getUniqueId());
                if (u == null) { p.sendMessage("Сначала зарегистрируйся."); return true; }
                if (args.length < 2) { p.sendMessage(ChatColor.YELLOW+"/soldinregister 2fa <enable|confirm|disable|unbind|reset> [код|ник]"); return true; }
                switch (args[1].toLowerCase()) {
                    case "enable":
                        if (u.twoFASecret != null) { p.sendMessage(ChatColor.RED+"2FA уже включена."); return true; }
                        String secret = com.soldin.register.util.TOTPUtil.generateSecret();
                        String issuer = plugin.getConfig().getString("twofa.issuer", "SoldinServer");
                        String account = p.getName();
                        String otpauth = com.soldin.register.util.TOTPUtil.buildOtpAuthURL(issuer, account, secret);
                        plugin.getConfig().set("runtime.tmp2fa."+p.getUniqueId(), secret);
                        plugin.saveConfig();
                        p.sendMessage(ChatColor.GREEN+"Секрет сгенерирован. Добавь в Google Authenticator по ссылке:");
                        p.sendMessage(ChatColor.AQUA+otpauth);
                        p.sendMessage(ChatColor.YELLOW+"Теперь введи: /soldinregister 2fa confirm <код>");
                        return true;
                    case "confirm":
                        if (args.length < 3) { p.sendMessage(ChatColor.YELLOW+"/soldinregister 2fa confirm <код>"); return true; }
                        String pending = plugin.getConfig().getString("runtime.tmp2fa."+p.getUniqueId());
                        if (pending == null) { p.sendMessage(ChatColor.RED+"Секрет не сгенерирован. /soldinregister 2fa enable"); return true; }
                        if (!com.soldin.register.util.TOTPUtil.verifyCode(pending, args[2], plugin.getConfig().getInt("twofa.totp_window", 1))) {
                            p.sendMessage(ChatColor.RED+"Неверный код.");
                            return true;
                        }
                        u.twoFASecret = pending; plugin.storage().update(u);
                        plugin.getConfig().set("runtime.tmp2fa."+p.getUniqueId(), null); plugin.saveConfig();
                        p.sendMessage(ChatColor.GREEN+"2FA включена!");
                        return true;
                    case "disable":
                        if (u.twoFASecret == null) { p.sendMessage(ChatColor.RED+"2FA не включена."); return true; }
                        if (args.length < 3) { p.sendMessage(ChatColor.YELLOW+"Нужно подтвердить кодом: /soldinregister 2fa disable <код>"); return true; }
                        if (!com.soldin.register.util.TOTPUtil.verifyCode(u.twoFASecret, args[2], plugin.getConfig().getInt("twofa.totp_window", 1))) {
                            p.sendMessage(ChatColor.RED+"Неверный код."); return true; }
                        u.twoFASecret = null; plugin.storage().update(u);
                        p.sendMessage(ChatColor.GREEN+"2FA выключена.");
                        return true;
                    case "unbind":
                        // игрок сам отвязывает 2FA, подтверждая текущим паролем
                        if (args.length < 3) { p.sendMessage(ChatColor.YELLOW+"Исп: /soldinregister 2fa unbind <пароль>"); return true; }
                        if (u.twoFASecret == null) { p.sendMessage(ChatColor.RED+"2FA не включена."); return true; }
                        if (!PasswordUtil.verify(args[2], u)) { p.sendMessage(ChatColor.RED+"Пароль неверен."); return true; }
                        u.twoFASecret = null; plugin.storage().update(u);
                        p.sendMessage(ChatColor.GREEN+"2FA отвязана от аккаунта.");
                        return true;
                    case "reset":
                        // админская отвязка 2FA у другого игрока
                        if (!p.hasPermission("soldinregister.admin")) { p.sendMessage(ChatColor.RED+"Нет прав."); return true; }
                        if (args.length < 3) { p.sendMessage(ChatColor.YELLOW+"Исп: /soldinregister 2fa reset <ник>"); return true; }
                        UserRecord tu = plugin.storage().getByName(args[2]);
                        if (tu == null) { p.sendMessage(ChatColor.RED+"Игрок не найден."); return true; }
                        tu.twoFASecret = null; plugin.storage().update(tu);
                        p.sendMessage(ChatColor.GREEN+"2FA сброшена у "+tu.name);
                        return true;
                    default:
                        p.sendMessage(ChatColor.YELLOW+"/soldinregister 2fa <enable|confirm|disable|unbind|reset> ...");
                        return true;
                }
            case "2faurl":
                if (!(sender instanceof Player)) { sender.sendMessage("Только игрок."); return true; }
                Player pl = (Player) sender;
                UserRecord ur = plugin.storage().getByUUID(pl.getUniqueId());
                if (ur == null || ur.twoFASecret == null) { pl.sendMessage(ChatColor.RED+"2FA не включена."); return true; }
                String issuer = plugin.getConfig().getString("twofa.issuer", "SoldinServer");
                String otpauth = com.soldin.register.util.TOTPUtil.buildOtpAuthURL(issuer, pl.getName(), ur.twoFASecret);
                pl.sendMessage(ChatColor.AQUA+otpauth);
                return true;
        }
        return true;
    }
}