package com.c446.disctui_client.tui;

import com.c446.disctui_client.ClientDataManager;
import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.GuildUpdatePacket;

import java.util.List;

public class TuiCommandRouter {
    public record CommandResult(boolean handled,
                                boolean rerender,
                                Long channelToSendTo,
                                String outgoingMessage,
                                String status) {
    }

    public CommandResult route(String input, ClientDataManager state) {
        if (input == null || !input.startsWith("/")) {
            return new CommandResult(false, false, null, null, null);
        }

        String[] parts = input.trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        return switch (command) {
            case "/help" -> new CommandResult(true, false, null, null, help());
            case "/guilds" -> new CommandResult(true, false, null, null, formatGuilds(state));
            case "/channels" -> new CommandResult(true, false, null, null, formatChannels(state));
            case "/keybinds" -> new CommandResult(true, false, null, null, keybinds());
            case "/dms" -> {
                List<ChannelUpdatePacket> dms = state.getDmChannels();
                if (!dms.isEmpty()) {
                    Long dmChannelId = dms.getFirst().channelId();
                    state.selectDm(dmChannelId);
                    yield new CommandResult(true, true, dmChannelId, null, "Switched to DM view.");
                } else {
                    state.selectDm(null);
                    yield new CommandResult(true, true, null, null, "Switched to DM view.");
                }
            }
            case "/clear" -> {
                state.clearActiveMessages();
                yield new CommandResult(true, true, null, null, "Cleared current chat buffer.");
            }
            case "/collapse" -> {
                boolean collapsed = state.toggleCollapseMessages();
                yield new CommandResult(true, true, null, null,
                        collapsed ? "Collapse mode enabled." : "Collapse mode disabled.");
            }
            case "/goto" -> gotoTarget(arg, state);
            default -> new CommandResult(false, false, null, null, null);
        };
    }

    private CommandResult gotoTarget(String arg, ClientDataManager state) {
        if (arg.isBlank()) {
            return new CommandResult(true, false, null, null, "Usage: /goto <guild|channel|guild/channel|dms>");
        }

        if (arg.equalsIgnoreCase("dm") || arg.equalsIgnoreCase("dms")) {
            List<ChannelUpdatePacket> dms = state.getDmChannels();
            if (!dms.isEmpty()) {
                state.selectDm(dms.getFirst().channelId());
                return new CommandResult(true, true, null, null, "Switched to DM channel.");
            }
            return new CommandResult(true, false, null, null, "No DM channels available.");
        }

        if (arg.contains("/")) {
            String[] parts = arg.split("/", 2);
            GuildUpdatePacket guild = state.findGuild(parts[0]);
            ChannelUpdatePacket channel = state.findChannel(parts[1]);
            if (guild != null) {
                state.selectGuild(guild.guildId());
            }
            if (channel != null) {
                state.selectChannel(channel.channelId());
                return new CommandResult(true, true, channel.channelId(), null, "Switched to " + state.getActiveChannelLabel());
            }
            return new CommandResult(true, false, null, null, "Could not resolve guild/channel pair.");
        }

        GuildUpdatePacket guild = state.findGuild(arg);
        if (guild != null) {
            state.selectGuild(guild.guildId());
            return new CommandResult(true, true, state.getActiveChannelId(), null, "Switched to guild " + guild.name());
        }

        ChannelUpdatePacket channel = state.findChannel(arg);
        if (channel != null) {
            state.selectChannel(channel.channelId());
            return new CommandResult(true, true, channel.channelId(), null, "Switched to channel " + channel.name());
        }

        return new CommandResult(true, false, null, null, "Could not find guild/channel matching: " + arg);
    }

    private String help() {
        return String.join("\n",
                "Commands:",
                "  /goto <guild|channel|guild/channel|dms>",
                "  /guilds",
                "  /channels",
                "  /dms",
                "  /clear",
                "  /collapse",
                "  /keybinds",
                "  /help",
                "Any other text is sent as /send by default.");
    }

    private String keybinds() {
        return String.join("\n",
                "Keybinds:",
                "  Up/Down arrows    navigate in focused pane",
                "  Left/Right arrows switch focus between guilds/channels/chat",
                "  In Result mode: Up/Down scroll full command output",
                "  Ctrl+E / Alt+E    insert emoji",
                "Commands mirror navigation: /goto, /channel, /dms, /collapse");
    }

    private String formatGuilds(ClientDataManager state) {
        StringBuilder sb = new StringBuilder("Guilds:\n");
        for (GuildUpdatePacket guild : state.getGuilds()) {
            sb.append(" - ").append(guild.guildId()).append(' ')
                    .append(guild.name() == null ? "<unnamed>" : guild.name())
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private String formatChannels(ClientDataManager state) {
        StringBuilder sb = new StringBuilder("Channels:\n");
        for (ChannelUpdatePacket channel : state.getActiveChannels()) {
            sb.append(" - ").append(channel.channelId()).append(' ')
                    .append(channel.name() == null ? "<unnamed>" : "#" + channel.name())
                    .append('\n');
        }
        return sb.toString().trim();
    }
}
