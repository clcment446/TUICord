package com.c446.disctui_client;

import com.c446.disctui_client.tui.AnsiTuiRenderer;
import com.c446.disctui_client.tui.TuiCommandRouter;
import com.c446.disctui_client.tui.TuiCommandRouter.CommandResult;
import com.c446.disctui_server.api.ClientBoundMessage;
import com.c446.disctui_server.api.DiscoverPacket;
import com.c446.disctui_server.api.GuildUpdatePacket;
import com.c446.disctui_server.api.GuildUserUpdatePacket;
import com.c446.disctui_server.api.MessageUpdatePacket;
import com.c446.disctui_server.api.UserUpdatePacket;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jspecify.annotations.NonNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicReference;

public class Master {
    private static final int DEFAULT_PORT = 6769;
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static volatile boolean running = true;

    private static final TuiCommandRouter COMMAND_ROUTER = new TuiCommandRouter();

    public static void main(String @NonNull [] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        initServer(host, port);
    }

    public static void initServer(String host, int port) {
        ClientDataManager dataManager = new ClientDataManager();
        AnsiTuiRenderer renderer = new AnsiTuiRenderer();
        ConsoleTerminalRenderer inputRenderer = new ConsoleTerminalRenderer();
        AtomicReference<String> currentDraft = new AtomicReference<>("");

        System.out.println("Connecting to " + host + ":" + port + "...");

        try (SocketEventClient socketClient = new SocketEventClient(host, port)) {
            System.out.println("Connected! Waiting for messages...");
            System.out.println("Commands: /goto <guild/channel>, /guilds, /channels, /dms, /clear, /collapse, /keybinds, /help, /channel <id>, /send <text>, /quit");
            System.out.println("Default command is /send, so plain text sends a message.");

            LineReader lineReader = inputRenderer.buildLineReader(dataManager);
            Terminal terminal = lineReader.getTerminal();
            Terminal.SignalHandler previousWinchHandler = terminal.handle(Terminal.Signal.WINCH,
                    ignored -> {
                        int[] size = resolveTerminalSize(terminal);
                        renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
                    });

            socketClient.startReadLoop(packet -> {
                handleIncomingPacket(packet, dataManager);
                int[] size = resolveTerminalSize(terminal);
                renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
            }, info -> {
                dataManager.setStatus(info);
                int[] size = resolveTerminalSize(terminal);
                renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
            });

            int[] initialSize = resolveTerminalSize(terminal);
            renderer.render(dataManager, currentDraft.get(), initialSize[0], initialSize[1]);

            while (running && socketClient.isConnected()) {
                String line;
                try {
                    line = lineReader.readLine("tuicord> ");
                } catch (UserInterruptException e) {
                    running = false;
                    break;
                }

                if (line == null) {
                    break;
                }

                currentDraft.set(line);
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.equals("/quit")) {
                    running = false;
                    break;
                }

                if (line.startsWith("/nav ")) {
                    handleNavigation(line.substring("/nav ".length()).trim(), dataManager, socketClient);
                    currentDraft.set("");
                    int[] size = resolveTerminalSize(terminal);
                    renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
                    continue;
                }

                if (line.startsWith("/goto ") || line.equals("/guilds") || line.equals("/channels") || line.equals("/dms") || line.equals("/clear") || line.equals("/help") || line.equals("/collapse") || line.equals("/keybinds")) {
                    CommandResult result = COMMAND_ROUTER.route(line, dataManager);
                    if (result.status() != null && !result.status().isBlank()) {
                        dataManager.setStatus(result.status());
                    }
                    if (result.channelToSendTo() != null) {
                        try {
                            socketClient.sendChannelSelect(result.channelToSendTo());
                        } catch (Exception e) {
                            dataManager.setStatus("Failed to select channel: " + e.getMessage());
                        }
                    }
                    currentDraft.set("");
                    int[] size = resolveTerminalSize(terminal);
                    renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
                    continue;
                }

                if (line.startsWith("/channel ")) {
                    String channelIdToken = line.substring("/channel ".length()).trim();
                    handleChannelSelection(channelIdToken, socketClient, dataManager);
                    currentDraft.set("");
                    int[] size = resolveTerminalSize(terminal);
                    renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
                    continue;
                }

                if (line.startsWith("/send ")) {
                    String message = line.substring("/send ".length()).trim();
                    sendMessage(socketClient, dataManager, message);
                    currentDraft.set("");
                    int[] size = resolveTerminalSize(terminal);
                    renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
                    continue;
                }

                if (line.startsWith("/")) {
                    dataManager.setStatus("Unknown command. Supported: /goto, /guilds, /channels, /dms, /clear, /collapse, /keybinds, /help, /channel, /send, /quit");
                    currentDraft.set("");
                    int[] size = resolveTerminalSize(terminal);
                    renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
                    continue;
                }

                // Default command behavior: plain text is equivalent to /send <text>.
                sendMessage(socketClient, dataManager, line);
                currentDraft.set("");
                int[] size = resolveTerminalSize(terminal);
                renderer.render(dataManager, currentDraft.get(), size[0], size[1]);
            }
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            renderer.shutdown();
        }
    }

    private static void handleNavigation(String navArg,
                                         ClientDataManager dataManager,
                                         SocketEventClient socketClient) {
        Long beforeChannel = dataManager.getActiveChannelId();
        switch (navArg.toLowerCase()) {
            case "up" -> dataManager.navigateUp();
            case "down" -> dataManager.navigateDown();
            case "left" -> dataManager.focusPreviousFrame();
            case "right" -> dataManager.focusNextFrame();
            default -> dataManager.setStatus("Unknown navigation: " + navArg);
        }

        Long afterChannel = dataManager.getActiveChannelId();
        if (afterChannel != null && !afterChannel.equals(beforeChannel)) {
            try {
                socketClient.sendChannelSelect(afterChannel);
            } catch (Exception e) {
                dataManager.setStatus("Failed to select channel: " + e.getMessage());
            }
        }
    }

    private static void handleChannelSelection(String channelIdToken,
                                               SocketEventClient socketClient,
                                               ClientDataManager dataManager) {
        try {
            long channelId = Long.parseLong(channelIdToken);
            dataManager.selectChannel(channelId);
            socketClient.sendChannelSelect(channelId);
            dataManager.setStatus("Selected channel " + channelId);
        } catch (NumberFormatException e) {
            dataManager.setStatus("Invalid channel id: " + channelIdToken);
        } catch (Exception e) {
            dataManager.setStatus("Failed to send /channel selection: " + e.getMessage());
        }
    }

    private static void sendMessage(SocketEventClient socketClient,
                                    ClientDataManager dataManager,
                                    String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        Long channelId = dataManager.getActiveChannelId();
        if (channelId == null || channelId <= 0) {
            dataManager.setStatus("No selected channel. Use /goto or /channel first.");
            return;
        }

        try {
            socketClient.sendMessage(content, channelId);
            dataManager.setStatus("Sent to " + dataManager.getActiveChannelLabel());
        } catch (Exception e) {
            dataManager.setStatus("Failed to send message: " + e.getMessage());
        }
    }

    private static void handleIncomingPacket(Object packet, ClientDataManager dataManager) {
        if (packet instanceof ClientBoundMessage message) {
            dataManager.applyMessageUpdate(new MessageUpdatePacket(
                    System.nanoTime(),
                    message.channelId(),
                    message.guildId(),
                    message.authorId(),
                    message.content(),
                    false,
                    false,
                    0
            ));
            return;
        }

        if (packet instanceof DiscoverPacket discover) {
            dataManager.setStatus("Connected to " + discover.serverName() + " (guilds=" + discover.guildCount() + ", channels=" + discover.channelCount() + ")");
            return;
        }

        if (packet instanceof GuildUpdatePacket guild) {
            dataManager.applyGuildUpdate(guild);
            return;
        }

        if (packet instanceof com.c446.disctui_server.api.ChannelUpdatePacket channel) {
            dataManager.applyChannelUpdate(channel);
            return;
        }

        if (packet instanceof UserUpdatePacket user) {
            dataManager.applyUserUpdate(user);
            return;
        }

        if (packet instanceof GuildUserUpdatePacket guildUser) {
            dataManager.applyGuildUserUpdate(guildUser);
            return;
        }

        if (packet instanceof MessageUpdatePacket message) {
            dataManager.applyMessageUpdate(message);
        }
    }

    private static int[] resolveTerminalSize(Terminal terminal) {
        int width = terminal.getWidth();
        int height = terminal.getHeight();
        if (width > 60 && height > 15) {
            return new int[]{width, height};
        }

        int[] stty = readSttySize();
        if (stty[0] > 0 && stty[1] > 0) {
            width = Math.max(width, stty[0]);
            height = Math.max(height, stty[1]);
        }

        if (width <= 0) {
            width = AnsiTuiRenderer.readIntEnv("COLUMNS", 120);
        }
        if (height <= 0) {
            height = AnsiTuiRenderer.readIntEnv("LINES", 40);
        }

        width = Math.max(50, width);
        height = Math.max(12, height);
        return new int[]{width, height};
    }

    private static int[] readSttySize() {
        try {
            Process process = new ProcessBuilder("sh", "-c", "stty size </dev/tty").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    return new int[]{0, 0};
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length != 2) {
                    return new int[]{0, 0};
                }
                int rows = Integer.parseInt(parts[0]);
                int cols = Integer.parseInt(parts[1]);
                return new int[]{cols, rows};
            }
        } catch (Exception ignored) {
            return new int[]{0, 0};
        }
    }
}