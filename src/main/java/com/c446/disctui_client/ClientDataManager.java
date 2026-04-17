package com.c446.disctui_client;

import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.GuildUpdatePacket;
import com.c446.disctui_server.api.GuildUserUpdatePacket;
import com.c446.disctui_server.api.MessageUpdatePacket;
import com.c446.disctui_server.api.UserUpdatePacket;
import com.c446.disctui_client.tui.TuiAttachment;
import com.c446.disctui_client.tui.TuiEmbed;
import com.c446.disctui_client.tui.TuiMessage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientDataManager {
    public enum FrameFocus {
        GUILDS,
        CHANNELS,
        CHAT
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_MESSAGES_PER_CHANNEL = 500;
    private static final Pattern ATTACHMENT_PATTERN = Pattern.compile("\\{\\s*\\\"filename\\\":\\\"([^\\\"]*)\\\",\\s*\\\"url\\\":\\\"([^\\\"]*)\\\",\\s*\\\"proxyUrl\\\":\\\"([^\\\"]*)\\\",\\s*\\\"size\\\":(\\d+)\\s*\\}");

    private final Map<Long, GuildUpdatePacket> guildCache = new ConcurrentHashMap<>();
    private final Map<Long, ChannelUpdatePacket> channelCache = new ConcurrentHashMap<>();
    private final Map<Long, UserUpdatePacket> userCache = new ConcurrentHashMap<>();
    private final Map<String, GuildUserUpdatePacket> guildUserCache = new ConcurrentHashMap<>();
    private final Map<Long, LinkedHashMap<Long, TuiMessage>> messageBuffers = new ConcurrentHashMap<>();

    private volatile Long activeGuildId;
    private volatile Long activeChannelId;
    private volatile String uiStatus = "";
    private volatile boolean collapseMessages;
    private volatile FrameFocus frameFocus = FrameFocus.CHANNELS;
    private volatile int guildScrollOffset;
    private volatile int channelScrollOffset;
    private volatile int chatScrollOffset;

    public void applyGuildUpdate(GuildUpdatePacket guild) {
        if (guild == null || guild.guildId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(guild.deleted())) {
            guildCache.remove(guild.guildId());
        } else {
            guildCache.put(guild.guildId(), guild);
        }
    }

    public void applyChannelUpdate(ChannelUpdatePacket channel) {
        if (channel == null || channel.channelId() == null) {
            return;
        }
        if (Boolean.TRUE.equals(channel.deleted())) {
            channelCache.remove(channel.channelId());
        } else {
            channelCache.put(channel.channelId(), channel);
        }
    }

    public void applyUserUpdate(UserUpdatePacket user) {
        if (user == null || user.userId() == null) {
            return;
        }
        userCache.put(user.userId(), user);
    }

    public void applyGuildUserUpdate(GuildUserUpdatePacket guildUser) {
        if (guildUser == null || guildUser.guildId() == null || guildUser.userId() == null) {
            return;
        }
        String key = guildUserKey(guildUser.guildId(), guildUser.userId());
        if (Boolean.TRUE.equals(guildUser.deleted())) {
            guildUserCache.remove(key);
        } else {
            guildUserCache.put(key, guildUser);
        }
    }

    public void applyMessageUpdate(MessageUpdatePacket packet) {
        if (packet == null || packet.messageId() == null || packet.channelId() == null) {
            return;
        }

        LinkedHashMap<Long, TuiMessage> buffer = messageBuffers.computeIfAbsent(packet.channelId(), ignored -> new LinkedHashMap<>());
        synchronized (buffer) {
            TuiMessage previous = buffer.get(packet.messageId());
            TuiMessage next = buildMessage(packet, previous);
            buffer.put(packet.messageId(), next);
            trimBuffer(buffer);
        }
    }

    public TuiMessage buildMessage(MessageUpdatePacket message) {
        return buildMessage(message, null);
    }

    private TuiMessage buildMessage(MessageUpdatePacket message, TuiMessage previous) {
        Long userId = message.authorId() != null ? message.authorId() : (previous == null ? null : previous.userId());
        String username = userId != null
                ? resolveAuthorDisplayName(message.guildId(), userId)
                : (previous == null ? "unknown-user" : previous.username());
        String avatarUrl = resolveAvatarUrl(userId);
        if ((avatarUrl == null || avatarUrl.isBlank()) && previous != null) {
            avatarUrl = previous.avatarUrl();
        }

        String content = message.content() != null
                ? extractReadableContent(message.content())
                : (previous == null ? "<empty>" : previous.content());
        List<TuiAttachment> attachments = extractAttachments(message.content());
        if (attachments.isEmpty() && previous != null) {
            attachments = previous.attachments();
        }

        long timestamp = previous == null ? System.currentTimeMillis() : previous.timestamp();
        boolean edited = Boolean.TRUE.equals(message.edited()) || (previous != null && previous.edited());
        boolean deleted = Boolean.TRUE.equals(message.deleted()) || (previous != null && previous.deleted());

        return new TuiMessage(
                message.messageId(),
                message.guildId(),
                message.channelId(),
                userId,
                username,
                avatarUrl,
                content,
                List.of(),
                attachments,
                timestamp,
                edited,
                deleted
        );
    }

    public void selectGuild(Long guildId) {
        activeGuildId = guildId;
        guildScrollOffset = 0;
        channelScrollOffset = 0;
        if (guildId == null) {
            return;
        }

        List<ChannelUpdatePacket> channels = getChannelsForGuild(guildId);
        if (!channels.isEmpty()) {
            activeChannelId = channels.getFirst().channelId();
        }
    }

    public void selectChannel(Long channelId) {
        activeChannelId = channelId;
        chatScrollOffset = 0;
        if (channelId != null) {
            ChannelUpdatePacket channel = channelCache.get(channelId);
            if (channel != null) {
                activeGuildId = channel.guildId();
            }
        }
    }

    public void selectDm(Long channelId) {
        activeGuildId = null;
        activeChannelId = channelId;
        channelScrollOffset = 0;
        chatScrollOffset = 0;
    }

    public void clearActiveMessages() {
        if (activeChannelId != null) {
            messageBuffers.put(activeChannelId, new LinkedHashMap<>());
        }
    }

    public List<TuiMessage> getActiveMessages() {
        if (activeChannelId == null) {
            return List.of();
        }
        LinkedHashMap<Long, TuiMessage> buffer = messageBuffers.get(activeChannelId);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer) {
            return new ArrayList<>(buffer.values());
        }
    }

    public List<TuiMessage> getVisibleMessages(int maxRows) {
        List<TuiMessage> all = getActiveMessages();
        if (all.isEmpty() || maxRows <= 0) {
            return List.of();
        }
        int maxOffset = Math.max(0, all.size() - maxRows);
        int offset = Math.min(Math.max(0, chatScrollOffset), maxOffset);
        chatScrollOffset = offset;
        int start = Math.max(0, all.size() - maxRows - offset);
        int end = Math.max(start, all.size() - offset);
        return new ArrayList<>(all.subList(start, end));
    }

    public List<GuildUpdatePacket> getVisibleGuilds(int rows) {
        return viewport(getGuilds(), rows, guildScrollOffset, value -> guildScrollOffset = value);
    }

    public List<ChannelUpdatePacket> getVisibleChannels(int rows) {
        return viewport(getActiveChannels(), rows, channelScrollOffset, value -> channelScrollOffset = value);
    }

    public List<ChannelUpdatePacket> getActiveChannels() {
        if (activeGuildId == null) {
            return getDmChannels();
        }
        return getChannelsForGuild(activeGuildId);
    }

    public List<GuildUpdatePacket> getGuilds() {
        return guildCache.values().stream()
                .filter(g -> !Boolean.TRUE.equals(g.deleted()))
                .sorted(Comparator.comparing(g -> safeLower(g.name())))
                .toList();
    }

    public List<ChannelUpdatePacket> getChannelsForGuild(Long guildId) {
        return channelCache.values().stream()
                .filter(channel -> !Boolean.TRUE.equals(channel.deleted()))
                .filter(channel -> guildId == null ? channel.guildId() == null : guildId.equals(channel.guildId()))
                .sorted(Comparator.comparing(channel -> safeLower(channel.name())))
                .toList();
    }

    public List<ChannelUpdatePacket> getDmChannels() {
        return channelCache.values().stream()
                .filter(channel -> !Boolean.TRUE.equals(channel.deleted()))
                .filter(channel -> channel.guildId() == null)
                .sorted(Comparator.comparing(channel -> safeLower(channel.name())))
                .toList();
    }

    public GuildUpdatePacket findGuild(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Long id = parseLongOrNull(token);
        if (id != null && guildCache.containsKey(id)) {
            return guildCache.get(id);
        }
        String lower = token.toLowerCase();
        return guildCache.values().stream()
                .filter(g -> g.name() != null && g.name().toLowerCase().contains(lower))
                .findFirst()
                .orElse(null);
    }

    public ChannelUpdatePacket findChannel(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Long id = parseLongOrNull(token);
        if (id != null && channelCache.containsKey(id)) {
            return channelCache.get(id);
        }
        String lower = token.toLowerCase();
        return getActiveChannels().stream()
                .filter(channel -> channel.name() != null && channel.name().toLowerCase().contains(lower))
                .findFirst()
                .orElseGet(() -> channelCache.values().stream()
                        .filter(channel -> channel.name() != null && channel.name().toLowerCase().contains(lower))
                        .findFirst().orElse(null));
    }

    public String getActiveGuildLabel() {
        if (activeGuildId == null) {
            return "DMs";
        }
        GuildUpdatePacket guild = guildCache.get(activeGuildId);
        return guild != null && guild.name() != null ? guild.name() : "Guild " + activeGuildId;
    }

    public String getActiveChannelLabel() {
        if (activeChannelId == null) {
            return "No channel";
        }
        ChannelUpdatePacket channel = channelCache.get(activeChannelId);
        return channel != null && channel.name() != null ? "#" + channel.name() : "Channel " + activeChannelId;
    }

    public Long getActiveGuildId() {
        return activeGuildId;
    }

    public Long getActiveChannelId() {
        return activeChannelId;
    }

    public boolean toggleCollapseMessages() {
        collapseMessages = !collapseMessages;
        return collapseMessages;
    }

    public boolean isCollapseMessages() {
        return collapseMessages;
    }

    public FrameFocus getFrameFocus() {
        return frameFocus;
    }

    public void focusNextFrame() {
        frameFocus = switch (frameFocus) {
            case GUILDS -> FrameFocus.CHANNELS;
            case CHANNELS -> FrameFocus.CHAT;
            case CHAT -> FrameFocus.GUILDS;
        };
    }

    public void focusPreviousFrame() {
        frameFocus = switch (frameFocus) {
            case GUILDS -> FrameFocus.CHAT;
            case CHANNELS -> FrameFocus.GUILDS;
            case CHAT -> FrameFocus.CHANNELS;
        };
    }

    public void navigateUp() {
        switch (frameFocus) {
            case GUILDS -> navigateGuild(-1);
            case CHANNELS -> navigateChannel(-1);
            case CHAT -> scrollChat(1);
        }
    }

    public void navigateDown() {
        switch (frameFocus) {
            case GUILDS -> navigateGuild(1);
            case CHANNELS -> navigateChannel(1);
            case CHAT -> scrollChat(-1);
        }
    }

    public void setStatus(String status) {
        uiStatus = status == null ? "" : status;
    }

    public String getStatus() {
        return uiStatus == null ? "" : uiStatus;
    }

    public int getGuildCount() {
        return getGuilds().size();
    }

    public int getVisibleChannelCount() {
        return getActiveChannels().size();
    }

    public String getFocusLabel() {
        return switch (frameFocus) {
            case GUILDS -> "guilds";
            case CHANNELS -> "channels";
            case CHAT -> "chat";
        };
    }

    public String formatMessage(TuiMessage message) {
        String timestamp = LocalTime.ofNanoOfDay((message.timestamp() % 86_400_000L) * 1_000_000L).format(TIME_FORMAT);
        String guildName = resolveGuildName(message.guildId());
        String channelName = resolveChannelName(message.channelId());
        String author = resolveAuthorDisplayName(message.guildId(), message.userId());

        if (message.deleted()) {
            return "[" + timestamp + "] [" + guildName + "/" + channelName + "] <deleted #" + message.messageId() + ">";
        }

        String editedSuffix = message.edited() ? " (edited)" : "";
        return "[" + timestamp + "] [" + guildName + "/" + channelName + "] " + author + ": " + message.content() + editedSuffix;
    }

    private String resolveGuildName(Long guildId) {
        if (guildId == null) {
            return "DM";
        }
        GuildUpdatePacket packet = guildCache.get(guildId);
        if (packet != null && packet.name() != null && !packet.name().isBlank()) {
            return packet.name();
        }
        return "guild:" + guildId;
    }

    private String resolveChannelName(Long channelId) {
        if (channelId == null) {
            return "unknown-channel";
        }
        ChannelUpdatePacket packet = channelCache.get(channelId);
        if (packet != null && packet.name() != null && !packet.name().isBlank()) {
            return "#" + packet.name();
        }
        return "channel:" + channelId;
    }

    private String resolveAuthorDisplayName(Long guildId, Long authorId) {
        if (authorId == null) {
            return "unknown-user";
        }

        if (guildId != null) {
            GuildUserUpdatePacket guildUser = guildUserCache.get(guildUserKey(guildId, authorId));
            if (guildUser != null && guildUser.effectiveName() != null && !guildUser.effectiveName().isBlank()) {
                return guildUser.effectiveName();
            }
        }

        UserUpdatePacket user = userCache.get(authorId);
        if (user != null) {
            if (user.globalName() != null && !user.globalName().isBlank()) {
                return user.globalName();
            }
            if (user.username() != null && !user.username().isBlank()) {
                return user.username();
            }
        }

        return "user:" + authorId;
    }

    private String resolveAvatarUrl(Long authorId) {
        if (authorId == null) {
            return null;
        }
        UserUpdatePacket user = userCache.get(authorId);
        return user == null ? null : user.avatarUrl();
    }

    private String extractReadableContent(String content) {
        if (content == null || content.isBlank()) {
            return "<empty>";
        }

        int marker = content.indexOf("\"raw\":");
        if (marker >= 0) {
            int firstQuote = content.indexOf('"', marker + 6);
            if (firstQuote >= 0) {
                int secondQuote = content.indexOf('"', firstQuote + 1);
                if (secondQuote > firstQuote) {
                    String raw = content.substring(firstQuote + 1, secondQuote);
                    return raw.replace("\\n", "\n").replace("\\t", "\t");
                }
            }
        }

        return content;
    }

    private List<TuiAttachment> extractAttachments(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        Matcher matcher = ATTACHMENT_PATTERN.matcher(content);
        List<TuiAttachment> attachments = new ArrayList<>();
        while (matcher.find()) {
            String fileName = matcher.group(1);
            String url = matcher.group(2);
            String proxyUrl = matcher.group(3);
            long size = 0L;
            try {
                size = Long.parseLong(matcher.group(4));
            } catch (Exception ignored) {
            }
            attachments.add(new TuiAttachment(fileName, url, proxyUrl, size, isImageAttachment(fileName, url)));
        }
        return attachments;
    }

    private boolean isImageAttachment(String fileName, String url) {
        String candidate = fileName != null && !fileName.isBlank() ? fileName : url;
        if (candidate == null) {
            return false;
        }
        String lower = candidate.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    private void trimBuffer(LinkedHashMap<Long, TuiMessage> buffer) {
        while (buffer.size() > MAX_MESSAGES_PER_CHANNEL) {
            Long oldestKey = buffer.keySet().iterator().next();
            buffer.remove(oldestKey);
        }
    }

    private void navigateGuild(int delta) {
        List<GuildUpdatePacket> guilds = getGuilds();
        if (guilds.isEmpty()) {
            return;
        }

        int idx = 0;
        if (activeGuildId != null) {
            for (int i = 0; i < guilds.size(); i++) {
                if (activeGuildId.equals(guilds.get(i).guildId())) {
                    idx = i;
                    break;
                }
            }
        }
        idx = Math.clamp(idx + delta, 0, guilds.size() - 1);
        selectGuild(guilds.get(idx).guildId());
    }

    private void navigateChannel(int delta) {
        List<ChannelUpdatePacket> channels = getActiveChannels();
        if (channels.isEmpty()) {
            return;
        }

        int idx = 0;
        if (activeChannelId != null) {
            for (int i = 0; i < channels.size(); i++) {
                if (activeChannelId.equals(channels.get(i).channelId())) {
                    idx = i;
                    break;
                }
            }
        }
        idx = Math.clamp(idx + delta, 0, channels.size() - 1);
        selectChannel(channels.get(idx).channelId());
    }

    private void scrollChat(int delta) {
        chatScrollOffset = Math.max(0, chatScrollOffset + delta);
    }

    private <T> List<T> viewport(List<T> all, int rows, int offset, java.util.function.IntConsumer offsetWriter) {
        if (rows <= 0 || all.isEmpty()) {
            return List.of();
        }

        int maxOffset = Math.max(0, all.size() - rows);
        int safeOffset = Math.min(Math.max(0, offset), maxOffset);
        offsetWriter.accept(safeOffset);

        int from = safeOffset;
        int to = Math.min(all.size(), safeOffset + rows);
        return new ArrayList<>(all.subList(from, to));
    }

    private Long parseLongOrNull(String token) {
        try {
            return Long.parseLong(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String guildUserKey(Long guildId, Long userId) {
        return guildId + ":" + userId;
    }
}

