package com.soldinregister.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {
    private final Plugin plugin;
    private HikariDataSource ds;

    public Database(Plugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String type = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase();
        HikariConfig cfg = new HikariConfig();
        if (type.equals("mysql")) {
            String host = plugin.getConfig().getString("storage.mysql.host");
            int port = plugin.getConfig().getInt("storage.mysql.port");
            String db = plugin.getConfig().getString("storage.mysql.database");
            String user = plugin.getConfig().getString("storage.mysql.user");
            String pass = plugin.getConfig().getString("storage.mysql.password");
            boolean ssl = plugin.getConfig().getBoolean("storage.mysql.useSSL", false);
            String jdbc = "jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=" + ssl + "&characterEncoding=utf8";
            cfg.setJdbcUrl(jdbc);
            cfg.setUsername(user);
            cfg.setPassword(pass);
        } else {
            String file = plugin.getConfig().getString("storage.sqlite.file", "plugins/SoldinRegister/users.db");
            File f = new File(file);
            f.getParentFile().mkdirs();
            cfg.setJdbcUrl("jdbc:sqlite:" + f.getAbsolutePath());
        }
        cfg.setMaximumPoolSize(5);
        cfg.setPoolName("SoldinRegisterPool");
        ds = new HikariDataSource(cfg);

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(16) NOT NULL," +
                    "password VARCHAR(100) NOT NULL," +
                    "ip VARCHAR(45)," +
                    "totp_secret VARCHAR(64)," +
                    "totp_confirmed BOOLEAN DEFAULT 0," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_name ON users(name)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_users_ip ON users(ip)");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void shutdown() {
        if (ds != null) ds.close();
    }
}
