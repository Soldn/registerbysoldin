
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
        String type = plugin.getConfig().getString("database.type", "sqlite");
        HikariConfig cfg = new HikariConfig();
        if ("mysql".equalsIgnoreCase(type)) {
            String host = plugin.getConfig().getString("database.mysql.host");
            int port = plugin.getConfig().getInt("database.mysql.port", 3306);
            String db = plugin.getConfig().getString("database.mysql.database");
            String user = plugin.getConfig().getString("database.mysql.user");
            String pass = plugin.getConfig().getString("database.mysql.password");
            String params = plugin.getConfig().getString("database.mysql.params", "");
            cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + params);
            cfg.setUsername(user);
            cfg.setPassword(pass);
            cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        } else {
            File file = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.sqlite-file","users.db"));
            file.getParentFile().mkdirs();
            cfg.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }
        cfg.setMaximumPoolSize(10);
        ds = new HikariDataSource(cfg);

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "name TEXT PRIMARY KEY," +
                    "password TEXT NOT NULL," +
                    "ip TEXT," +
                    "twofa_secret TEXT," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ip ON users(ip)");
        } catch (SQLException e) {
            plugin.getLogger().severe("DB init error: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public void shutdown() {
        if (ds != null) ds.close();
    }
}
