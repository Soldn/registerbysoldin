package com.soldinregister.db;

import com.soldinregister.util.HashUtil;

import java.sql.*;
import java.util.UUID;

public class UserService {
    private final String type;
    private final String sqlitePath;
    private final String mysqlUrl;
    private final String mysqlUser;
    private final String mysqlPass;
    private final int bcryptRounds;

    public UserService(String type, String sqlitePath, String mysqlHost, int mysqlPort, String mysqlDb, String mysqlUser, String mysqlPass, int bcryptRounds) {
        this.type = type.toLowerCase();
        this.sqlitePath = sqlitePath;
        this.mysqlUrl = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDb + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        this.mysqlUser = mysqlUser;
        this.mysqlPass = mysqlPass;
        this.bcryptRounds = bcryptRounds;
        init();
    }

    private Connection connect() throws SQLException {
        if (type.equals("mysql")) {
            return DriverManager.getConnection(mysqlUrl, mysqlUser, mysqlPass);
        } else {
            return DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
        }
    }

    private void init() {
        try (Connection c = connect();
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "ip TEXT," +
                    "passhash TEXT NOT NULL," +
                    "twofa_secret TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "last_login TIMESTAMP)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_ip ON users(ip)");
            st.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_name ON users(name)");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isRegistered(UUID uuid) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean isRegisteredByName(String name) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean register(UUID uuid, String name, String ip, String password) {
        if (isRegistered(uuid)) return false;
        String hash = HashUtil.hash(password, bcryptRounds);
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(uuid,name,ip,passhash) VALUES(?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, ip);
            ps.setString(4, hash);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean validatePassword(UUID uuid, String password) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT passhash FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString(1);
                    return HashUtil.check(password, hash);
                }
                return false;
            }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean changePassword(UUID uuid, String newPassword) {
        String hash = HashUtil.hash(newPassword, bcryptRounds);
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET passhash=? WHERE uuid=?")) {
            ps.setString(1, hash);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public int countByIP(String ip) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE ip=?")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) { e.printStackTrace(); return 0; }
    }

    public boolean set2FA(UUID uuid, String secret) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET twofa_secret=? WHERE uuid=?")) {
            ps.setString(1, secret);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public boolean clear2FA(UUID uuid) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET twofa_secret=NULL WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public String get2FA(UUID uuid) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT twofa_secret FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    public UUID findUUIDByName(String name) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT uuid FROM users WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.UUID.fromString(rs.getString(1));
                return null;
            }
        } catch (SQLException e) { e.printStackTrace(); return null; }
    }

    public boolean deleteByName(String name) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }
}
