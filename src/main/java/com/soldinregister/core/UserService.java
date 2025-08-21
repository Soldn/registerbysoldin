package com.soldinregister.core;

import org.mindrot.jbcrypt.BCrypt;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserService {
    private final Database db;
    private final Plugin plugin;

    public UserService(Database db, Plugin plugin) {
        this.db = db;
        this.plugin = plugin;
    }

    public void shutdown() { db.shutdown(); }

    public boolean isRegistered(UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean has2FA(UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT totp_secret, totp_confirmed FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String secret = rs.getString(1);
                boolean confirmed = rs.getBoolean(2);
                return secret != null && !secret.isEmpty() && confirmed;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean register(Player p, String password) {
        if (isRegistered(p.getUniqueId())) return false;

        // IP limit
        int limit = plugin.getConfig().getInt("limits.max-accounts-per-ip", 3);
        String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "0.0.0.0";
        if (countByIP(ip) >= limit) return false;

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(plugin.getConfig().getInt("security.password.bcrypt-rounds", 10)));
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(uuid, name, password, ip) VALUES(?,?,?,?)")) {
            ps.setString(1, p.getUniqueId().toString());
            ps.setString(2, p.getName());
            ps.setString(3, hash);
            ps.setString(4, ip);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    private int countByIP(String ip) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE ip=?")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean changePassword(Player p, String oldPass, String newPass) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT password FROM users WHERE uuid=?")) {
            ps.setString(1, p.getUniqueId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String hash = rs.getString(1);
                if (!BCrypt.checkpw(oldPass, hash)) return false;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }

        String hashNew = BCrypt.hashpw(newPass, BCrypt.gensalt(plugin.getConfig().getInt("security.password.bcrypt-rounds", 10)));
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password=? WHERE uuid=?")) {
            ps.setString(1, hashNew);
            ps.setString(2, p.getUniqueId().toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean adminChangePassword(String name, String newPass) {
        String hashNew = BCrypt.hashpw(newPass, BCrypt.gensalt(plugin.getConfig().getInt("security.password.bcrypt-rounds", 10)));
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password=? WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, hashNew);
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean adminDelete(String name) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean login(Player p, String pass) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT password FROM users WHERE uuid=?")) {
            ps.setString(1, p.getUniqueId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String hash = rs.getString(1);
                return org.mindrot.jbcrypt.BCrypt.checkpw(pass, hash);
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean update2FASecret(UUID uuid, String secret, boolean confirmed) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET totp_secret=?, totp_confirmed=? WHERE uuid=?")) {
            ps.setString(1, secret);
            ps.setBoolean(2, confirmed);
            ps.setString(3, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public String get2FASecret(UUID uuid) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT totp_secret FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean reset2FA(String name) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET totp_secret=NULL, totp_confirmed=0 WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { throw new RuntimeException(e); }
    }

    public boolean disable2FA(Player p, int code) {
        String sec = get2FASecret(p.getUniqueId());
        if (sec == null) return false;
        int window = plugin.getConfig().getInt("security.twofactor.window-steps", 1);
        if (!TOTPUtil.verifyCode(sec, code, window)) return false;
        return update2FASecret(p.getUniqueId(), null, false);
    }

    public boolean confirm2FA(Player p, int code) {
        String sec = get2FASecret(p.getUniqueId());
        if (sec == null) return false;
        int window = plugin.getConfig().getInt("security.twofactor.window-steps", 1);
        if (!TOTPUtil.verifyCode(sec, code, window)) return false;
        return update2FASecret(p.getUniqueId(), sec, true);
    }
}
