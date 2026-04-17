package com.c446.disctui_server.db.handlers;

import com.c446.disctui_server.Config;
import com.c446.disctui_server.db.model.MessageEntity;
import com.c446.disctui_server.db.repositories.ChannelRepository;
import com.c446.disctui_server.db.repositories.MessageRepository;
import com.c446.disctui_server.db.repositories.UserRepository;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MessageHistoryIndexer {
    private static final Logger LOG = LoggerFactory.getLogger(MessageHistoryIndexer.class);

    private final JDA jda;
    private final MessageRepository messageRepository = new MessageRepository();
    private final ChannelRepository channelRepository = new ChannelRepository();
    private final UserRepository userRepository = new UserRepository();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "message-history-indexer");
        t.setDaemon(true);
        return t;
    });

    public MessageHistoryIndexer(JDA jda) {
        this.jda = jda;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(
                this::safeCrawl,
                0,
                Config.MESSAGE_BACKFILL_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void safeCrawl() {
        try {
            crawlAllGuilds();
        } catch (Exception e) {
            LOG.error("History crawl failed", e);
        }
    }

    private void crawlAllGuilds() {
        int indexedCount = 0;
        for (Guild guild : jda.getGuilds()) {
            for (GuildMessageChannel channel : guild.getTextChannels()) {
                indexedCount += crawlChannel(channel, Config.MESSAGE_BACKFILL_LIMIT);
            }
            for (GuildMessageChannel channel : guild.getNewsChannels()) {
                indexedCount += crawlChannel(channel, Config.MESSAGE_BACKFILL_LIMIT);
            }
            for (GuildMessageChannel channel : guild.getThreadChannels()) {
                indexedCount += crawlChannel(channel, Config.MESSAGE_BACKFILL_LIMIT);
            }
        }
        LOG.info("History crawl complete. Indexed/updated {} messages.", indexedCount);
    }

    private int crawlChannel(GuildMessageChannel channel, int messageLimit) {
        int indexedCount = 0;
        String beforeMessageId = null;
        int remaining = messageLimit;

        try {
            channelRepository.save(channel);
        } catch (Exception e) {
            LOG.warn("Failed to persist channel {} before crawl", channel.getId(), e);
        }

        while (remaining > 0) {
            int chunkSize = Math.min(100, remaining);
            List<Message> batch = fetchBatch(channel, beforeMessageId, chunkSize);
            if (batch.isEmpty()) {
                break;
            }

            for (Message message : batch) {
                try {
                    userRepository.save(message.getAuthor());
                    MessageEntity entity = MessageHandler.toMessageEntity(message);
                    messageRepository.save(entity);
                    indexedCount++;
                } catch (Exception e) {
                    LOG.warn("Failed to index message {} in channel {}", message.getId(), channel.getId(), e);
                }
            }

            remaining -= batch.size();
            beforeMessageId = batch.get(batch.size() - 1).getId();

            if (batch.size() < chunkSize) {
                break;
            }
        }

        return indexedCount;
    }

    private List<Message> fetchBatch(GuildMessageChannel channel, String beforeMessageId, int chunkSize) {
        try {
            if (beforeMessageId == null) {
                return channel.getHistory().retrievePast(chunkSize).complete();
            }
            return channel.getHistoryBefore(beforeMessageId, chunkSize).complete().getRetrievedHistory();
        } catch (Exception e) {
            LOG.warn("Failed to retrieve history for channel {}", channel.getId(), e);
            return List.of();
        }
    }
}

