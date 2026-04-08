package com.c446.db;

import com.c446.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.entities.Guild;

import java.sql.Connection;
import java.sql.SQLException;

public class DBMan {
    private static final HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();

        // Construct JDBC URL (Assuming MariaDB/MySQL based on your Config ports)
        String jdbcUrl = String.format("jdbc:mariadb://%s:%s/%s",
                Config.DB_HOST,
                Config.DB_PORT,
                Config.DB_NAME);

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(Config.DB_USER);
        config.setPassword(Config.DB_PASS);

        // --- Hikari Optimization Settings ---
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        // Pool Settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(600000); // 10 minutes
        config.setConnectionTimeout(30000); // 30 seconds

        // Initialize the source
        dataSource = new HikariDataSource(config);
    }

    /**
     * @return A connection from the pool.
     * Remember to use try-with-resources to return it to the pool!
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}