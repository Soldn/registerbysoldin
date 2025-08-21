
package com.soldinregister;

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

    private Set<UUID> lockedPlayers = new HashSet<>();
    private Map<UUID, Integer> loginAttempts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SoldinRegister enabled");

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

    private boolean isRegistered(Player p) {
        return false; // Реализация через базу
    }

    private boolean isLogged(Player p) {
        return false; // Реализация статуса
    }

    private boolean has2FA(Player p) {
        return false; // Реализация
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        lockedPlayers.add(p.getUniqueId());
        loginAttempts.put(p.getUniqueId(), 3);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (lockedPlayers.contains(p.getUniqueId())) {
                    p.kickPlayer("Время на авторизацию истекло!");
                }
            }
        }.runTaskLater(this, 20L * 60);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (lockedPlayers.contains(e.getPlayer().getUniqueId())) {
            e.getPlayer().sendMessage(ChatColor.RED + "Вы должны авторизоваться или зарегистрироваться!");
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (lockedPlayers.contains(p.getUniqueId())) {
            String msg = e.getMessage().toLowerCase();
            if (!(msg.startsWith("/login") || msg.startsWith("/register"))) {
                p.sendMessage(ChatColor.RED + "Вы не можете использовать команды, пока не авторизуетесь!");
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (lockedPlayers.contains(e.getPlayer().getUniqueId())) {
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
                e.getPlayer().teleport(e.getFrom());
            }
        }
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
            // регистрация
            p.sendMessage(ChatColor.GREEN + "Вы зарегистрированы!");
            lockedPlayers.remove(p.getUniqueId());
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (!isRegistered(p)) {
                p.sendMessage(ChatColor.RED + "У вас нет аккаунта! Используйте /register");
                return true;
            }
            if (args.length < 1) {
                p.sendMessage(ChatColor.RED + "Используйте: /login <пароль> [код2FA]");
                return true;
            }
            // логика логина
            p.sendMessage(ChatColor.GREEN + "Вы вошли!");
            lockedPlayers.remove(p.getUniqueId());
            return true;
        }

        return false;
    }
}
