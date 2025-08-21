package com.soldin.register.storage;

import com.soldin.register.SoldinRegister;
import com.soldin.register.model.UserRecord;

import java.sql.*;
import java.util.UUID;

public class SqlStorage implements Storage {
    private final SoldinRegister plugin;
    private Connection conn;
    public SqlStorage(SoldinRegister plugin) { this.plugin = plugin; }

    @Override public void connect() throws Exception {
        String type = plugin.getConfig().getString("storage.type", "SQLITE").toUpperCase();
        if (type.equals("MYSQL")) {
            String host = plugin.getConfig().getString("storage.mysql.host", "localhost");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String db = plugin.getConfig().getString("storage.mysql.database", "soldin");
            String user = plugin.getConfig().getString("storage.mysql.user", "root");
            String pass = plugin.getConfig().getString("storage.mysql.password", "");
            String params = plugin.getConfig().getString("storage.mysql.params", "useSSL=false&autoReconnect=true&characterEncoding=utf8");
            conn = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db + "?" + params, user, pass);
        } else {
            String path = plugin.getDataFolder().getAbsolutePath()+"/users.db";
            conn = DriverManager.getConnection("jdbc:sqlite:"+path);
        }
    }

    @Override public void init() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "name TEXT, " +
                    "hash TEXT, " +
                    "salt TEXT, " +
                    "iterations INTEGER, " +
                    "ip TEXT, " +
                    "registeredAt INTEGER, " +
                    "lastLogin INTEGER, " +
                    "twoFASecret TEXT" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_name ON users(name)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_ip ON users(ip)");
        }
    }

    @Override public void close() throws Exception { if (conn != null) conn.close(); }

    private UserRecord map(ResultSet rs) {
        try {
            if (rs == null) return null;
            UUID uuid = UUID.fromString(rs.getString("uuid"));
            return new UserRecord(uuid,
                    rs.getString("name"),
                    rs.getString("hash"),
                    rs.getString("salt"),
                    rs.getInt("iterations"),
                    rs.getString("ip"),
                    rs.getLong("registeredAt"),
                    rs.getLong("lastLogin"),
                    rs.getString("twoFASecret"));
        } catch (Exception e) { return null; }
    }

    @Override public UserRecord getByUUID(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        } catch (Exception ignored) {}
        return null;
    }

    @Override public UserRecord getByName(String name) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE LOWER(name)=LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return map(rs); }
        } catch (Exception ignored) {}
        return null;
    }

    @Override public void create(UserRecord r) {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (uuid,name,hash,salt,iterations,ip,registeredAt,lastLogin,twoFASecret) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, r.uuid.toString());
            ps.setString(2, r.name);
            ps.setString(3, r.hash);
            ps.setString(4, r.salt);
            ps.setInt(5, r.iterations);
            ps.setString(6, r.ip);
            ps.setLong(7, r.registeredAt);
            ps.setLong(8, r.lastLogin);
            ps.setString(9, r.twoFASecret);
            ps.executeUpdate();
        } catch (Exception e) { plugin.getLogger().warning("create error: "+e.getMessage()); }
    }

    @Override public void update(UserRecord r) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET name=?, hash=?, salt=?, iterations=?, ip=?, registeredAt=?, lastLogin=?, twoFASecret=? WHERE uuid=?")) {
            ps.setString(1, r.name);
            ps.setString(2, r.hash);
            ps.setString(3, r.salt);
            ps.setInt(4, r.iterations);
            ps.setString(5, r.ip);
            ps.setLong(6, r.registeredAt);
            ps.setLong(7, r.lastLogin);
            ps.setString(8, r.twoFASecret);
            ps.setString(9, r.uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) { plugin.getLogger().warning("update error: "+e.getMessage()); }
    }

    @Override public void delete(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (Exception e) { plugin.getLogger().warning("delete error: "+e.getMessage()); }
    }

    @Override public int countByIP(String ip) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE ip=?")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (Exception e) { plugin.getLogger().warning("countByIP error: "+e.getMessage()); }
        return 0;
    }
}