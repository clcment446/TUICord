package com.c446.disctui_server;

import com.c446.disctui_server.api.Messenger;
import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.DiscoverPacket;
import com.c446.disctui_server.api.GuildUpdatePacket;
import com.c446.disctui_server.api.GuildUserUpdatePacket;
import com.c446.disctui_server.api.IByteBufferTransmutable;
import com.c446.disctui_server.api.UserUpdatePacket;
import com.c446.disctui_server.db.DBMan;
import com.c446.disctui_server.db.handlers.MessageHandler;
import com.c446.disctui_server.db.handlers.MessageHistoryIndexer;
import com.c446.disctui_server.db.repositories.*;
import com.c446.disctui_server.events.SocketPacketDispatcher;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Application entry point.
 */
public class DiscordBot {

    private static final Logger LOG = LoggerFactory.getLogger(DiscordBot.class);
    private static final int MASTER_SOCKET_PORT = 6769;

    public static JDA jda = null;
    private static ServerSocket masterSocketServer;
    private static Thread masterSocketAcceptThread;
    private static Thread masterSocketReadThread;
    private static final SocketPacketDispatcher SOCKET_PACKET_DISPATCHER = new SocketPacketDispatcher();
    private static MessageHistoryIndexer messageHistoryIndexer;

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

            // ── 3.5 Start socket bridge for TUI client ─────────────
            startMasterSocketAcceptor(MASTER_SOCKET_PORT);

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

            jda.addEventListener(
                    new com.c446.disctui_server.db.handlers.MasterCacheUpdater(new ChannelRepository(), new UserRepository(), new GuildRepository()),
                    new MessageHandler()
            );

            messageHistoryIndexer = new MessageHistoryIndexer(jda);
            messageHistoryIndexer.start();

            LOG.info("Discord bot is online. Slash commands registered.");
            LOG.info("Running as{} (ID: {})", jda.getSelfUser().getAsTag(), jda.getSelfUser().getId());
            // ── Graceful Shutdown Hook ──────────────────────────
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutdown signal received. Cleaning up...");

                // 1. Stop JDA first to stop receiving new events
                if (jda != null) {
                    jda.shutdown();
                }

                // 2. Stop socket bridge
                stopMasterSocketAcceptor();
                Messenger.getInstance().disconnect();
                SOCKET_PACKET_DISPATCHER.shutdown();
                if (messageHistoryIndexer != null) {
                    messageHistoryIndexer.stop();
                }

                // 3. Stop API server
                // apiServer.stop();

                // 4. Close DB Pool last
                DBMan.shutdown();

                LOG.info("Shutdown complete. Goodbye!");
            }));

        } catch (Exception e) {
            LOG.error("FATAL: Failed to start application", e);

            // Emergency Cleanup
            stopMasterSocketAcceptor();
            Messenger.getInstance().disconnect();
            SOCKET_PACKET_DISPATCHER.shutdown();
            if (messageHistoryIndexer != null) {
                messageHistoryIndexer.stop();
            }
            // if (apiServer != null) apiServer.stop();
            DBMan.shutdown();

            System.exit(1);
        }
    }

    private static void startMasterSocketAcceptor(int port) throws IOException {
        masterSocketServer = new ServerSocket(port);
        masterSocketAcceptThread = new Thread(() -> {
            LOG.info("Master socket bridge listening on port {}", port);
            while (!masterSocketServer.isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    Socket socket = masterSocketServer.accept();
                    Messenger.getInstance().registerSocket(socket);
                    LOG.info("Master client connected from {}", socket.getRemoteSocketAddress());
                    startSocketReadLoop(socket);
                    emitDiscoverySnapshot();
                    emitCurrentStateSnapshot();
                } catch (IOException e) {
                    if (masterSocketServer.isClosed()) {
                        LOG.info("Master socket acceptor stopped.");
                        break;
                    }
                    LOG.error("Failed while accepting/registering master client socket", e);
                }
            }
        }, "master-socket-acceptor");

        masterSocketAcceptThread.setDaemon(true);
        masterSocketAcceptThread.start();
    }

    private static void startSocketReadLoop(Socket socket) {
        if (masterSocketReadThread != null) {
            masterSocketReadThread.interrupt();
        }

        masterSocketReadThread = new Thread(() -> {
            try (DataInputStream input = new DataInputStream(socket.getInputStream())) {
                while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                    IByteBufferTransmutable.WireEnvelope envelope = IByteBufferTransmutable.readWirePacket(input);
                    Object packet = IByteBufferTransmutable.decodeByType(envelope.type(), envelope.payload());
                    SOCKET_PACKET_DISPATCHER.dispatchAsync(packet);
                }
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    LOG.info("Master client read loop ended: {}", e.getMessage());
                }
            } catch (Exception e) {
                LOG.error("Unhandled error in master client read loop", e);
            }
        }, "master-socket-reader");

        masterSocketReadThread.setDaemon(true);
        masterSocketReadThread.start();
    }

    private static void emitDiscoverySnapshot() {
        int guildCount = jda == null ? 0 : jda.getGuilds().size();
        int channelCount = jda == null ? 0 : jda.getGuilds().stream().mapToInt(guild -> guild.getChannels().size()).sum();
        int userCount = jda == null ? 0 : jda.getUsers().size();

        Messenger.getInstance().send(new DiscoverPacket(
                "TUICord",
                "v1",
                guildCount,
                channelCount,
                userCount
        ));
    }

    private static void emitCurrentStateSnapshot() {
        if (jda == null) {
            return;
        }

        for (var guild : jda.getGuilds()) {
            Messenger.getInstance().send(new GuildUpdatePacket(
                    guild.getIdLong(),
                    guild.getName(),
                    guild.getIconUrl(),
                    false
            ));
        }

        for (var guild : jda.getGuilds()) {
            for (var channel : guild.getChannels()) {
                Long guildId = channel instanceof GuildChannel guildChannel ? guildChannel.getGuild().getIdLong() : null;
                Messenger.getInstance().send(new ChannelUpdatePacket(
                        channel.getIdLong(),
                        guildId,
                        channel.getName(),
                        channel.getType().getId()
                        , false
                ));
            }
        }

        for (var user : jda.getUserCache()) {
            Messenger.getInstance().send(new UserUpdatePacket(
                    user.getIdLong(),
                    user.getName(),
                    user.getGlobalName(),
                    user.getEffectiveAvatarUrl(),
                    user.isBot()
            ));
        }

        for (var guild : jda.getGuilds()) {
            for (var member : guild.getMembers()) {
                Messenger.getInstance().send(new GuildUserUpdatePacket(
                        guild.getIdLong(),
                        member.getUser().getIdLong(),
                        member.getNickname(),
                        member.getEffectiveName(),
                        false
                ));
            }
        }
    }

    private static void stopMasterSocketAcceptor() {
        if (masterSocketReadThread != null) {
            masterSocketReadThread.interrupt();
            masterSocketReadThread = null;
        }

        if (masterSocketAcceptThread != null) {
            masterSocketAcceptThread.interrupt();
            masterSocketAcceptThread = null;
        }

        if (masterSocketServer != null) {
            try {
                masterSocketServer.close();
            } catch (IOException e) {
                LOG.warn("Failed to close master socket server", e);
            }
            masterSocketServer = null;
        }
    }
}