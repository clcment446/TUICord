package com.c446.disctui_server.events;

import com.c446.disctui_server.DiscordBot;
import com.c446.disctui_server.Config;
import com.c446.disctui_server.api.ChannelSelectPacket;
import com.c446.disctui_server.api.MessageUpdatePacket;
import com.c446.disctui_server.api.Messenger;
import com.c446.disctui_server.api.ServerBoundMessage;
import com.c446.disctui_server.db.model.MessageEntity;
import com.c446.disctui_server.db.repositories.MessageRepository;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
import java.util.List;

public class SocketPacketDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(SocketPacketDispatcher.class);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "socket-packet-dispatcher");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<Long> selectedChannelId = new AtomicReference<>();
    private final MessageRepository messageRepository = new MessageRepository();

    public void dispatchAsync(Object packet) {
        if (packet == null) {
            return;
        }
        executor.submit(() -> dispatch(packet));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void dispatch(Object packet) {
        try {
            if (packet instanceof ServerBoundMessage message) {
                handleServerBoundMessage(message);
                return;
            }

            if (packet instanceof ChannelSelectPacket channelSelectPacket) {
                handleChannelSelect(channelSelectPacket);
                return;
            }

            LOG.warn("No socket handler registered for packet type {}", packet.getClass().getName());
        } catch (Exception e) {
            LOG.error("Failed to dispatch packet {}", packet.getClass().getSimpleName(), e);
        }
    }

    private void handleChannelSelect(ChannelSelectPacket packet) {
        if (packet.channelId() == null) {
            LOG.warn("Received /channel selection with null channelId");
            return;
        }

        Long channelId = packet.channelId();
        selectedChannelId.set(channelId);
        LOG.info("Client selected channel {}", channelId);

        streamChannelHistory(channelId);
    }

    private void streamChannelHistory(Long channelId) {
        try {
            List<MessageEntity> history = messageRepository.findByChannel(channelId, Config.MESSAGE_BACKFILL_LIMIT);
            Collections.reverse(history); // DB returns latest-first; client needs oldest-first playback.

            Long guildId = resolveGuildId(channelId);
            for (MessageEntity message : history) {
                Messenger.getInstance().send(new MessageUpdatePacket(
                        message.id,
                        message.channelId,
                        guildId,
                        message.authorId,
                        message.content,
                        message.deleted,
                        message.editCount > 0,
                        message.editCount
                ));
            }
            LOG.info("Streamed {} historical messages for channel {}", history.size(), channelId);
        } catch (Exception e) {
            LOG.error("Failed to stream history for channel {}", channelId, e);
        }
    }

    private Long resolveGuildId(Long channelId) {
        if (DiscordBot.jda == null || channelId == null) {
            return null;
        }
        GuildMessageChannel guildChannel = DiscordBot.jda.getChannelById(GuildMessageChannel.class, channelId);
        return guildChannel == null ? null : guildChannel.getGuild().getIdLong();
    }

    private void handleServerBoundMessage(ServerBoundMessage packet) {
        Long channelId = packet.channelId() != null ? packet.channelId() : selectedChannelId.get();
        if (channelId == null) {
            LOG.warn("Dropping outbound message: no selected channel and packet.channelId is null");
            return;
        }

        if (DiscordBot.jda == null) {
            LOG.warn("Dropping outbound message: JDA is not ready");
            return;
        }

        MessageChannel channel = DiscordBot.jda.getChannelById(MessageChannel.class, channelId);
        if (channel == null) {
            LOG.warn("Dropping outbound message: unknown channel {}", channelId);
            return;
        }

        channel.sendMessage(packet.contents()).queue(
                ignored -> LOG.debug("Forwarded socket message to channel {}", channelId),
                throwable -> LOG.error("Failed to send message to channel {}", channelId, throwable)
        );
    }
}

