package com.c446;

import com.c446.db.DBMan;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 */
public class DiscordBot {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);

    public static JDA jda = null;

    public static void main(String[] args) {

        try {
            // ── 1. Load Config ─────────────────────────────────────
            // Config class static initializer runs as soon as we touch it.
            LOG.info("Loading configuration for port {}...", Config.API_PORT);

            // ── 2. Warm the DB Pool ────────────────────────────────
            LOG.info("Initializing Database Pool...");
            // This triggers the Hikari static block and runs table creation

            // ── 3. Start the REST API ──────────────────────────────
            LOG.info("Starting Javalin API Server...");
            // apiServer.start(Config.API_PORT); // Assuming your apiServer instance is here

            // ── 4. JDA / Discord Login ─────────────────────────────
            LOG.info("Logging into Discord...");
            DiscordBot.jda = JDABuilder.createDefault(Config.DISCORD_TOKEN)
                    .setActivity(Activity.playing("/ping | /user"))
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_EXPRESSIONS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .build()
                    .awaitReady();

            // Register Commands
            jda.updateCommands()
//                    .addCommands(CommandHandler.getSlashCommands())
                    .queue();

            LOG.info("Discord bot is online. Slash commands registered.");

            // ── Graceful Shutdown Hook ──────────────────────────
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutdown signal received. Cleaning up...");

                // 1. Stop JDA first to stop receiving new events
                jda.shutdown();

                // 2. Stop API server
                // apiServer.stop();

                // 3. Close DB Pool last
                DBMan.shutdown();

                LOG.info("Shutdown complete. Goodbye!");
            }));

        } catch (Exception e) {
            LOG.error("FATAL: Failed to start application", e);

            // Emergency Cleanup
            // if (apiServer != null) apiServer.stop();
            DBMan.shutdown();

            System.exit(1);
        }
    }
}