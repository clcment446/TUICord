package com.c446.disctui_client.tui;

import com.c446.disctui_client.ClientDataManager;
import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.GuildUpdatePacket;
import com.c446.disctui_client.ClientDataManager.FrameFocus;
import com.c446.disctui_client.tui.layout.FrameNode;
import com.c446.disctui_client.tui.layout.FrameRect;
import com.c446.disctui_client.tui.layout.LeafFrame;
import com.c446.disctui_client.tui.layout.SplitFrame;
import com.c446.disctui_client.tui.layout.SplitOrientation;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AnsiTuiRenderer {
    private static final String RESET = "\u001b[0m";
    private static final String CLEAR = "\u001b[2J\u001b[H";
    private static final String HIDE_CURSOR = "\u001b[?25l";
    private static final String SHOW_CURSOR = "\u001b[?25h";

    private final KittyGraphicsRenderer kitty = new KittyGraphicsRenderer();

    public void render(ClientDataManager state, String inputDraft) {
        int[] detected = normalizeTerminalSize(0, 0);
        render(state, inputDraft, detected[0], detected[1]);
    }

    public void render(ClientDataManager state, String inputDraft, int width, int height) {
        int[] normalized = normalizeTerminalSize(width, height);
        width = normalized[0];
        height = normalized[1];

        int commandFrameRows = 3;
        int bodyRows = Math.max(6, height - commandFrameRows - 1);
        FrameNode contentFrame = getFrameNode(state, width);

        FrameNode commandFrame = new LeafFrame(rect -> buildCommandFrame(state, resetIfNeeded(inputDraft), rect.width(), rect.height()));
        FrameNode rootFrame = new SplitFrame(
                SplitOrientation.VERTICAL,
                contentFrame,
                commandFrame,
                1,
                '─',
                commandFrameRows,
                0.75d
        );

        rootFrame.layout(new FrameRect(0, 0, width, bodyRows + 1 + commandFrameRows));
        List<String> lines = new ArrayList<>();
        lines.add(HIDE_CURSOR + CLEAR);
        lines.addAll(rootFrame.render());

        System.out.print(String.join("\n", fitHeight(lines, height)));
        System.out.flush();
    }

    private @NonNull FrameNode getFrameNode(ClientDataManager state, int width) {
        int sidebarWidth = Math.clamp(width / 4, 24, 40);
        double sidebarRatio = Math.clamp((double) sidebarWidth / Math.max(1, width - 1), 0.2d, 0.45d);

        FrameNode sidebarFrame = getFrameNode(state);
        FrameNode chatFrame = new LeafFrame(rect -> buildChat(state, rect.width(), rect.height()));
        FrameNode contentFrame = new SplitFrame(
                SplitOrientation.HORIZONTAL,
                sidebarFrame,
                chatFrame,
                1,
                '│',
                null,
                sidebarRatio
        );
        return contentFrame;
    }

    private @NonNull FrameNode getFrameNode(ClientDataManager state) {
        int guildCount = Math.max(1, state.getGuildCount());
        int channelCount = Math.max(1, state.getVisibleChannelCount());
        double guildRatio = Math.clamp((double) guildCount / (guildCount + channelCount + 2.0d), 0.15d, 0.55d);

        FrameNode guildFrame = new LeafFrame(rect -> buildGuildFrame(state, rect.width(), rect.height()));
        FrameNode channelFrame = new LeafFrame(rect -> buildChannelFrame(state, rect.width(), rect.height()));
        FrameNode sidebarFrame = new SplitFrame(
                SplitOrientation.VERTICAL,
                guildFrame,
                channelFrame,
                1,
                '─',
                null,
                guildRatio
        );
        return sidebarFrame;
    }

    public void shutdown() {
        System.out.print(CLEAR + SHOW_CURSOR + RESET);
    }

    private List<String> buildGuildFrame(ClientDataManager state, int width, int rows) {
        List<String> out = new ArrayList<>();
        out.add(fitLine(color(140, 110, 255) + bold("TUICord") + RESET + " "
                + dim("guilds") + ": " + state.getActiveGuildLabel() + RESET, width));
        String focusTag = state.getFrameFocus() == FrameFocus.GUILDS ? color(90, 255, 180) + " [focus]" + RESET : "";
        out.add(fitLine(color(160, 160, 160) + bold("Guilds") + RESET + focusTag, width));
        for (GuildUpdatePacket guild : state.getVisibleGuilds(Math.max(0, rows - 2))) {
            boolean selected = guild.guildId() != null && guild.guildId().equals(state.getActiveGuildId());
            out.add(fitLine(prefix(selected) + (guild.name() == null ? String.valueOf(guild.guildId()) : guild.name()), width));
        }
        return fitRows(out, rows, width);
    }

    private List<String> buildChannelFrame(ClientDataManager state, int width, int rows) {
        List<String> out = new ArrayList<>();
        String focusTag = state.getFrameFocus() == FrameFocus.CHANNELS ? color(90, 255, 180) + " [focus]" + RESET : "";
        out.add(fitLine(color(160, 160, 160) + bold("Channels") + RESET + focusTag, width));
        for (ChannelUpdatePacket channel : state.getVisibleChannels(Math.max(0, rows - 1))) {
            boolean selected = channel.channelId() != null && channel.channelId().equals(state.getActiveChannelId());
            String name = channel.name() == null ? String.valueOf(channel.channelId()) : "#" + channel.name();
            out.add(fitLine(prefix(selected) + name, width));
        }
        return fitRows(out, rows, width);
    }

    private List<String> buildChat(ClientDataManager state, int width, int rows) {
        List<String> out = new ArrayList<>();
        String chatFocus = state.getFrameFocus() == FrameFocus.CHAT ? color(90, 255, 180) + " [focus]" + RESET : "";
        out.add(fitLine(color(160, 160, 160) + bold("Chat") + RESET + chatFocus, width));

        List<TuiMessage> messages = state.getVisibleMessages(Math.max(0, rows - 1));
        if (messages.isEmpty()) {
            out.add(fitLine(dim("No messages in this channel yet."), width));
            return fitRows(out, rows, width);
        }

        Long lastAuthor = null;
        for (TuiMessage message : messages) {
            boolean grouped = lastAuthor != null && lastAuthor.equals(message.userId()) && !message.deleted();
            if (!grouped) {
                String header = headerForMessage(state, message);
                List<String> headerLines = wrap(header, width);
                if (message.deleted()) {
                    headerLines = headerLines.stream().map(this::deletedStyle).collect(Collectors.toCollection(ArrayList::new));
                }
                out.addAll(headerLines);
            }

            if (message.avatarUrl() != null && !message.avatarUrl().isBlank() && kitty.supportsKitty()) {
                String avatar = kitty.renderInlineUrl(message.avatarUrl(), 32, 32);
                if (!avatar.isBlank()) {
                    out.add(avatar + " " + dim("avatar"));
                }
            }

            if (message.deleted()) {
                out.add(fitLine(deletedStyle("<deleted>"), width));
            } else {
                if (state.isCollapseMessages()) {
                    String collapsed = message.content().replace('\n', ' ');
                    out.add(fitLine(collapsed + dim("  [collapsed]") + RESET, width));
                } else {
                    out.addAll(wrap(message.content(), width));
                }
            }

            if (!message.deleted() && !message.embeds().isEmpty()) {
                for (TuiEmbed embed : message.embeds()) {
                    out.addAll(renderEmbed(embed, width));
                }
            }

            if (!message.deleted() && !message.attachments().isEmpty()) {
                for (TuiAttachment attachment : message.attachments()) {
                    if (attachment.image() && kitty.supportsKitty()) {
                        String source = attachment.proxyUrl() != null && !attachment.proxyUrl().isBlank() ? attachment.proxyUrl() : attachment.url();
                        String inline = kitty.renderInlineUrl(source, 96, 96);
                        if (!inline.isBlank()) {
                            out.add(inline + " " + dim("[image] " + attachment.fileName()));
                            continue;
                        }
                    }

                    String marker = attachment.image() ? "[image] " : "[file] ";
                    out.addAll(wrap(dim(marker + attachment.fileName() + " → " + attachment.url()), width));
                }
            }

            out.add("");
            lastAuthor = message.userId();
        }

        return fitRows(out, rows, width);
    }

    private List<String> buildCommandFrame(ClientDataManager state, String inputDraft, int width, int rows) {
        List<String> out = new ArrayList<>();
        String status = state.getStatus();
        out.add(fitLine(dim(status == null || status.isBlank() ? "Ready." : status), width));
        out.add(fitLineNoPad(color(200, 200, 200) + "> " + inputDraft + RESET, width));
        out.add(fitLine(dim("Arrows navigate focus/scroll  Ctrl+E/Alt+E emoji  /keybinds /help /goto /guilds /channels /dms /clear /collapse"), width));
        return fitRows(out, rows, width);
    }

    private List<String> renderEmbed(TuiEmbed embed, int width) {
        List<String> out = new ArrayList<>();
        out.add(fitLine(color(120, 180, 255) + "┌" + repeat('─', Math.max(0, width - 2)) + "┐" + RESET, width));
        if (embed.title() != null && !embed.title().isBlank()) {
            out.add(fitLine(color(120, 180, 255) + "│ " + bold(embed.title()) + RESET, width));
        }
        if (embed.description() != null && !embed.description().isBlank()) {
            out.addAll(wrap(embed.description(), width));
        }
        if (embed.footer() != null && !embed.footer().isBlank()) {
            out.add(fitLine(dim(embed.footer()), width));
        }
        if (embed.imageUrl() != null && !embed.imageUrl().isBlank()) {
            if (kitty.supportsKitty()) {
                String inline = kitty.renderInlineUrl(embed.imageUrl(), 64, 64);
                if (!inline.isBlank()) {
                    out.add(inline);
                }
            } else {
                out.add(dim("[image] " + embed.imageUrl()));
            }
        }
        out.add(fitLine(color(120, 180, 255) + "└" + repeat('─', Math.max(0, width - 2)) + "┘" + RESET, width));
        return out;
    }

    private String headerForMessage(ClientDataManager state, TuiMessage message) {
        String time = java.time.Instant.ofEpochMilli(message.timestamp())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
                .withNano(0)
                .toString();
        String guild = message.guildId() == null ? "DM" : state.getActiveGuildLabel();
        String channel = message.channelId() == null ? "unknown" : state.getActiveChannelLabel();
        String avatar = message.avatarUrl() != null && !message.avatarUrl().isBlank() ? "◉ " : "";
        String edited = message.edited() ? dim(" (edited)") : "";
        if (message.deleted()) {
            return "[" + time + "] [" + guild + "/" + channel + "] " + avatar + message.username() + edited + ": ";
        }
        return color(160, 160, 160) + "[" + time + "] " + RESET
                + color(120, 180, 255) + "[" + guild + "/" + channel + "] " + RESET
                + avatar + color(255, 160, 220) + bold(message.username()) + RESET + edited + ": ";
    }

    private String deletedStyle(String text) {
        return color(255, 90, 90) + text + RESET;
    }

    private List<String> fitRows(List<String> lines, int rows, int width) {
        if (lines.size() > rows) {
            return new ArrayList<>(lines.subList(Math.max(0, lines.size() - rows), lines.size()));
        }
        while (lines.size() < rows) {
            lines.add(fitLine("", width));
        }
        return lines;
    }

    private List<String> wrap(String text, int width) {
        return DisplayWidth.wrap(text, width).stream()
                .map(line -> fitLine(line, width))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String prefix(boolean selected) {
        return selected ? color(90, 255, 180) + "▸ " + RESET : "  ";
    }

    private String repeat(char ch, int count) {
        if (count <= 0) {
            return "";
        }
        return String.valueOf(ch).repeat(count);
    }

    private String color(int r, int g, int b) {
        return "\u001b[38;2;" + r + ";" + g + ";" + b + "m";
    }

    private String dim(String text) {
        return "\u001b[2m" + text + RESET;
    }

    private String bold(String text) {
        return "\u001b[1m" + text + RESET;
    }

    private String resetIfNeeded(String value) {
        return value == null ? "" : value;
    }

    private String fitLine(String text, int width) {
        return DisplayWidth.fit(text, width);
    }

    private String fitLineNoPad(String text, int width) {
        String safe = text == null ? "" : text;
        if (DisplayWidth.width(safe) <= width) {
            return safe;
        }
        return DisplayWidth.fit(safe, width).stripTrailing();
    }

    private List<String> fitHeight(List<String> lines, int targetHeight) {
        if (lines.size() > targetHeight) {
            return new ArrayList<>(lines.subList(0, targetHeight));
        }
        List<String> out = new ArrayList<>(lines);
        while (out.size() < targetHeight) {
            out.add("");
        }
        return out;
    }

    private String stripAnsi(String text) {
        return DisplayWidth.stripAnsi(text);
    }

    public static int readIntEnv(String key, int defaultValue) {
        try {
            String value = System.getenv(key);
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int[] normalizeTerminalSize(int width, int height) {
        int normalizedWidth = width > 0 ? width : readIntEnv("COLUMNS", 120);
        int normalizedHeight = height > 0 ? height : readIntEnv("LINES", 40);

        // Heuristic normalization: many non-tty wrappers expose 50x11 defaults.
        if (normalizedWidth <= 60 && normalizedHeight <= 15) {
            int sttyCols = readIntEnv("COLUMNS", 0);
            int sttyRows = readIntEnv("LINES", 0);
            if (sttyCols > normalizedWidth) {
                normalizedWidth = sttyCols;
            }
            if (sttyRows > normalizedHeight) {
                normalizedHeight = sttyRows;
            }
        }

        normalizedWidth = Math.max(50, normalizedWidth);
        normalizedHeight = Math.max(12, normalizedHeight);
        return new int[]{normalizedWidth, normalizedHeight};
    }
}

