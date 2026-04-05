package com.c446;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 *
 * <p>Startup order (intentional):</p>
 * <ol>
 *   <li>Load config / .env  – fail fast on missing secrets.</li>
 *   <li>Warm the DB pool    – verifies connectivity before Discord login.</li>
 *   <li>Start the REST API  – Javalin is up before we need it.</li>
 *   <li>Login to Discord    – last, because it requires everything else.</li>
 * </ol>
 */
public class DiscordBot {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);

    public static void main(String[] args) {


        // ── 4. JDA / Discord ───────────────────────────────────
        try {
            JDA jda = JDABuilder.createDefault(config.discordToken())
                    // ── activity ────────────────────────────────
                    .setActivity(Activity.playing("/ping | /user"))

                    // ── event listeners ─────────────────────────
                    .addEventListeners(
                            new CommandHandler(),
                            new BadersoBot()
                    )
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EXPRESSIONS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.MESSAGE_CONTENT)
                    // ── build & await ready ─────────────────────
                    .build()
                    .awaitReady();

            // ── upsert slash commands with Discord ──────────────
            jda.updateCommands()
                    .addCommands(CommandHandler.getSlashCommands())
                    .queue();

            LOG.info("Discord bot is online. Slash commands registered.");

            // ── graceful shutdown hook ──────────────────────────
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutting down…");
                jda.shutdown();
                apiServer.stop();
                DatabasePool.getInstance().close();
                LOG.info("Shutdown complete.");
            }));

        } catch (Exception e) {
            LOG.error("Failed to start Discord bot", e);
            apiServer.stop();
            DatabasePool.getInstance().close();
            System.exit(1);
        }
    }
}
