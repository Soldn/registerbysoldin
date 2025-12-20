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
    public AuthCommands(SoldinRegister plugin) {
        this.plugin = plugin;
    }

    private String msg(String path) {
        return ChatColor.translateAlternateColorCodes(
                '&',
                plugin.getConfig().getString("messages." + path, path)
        );
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

        // ✅ IP получаем ОДИН раз
        String ip = p.getAddress() != null
                ? p.getAddress().getAddress().getHostAddress()
                : "unknown";

        switch (name) {

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

                int count = plugin.storage().countByIP(ip);
                int max = plugin.getConfig().getInt("limits.max_accounts_per_ip", 3);
                if (max > 0 && count >= max) {
                    p.sendMessage(msg("ip_limit_reached"));
                    return true;
                }

                PasswordUtil.HashPack hp = PasswordUtil.hashPassword(pass);
                UserRecord r = new UserRecord(
                        uuid,
                        p.getName(),
                        hp.hash,
                        hp.salt,
                        hp.iterations,
                        ip,
                        System.currentTimeMillis(),
                        0L,
                        null
                );

                plugin.storage().create(r);
                plugin.setAuthenticated(uuid);
                p.sendMessage(msg("registered_ok"));
                return true;

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

                if (!PasswordUtil.verify(args[0], u)) {
                    plugin.decAttempt(uuid);
                    int left = plugin.getAttemptsLeft(uuid);
                    if (left <= 0) {
                        p.kickPlayer(msg("login_attempts_exceeded"));
                        plugin.resetAttempts(uuid);
                    } else {
                        p.sendMessage(msg("login_fail_with_left")
                                .replace("{left}", String.valueOf(left)));
                    }
                    return true;
                }

                if (plugin.getConfig().getBoolean("enable-2fa", true)
                        && plugin.getConfig().getBoolean("twofa.require_if_enabled", true)
                        && u.twoFASecret != null) {

                    if (args.length < 2) {
                        p.sendMessage(msg("twofa_required"));
                        return true;
                    }

                    if (!TOTPUtil.verifyCode(
                            u.twoFASecret,
                            args[1],
                            plugin.getConfig().getInt("twofa.totp_window", 1)
                    )) {
                        plugin.decAttempt(uuid);
                        int left = plugin.getAttemptsLeft(uuid);
                        if (left <= 0) {
                            p.kickPlayer(msg("login_attempts_exceeded"));
                            plugin.resetAttempts(uuid);
                        } else {
                            p.sendMessage(msg("twofa_wrong_with_left")
                                    .replace("{left}", String.valueOf(left)));
                        }
                        return true;
                    }
                }

                // Telegram 2FA
                if (plugin.handleTelegramLogin(p, u, ip)) {
                    return true;
                }

                u.lastLogin = System.currentTimeMillis();
                u.ip = ip;
                plugin.storage().update(u);

                plugin.setAuthenticated(uuid);
                p.sendMessage(msg("login_ok"));
                plugin.sendProtectAccountMessage(p);
                return true;

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

    private boolean expired(UUID uuid) {
        long dl = plugin.getAuthDeadline(uuid);
        return dl > 0 && System.currentTimeMillis() > dl;
    }

    private void kickTimeout(Player p) {
        p.kickPlayer(msg("auth_time_expired"));
    }

    // admin-часть без изменений
    private boolean handleAdminRoot(CommandSender sender, String[] args) {
        // (оставлена как у тебя)
        return true;
    }
}
