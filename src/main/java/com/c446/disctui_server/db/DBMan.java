package com.c446.disctui_server.db;

import com.c446.disctui_server.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DBMan {
    private static HikariDataSource dataSource;

    /**
     * @return A connection from the pool.
     * Remember to use try-with-resources to return it to the pool!
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initPool();
        }
        assert dataSource != null;
        return dataSource.getConnection();
    }

    private static void initPool() {
        try {
            HikariConfig config = new HikariConfig();
            String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s",
                    Config.DB_HOST, Config.DB_PORT, Config.DB_NAME);

            config.setJdbcUrl(jdbcUrl);
            config.setUsername(Config.DB_USER);
            config.setPassword(Config.DB_PASS);

            // Important: Don't let a slow Docker start kill the app
            config.setInitializationFailTimeout(1000);
            config.setConnectionTimeout(2500);

            // Critical for MySQL 8 compatibility
            config.addDataSourceProperty("allowPublicKeyRetrieval", "true");
            config.addDataSourceProperty("useSSL", "false");
            config.setMaximumPoolSize(10);

            dataSource = new HikariDataSource(config);
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize Database Pool!", e);
        }
    }

    public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}