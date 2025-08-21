package com.soldinregister;

import com.soldinregister.db.UserService;
import com.soldinregister.util.TOTPUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SoldinRegister extends JavaPlugin implements Listener {

    private Set<UUID> locked = new HashSet<>();
    private Map<UUID, Integer> attempts = new HashMap<>();
    private Set<UUID> logged = new HashSet<>();

    private UserService users;
    private String prefix;
    private String chartUrl;
    private String issuer;
    private int timeout;
    private int maxAttempts;
    private int maxPerIp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        setupDB();
        Bukkit.getPluginManager().registerEvents(this, this);

        // 1 сек оповещения
        new BukkitRunnable() {
            @Override public void run() {
                for (UUID id : locked) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    if (!users.isRegistered(p.getUniqueId())) {
                        p.sendMessage(color(prefix + getConfig().getString("messages.not-registered")));
                    } else if (!logged.contains(id)) {
                        if (users.get2FA(id) != null) {
                            p.sendMessage(color(prefix + getConfig().getString("messages.need-login-2fa")));
                        } else {
                            p.sendMessage(color(prefix + getConfig().getString("messages.need-login")));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void loadConfigValues() {
        prefix = getConfig().getString("messages.prefix", "&6[SoldinRegister]&r ");
        chartUrl = getConfig().getString("qr.chart-url");
        issuer = getConfig().getString("security.twofactor.issuer", "SoldinRegister");
        timeout = getConfig().getInt("session.login-timeout-seconds", 60);
        maxAttempts = getConfig().getInt("session.max-login-attempts", 3);
        maxPerIp = getConfig().getInt("limits.max-accounts-per-ip", 3);
    }

    private void setupDB() {
        String type = getConfig().getString("storage.type", "sqlite");
        String sqlite = getConfig().getString("storage.sqlite-path");
        String host = getConfig().getString("storage.mysql.host");
        int port = getConfig().getInt("storage.mysql.port");
        String db = getConfig().getString("storage.mysql.database");
        String user = getConfig().getString("storage.mysql.user");
        String pass = getConfig().getString("storage.mysql.password");
        int rounds = getConfig().getInt("security.password.bcrypt-rounds", 10);
        users = new UserService(type, sqlite, host, port, db, user, pass, rounds);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        locked.add(p.getUniqueId());
        attempts.put(p.getUniqueId(), maxAttempts);
        // таймер кика
        new BukkitRunnable() {
            @Override public void run() {
                if (locked.contains(p.getUniqueId())) {
                    p.kick(color(getConfig().getString("messages.timeout-kick")));
                }
            }
        }.runTaskLater(this, timeout * 20L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (locked.contains(e.getPlayer().getUniqueId())) {
            if (e.getFrom().distance(e.getTo()) > 0) {
                e.setTo(e.getFrom());
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (locked.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(color(prefix + getConfig().getString("messages.no-chat")));
        }
    }

    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (locked.contains(e.getPlayer().getUniqueId())) {
            String m = e.getMessage().toLowerCase();
            if (!(m.startsWith("/login") || m.startsWith("/l ") || m.equals("/l")
                    || m.startsWith("/register") || m.startsWith("/reg ") || m.equals("/reg"))) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(color(prefix + getConfig().getString("messages.no-cmd")));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("register") || cmd.getName().equalsIgnoreCase("reg")) {
            if (!(sender instanceof Player p)) return true;
            if (users.isRegistered(p.getUniqueId())) {
                p.sendMessage(color(prefix + getConfig().getString("messages.already-registered")));
                return true;
            }
            if (args.length != 1) {
                p.sendMessage(color(prefix + getConfig().getString("messages.not-registered")));
                return true;
            }
            String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "unknown";
            if (users.countByIP(ip) >= maxPerIp) {
                p.sendMessage(color(prefix + getConfig().getString("messages.ip-limit")));
                return true;
            }
            if (users.register(p.getUniqueId(), p.getName(), ip, args[0])) {
                p.sendMessage(color(prefix + getConfig().getString("messages.registered")));
            } else {
                p.sendMessage(color(prefix + "&cОшибка регистрации."));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login") || cmd.getName().equalsIgnoreCase("l")) {
            if (!(sender instanceof Player p)) return true;
            if (!users.isRegistered(p.getUniqueId())) {
                p.sendMessage(color(prefix + getConfig().getString("messages.not-registered")));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(color(prefix + getConfig().getString("messages.need-login")));
                return true;
            }
            int left = attempts.getOrDefault(p.getUniqueId(), maxAttempts);
            if (left <= 0) {
                p.kick(color(getConfig().getString("messages.attempts-kick")));
                return true;
            }
            if (!users.validatePassword(p.getUniqueId(), args[0])) {
                attempts.put(p.getUniqueId(), left - 1);
                p.sendMessage(color(prefix + getConfig().getString("messages.attempts-left").replace("{left}", String.valueOf(left - 1))));
                if (left - 1 <= 0) p.kick(color(getConfig().getString("messages.attempts-kick")));
                return true;
            }
            String secret = users.get2FA(p.getUniqueId());
            if (secret != null) {
                if (args.length < 2) {
                    p.sendMessage(color(prefix + getConfig().getString("messages.need-2fa")));
                    return true;
                }
                try {
                    int code = Integer.parseInt(args[1]);
                    boolean ok = TOTPUtil.verifyCode(secret, code, System.currentTimeMillis());
                    if (!ok) {
                        p.sendMessage(color(prefix + getConfig().getString("messages.bad-2fa")));
                        return true;
                    }
                } catch (NumberFormatException ex) {
                    p.sendMessage(color(prefix + "&cКод 2FA должен быть числом."));
                    return true;
                }
            }
            locked.remove(p.getUniqueId());
            logged.add(p.getUniqueId());
            p.sendMessage(color(prefix + getConfig().getString("messages.login-ok")));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("changepass")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length != 2) {
                p.sendMessage(color(prefix + "&eИспользуйте: /changepass <старый> <новый>"));
                return true;
            }
            if (!users.validatePassword(p.getUniqueId(), args[0])) {
                p.sendMessage(color(prefix + getConfig().getString("messages.bad-password")));
                return true;
            }
            if (users.changePassword(p.getUniqueId(), args[1])) {
                p.sendMessage(color(prefix + getConfig().getString("messages.pass-changed")));
            } else p.sendMessage(color(prefix + "&cОшибка смены пароля."));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("2fa")) {
            if (!(sender instanceof Player p)) return true;
            if (args.length == 0) {
                p.sendMessage(color(prefix + "&e/2fa enable|confirm <код>|disable <код>|unbind <пароль>|url"));
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "enable" -> {
                    String secret = TOTPUtil.generateSecret();
                    users.set2FA(p.getUniqueId(), secret); // временно ставим, подтверждение через confirm
                    String url = TOTPUtil.buildOtpAuthUrl(issuer, p.getName(), secret);
                    String full = chartUrl + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8);
                    p.sendMessage(color(prefix + getConfig().getString("messages.twofa-url").replace("{url}", url)));
                    p.sendMessage(Component.text(ChatColor.translateAlternateColorCodes('&', prefix + getConfig().getString("messages.twofa-qr").replace("{url}", full)))
                            .clickEvent(ClickEvent.openUrl(full)));
                    p.sendMessage(color(prefix + "&7Введите &a/2fa confirm <код> &7из приложения."));
                    return true;
                }
                case "confirm" -> {
                    if (args.length < 2) {
                        p.sendMessage(color(prefix + "&eИспользуйте: /2fa confirm <код>"));
                        return true;
                    }
                    String secret = users.get2FA(p.getUniqueId());
                    if (secret == null) {
                        p.sendMessage(color(prefix + "&c2FA ещё не включалась."));
                        return true;
                    }
                    try {
                        int code = Integer.parseInt(args[1]);
                        if (TOTPUtil.verifyCode(secret, code, System.currentTimeMillis())) {
                            p.sendMessage(color(prefix + getConfig().getString("messages.twofa-enabled")));
                        } else {
                            p.sendMessage(color(prefix + getConfig().getString("messages.bad-2fa")));
                        }
                    } catch (NumberFormatException ex) {
                        p.sendMessage(color(prefix + "&cКод должен быть числом."));
                    }
                    return true;
                }
                case "disable" -> {
                    if (args.length < 2) {
                        p.sendMessage(color(prefix + "&eИспользуйте: /2fa disable <код>"));
                        return true;
                    }
                    String secret = users.get2FA(p.getUniqueId());
                    if (secret == null) {
                        p.sendMessage(color(prefix + "&e2FA уже отключена."));
                        return true;
                    }
                    try {
                        int code = Integer.parseInt(args[1]);
                        if (TOTPUtil.verifyCode(secret, code, System.currentTimeMillis())) {
                            users.clear2FA(p.getUniqueId());
                            p.sendMessage(color(prefix + getConfig().getString("messages.twofa-disabled")));
                        } else p.sendMessage(color(prefix + getConfig().getString("messages.bad-2fa")));
                    } catch (NumberFormatException ex) {
                        p.sendMessage(color(prefix + "&cКод должен быть числом."));
                    }
                    return true;
                }
                case "unbind" -> {
                    if (args.length < 2) {
                        p.sendMessage(color(prefix + "&eИспользуйте: /2fa unbind <пароль>"));
                        return true;
                    }
                    if (!users.validatePassword(p.getUniqueId(), args[1])) {
                        p.sendMessage(color(prefix + getConfig().getString("messages.bad-password")));
                        return true;
                    }
                    users.clear2FA(p.getUniqueId());
                    p.sendMessage(color(prefix + getConfig().getString("messages.twofa-disabled")));
                    return true;
                }
                case "url" -> {
                    String secret = users.get2FA(p.getUniqueId());
                    if (secret == null) {
                        p.sendMessage(color(prefix + "&c2FA не активна."));
                        return true;
                    }
                    String url = TOTPUtil.buildOtpAuthUrl(issuer, p.getName(), secret);
                    String full = chartUrl + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8);
                    p.sendMessage(color(prefix + getConfig().getString("messages.twofa-url").replace("{url}", url)));
                    p.sendMessage(Component.text(ChatColor.translateAlternateColorCodes('&', prefix + getConfig().getString("messages.twofa-qr").replace("{url}", full)))
                            .clickEvent(ClickEvent.openUrl(full)));
                    return true;
                }
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("soldinregister")) {
            if (!sender.hasPermission("soldinregister.admin")) {
                sender.sendMessage(color(prefix + "&cНедостаточно прав."));
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage(color(prefix + "&e/soldinregister reload|changepass <игрок> <пароль>|delete <игрок>|reset2fa <игрок>"));
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    reloadConfig();
                    loadConfigValues();
                    sender.sendMessage(color(prefix + "&aКонфиг перезагружен."));
                }
                case "changepass" -> {
                    if (args.length < 3) { sender.sendMessage(color(prefix + "&eИспользуйте: /soldinregister changepass <игрок> <пароль>")); break; }
                    UUID u = users.findUUIDByName(args[1]);
                    if (u == null) { sender.sendMessage(color(prefix + "&cИгрок не найден.")); break; }
                    boolean ok = users.changePassword(u, args[2]);
                    sender.sendMessage(color(prefix + (ok ? getConfig().getString("messages.admin-pass-changed").replace("{player}", args[1]) : "&cОшибка смены пароля.")));
                }
                case "delete" -> {
                    if (args.length < 2) { sender.sendMessage(color(prefix + "&eИспользуйте: /soldinregister delete <игрок>")); break; }
                    boolean ok = users.deleteByName(args[1]);
                    sender.sendMessage(color(prefix + (ok ? getConfig().getString("messages.user-deleted").replace("{player}", args[1]) : "&cОшибка удаления.")));
                }
                case "reset2fa" -> {
                    if (args.length < 2) { sender.sendMessage(color(prefix + "&eИспользуйте: /soldinregister reset2fa <игрок>")); break; }
                    UUID u = users.findUUIDByName(args[1]);
                    if (u == null) { sender.sendMessage(color(prefix + "&cИгрок не найден.")); break; }
                    boolean ok = users.clear2FA(u);
                    sender.sendMessage(color(prefix + (ok ? getConfig().getString("messages.twofa-reset").replace("{player}", args[1]) : "&cОшибка сброса 2FA.")));
                }
            }
            return true;
        }

        return false;
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
