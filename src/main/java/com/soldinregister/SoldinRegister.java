
package com.soldinregister;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SoldinRegister extends JavaPlugin implements Listener {

    private Set<UUID> lockedPlayers = new HashSet<>();
    private Map<UUID, Integer> attempts = new HashMap<>();
    private Map<UUID, Boolean> loggedIn = new HashMap<>();
    private int timeoutSeconds;
    private String siteUrl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        timeoutSeconds = getConfig().getInt("login-timeout", 60);
        siteUrl = getConfig().getString("qr-service", "https://chart.googleapis.com/chart?chs=200x200&cht=qr&chl=");

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID id : lockedPlayers) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        if (!isRegistered(p)) {
                            p.sendMessage(ChatColor.YELLOW + "Введите " + ChatColor.GREEN + "/register <пароль>");
                        } else if (!isLogged(p)) {
                            if (has2FA(p)) {
                                p.sendMessage(ChatColor.YELLOW + "Введите " + ChatColor.GREEN + "/login <пароль> <код2FA>");
                            } else {
                                p.sendMessage(ChatColor.YELLOW + "Введите " + ChatColor.GREEN + "/login <пароль>");
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        lockedPlayers.add(p.getUniqueId());
        attempts.put(p.getUniqueId(), 3);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (lockedPlayers.contains(p.getUniqueId())) {
                    p.kickPlayer("Время на авторизацию истекло!");
                }
            }
        }.runTaskLater(this, 20L * timeoutSeconds);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (lockedPlayers.contains(e.getPlayer().getUniqueId())) {
            if (e.getFrom().distance(e.getTo()) > 0) {
                e.setTo(e.getFrom());
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (lockedPlayers.contains(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Сначала авторизуйтесь!");
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (lockedPlayers.contains(e.getPlayer().getUniqueId())) {
            String cmd = e.getMessage().toLowerCase();
            if (!(cmd.startsWith("/login") || cmd.startsWith("/register"))) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(ChatColor.RED + "Вы не можете использовать команды до авторизации!");
            }
        }
    }

    private boolean isRegistered(Player p) {
        return false; // TODO DB logic
    }

    private boolean isLogged(Player p) {
        return loggedIn.getOrDefault(p.getUniqueId(), false);
    }

    private boolean has2FA(Player p) {
        return false; // TODO check DB
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (isRegistered(p)) {
                p.sendMessage(ChatColor.RED + "Вы уже зарегистрированы!");
                return true;
            }
            if (args.length != 1) {
                p.sendMessage(ChatColor.RED + "Используйте: /register <пароль>");
                return true;
            }
            p.sendMessage(ChatColor.GREEN + "Вы зарегистрированы! Теперь войдите: /login <пароль>");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (!isRegistered(p)) {
                p.sendMessage(ChatColor.RED + "Сначала зарегистрируйтесь!");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ChatColor.RED + "Используйте: /login <пароль> [код2FA]");
                return true;
            }
            int tries = attempts.getOrDefault(p.getUniqueId(), 3);
            if (tries <= 1) {
                p.kickPlayer("Слишком много неверных попыток!");
                return true;
            }
            attempts.put(p.getUniqueId(), tries - 1);
            p.sendMessage(ChatColor.GREEN + "Вы вошли!");
            lockedPlayers.remove(p.getUniqueId());
            loggedIn.put(p.getUniqueId(), true);
            return true;
        }

        return false;
    }
}
