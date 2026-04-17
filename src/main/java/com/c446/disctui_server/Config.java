package com.c446.disctui_server;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;

public class Config {

    private static final Dotenv dotenv;

    static {
        try {
            dotenv = Dotenv.configure()
                    .directory("./") // Looks in the root folder
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
        } catch (DotenvException e) {
            throw new RuntimeException("Failed to load .env file. Ensure it exists in the root directory.", e);
        }
    }

    // ─── Discord Settings ────────────────────────────────────────
    public static final String DISCORD_TOKEN = getRequired("DISCORD_TOKEN");

    // ─── Database Settings ───────────────────────────────────────
    public static final String DB_HOST = get("DB_HOST", "127.0.0.1");
    public static final String DB_PORT = get("DB_PORT", "3306");
    public static final String DB_NAME = getRequired("DB_NAME");
    public static final String DB_USER = getRequired("DB_USER");
    public static final String DB_PASS = getRequired("DB_PASSWORD");

    // ─── API Settings ────────────────────────────────────────────
    public static final int API_PORT = Integer.parseInt(get("API_PORT", "46092"));

    // ─── History Crawl Settings ──────────────────────────────────
    public static final int MESSAGE_BACKFILL_LIMIT = Integer.parseInt(get("MESSAGE_BACKFILL_LIMIT", "500"));
    public static final int MESSAGE_BACKFILL_INTERVAL_SECONDS = Integer.parseInt(get("MESSAGE_BACKFILL_INTERVAL_SECONDS", "300"));

    /**
     * Gets a variable or returns a default value.
     */
    private static String get(String key, String defaultValue) {
        String value = dotenv.get(key);
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * Gets a variable or throws an error if missing.
     * Use this for critical items like Tokens.
     */
    private static String getRequired(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("FATAL: Missing required environment variable: " + key);
        }
        return value;
    }
}
