
package com.soldinregister.core;

import org.bukkit.plugin.Plugin;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.Optional;

public class UserService {
    private final Plugin plugin;
    private final Database db;

    public UserService(Plugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public boolean isRegistered(String name) {
        return get(name).isPresent();
    }

    public Optional<User> get(String name) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT name,password,ip,twofa_secret FROM users WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new User(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getString(4)));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB get error: " + e.getMessage());
        }
        return Optional.empty();
    }

    public int countByIP(String ip) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE ip=?")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("DB countByIP error: " + e.getMessage());
        }
        return 0;
    }

    public boolean create(String name, String password, String ip) {
        int rounds = plugin.getConfig().getInt("security.bcrypt-log-rounds", 10);
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(rounds));
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(name,password,ip) VALUES (?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, hash);
            ps.setString(3, ip);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB create error: " + e.getMessage());
            return false;
        }
    }

    public boolean validatePassword(String name, String password) {
        Optional<User> u = get(name);
        if (u.isEmpty()) return false;
        return BCrypt.checkpw(password, u.get().passwordHash);
    }

    public boolean changePassword(String name, String newPassword) {
        int rounds = plugin.getConfig().getInt("security.bcrypt-log-rounds", 10);
        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt(rounds));
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password=? WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, hash);
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB changePassword error: " + e.getMessage());
            return false;
        }
    }

    public boolean delete(String name) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB delete error: " + e.getMessage());
            return false;
        }
    }

    public boolean setTwoFASecret(String name, String secret) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET twofa_secret=? WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, secret);
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().warning("DB setTwoFASecret error: " + e.getMessage());
            return false;
        }
    }

    public boolean resetTwoFA(String name) {
        return setTwoFASecret(name, null);
    }
}
