package com.c446.db.handlers;

import com.c446.db.repositories.ChannelRepository;
import com.c446.db.repositories.GuildRepository;
import com.c446.db.repositories.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.update.GenericGuildUpdateEvent;
import net.dv8tion.jda.api.events.user.GenericUserEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MasterCacheUpdater extends ListenerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(MasterCacheUpdater.class);

    private final ChannelRepository channelRepo;
    private final UserRepository userRepo;
    private final GuildRepository guildRepo;

    // Standard TTL Caches
    private final Cache<Long, Boolean> guildCache = createCache(30);
    private final Cache<Long, Boolean> channelCache = createCache(15);
    private final Cache<Long, Boolean> userCache = createCache(60);

    public MasterCacheUpdater(ChannelRepository channelRepo, UserRepository userRepo, GuildRepository guildRepo) {
        this.channelRepo = channelRepo;
        this.userRepo = userRepo;
        this.guildRepo = guildRepo;
    }

    private Cache<Long, Boolean> createCache(int minutes) {
        return Caffeine.newBuilder()
                .expireAfterWrite(minutes, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }

    // --- GUILD LOGIC ---

    @Override
    public void onGenericGuild(@NotNull GenericGuildEvent event) {
        long id = event.getGuild().getIdLong();
        // Passive check: only save if we haven't seen this guild in 30 mins
        if (guildCache.getIfPresent(id) == null) {
            try {
                if (guildRepo.findArchivedById(id).isEmpty()) {
                    guildRepo.save(event.getGuild());
                }
                guildCache.put(id, true);
            } catch (Exception e) {
                LOG.error("Passive guild sync failed: {}", id, e);
            }
        }
    }

    @Override
    public void onGenericGuildUpdate(@NotNull GenericGuildUpdateEvent<?> event) {
        // EXPLICIT BYPASS: Name/Icon/Owner changed
        try {
            guildRepo.save(event.getGuild());
            guildCache.put(event.getGuild().getIdLong(), true); // Reset TTL
            LOG.debug("Explicit update for guild: {}", event.getGuild().getId());
        } catch (Exception e) {
            LOG.error("Failed to push explicit guild update: {}", event.getGuild().getId(), e);
        }
    }

    // --- CHANNEL LOGIC ---

    @Override
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        // EXPLICIT BYPASS
        try {
            channelRepo.save(event.getChannel());
            channelCache.put(event.getChannel().getIdLong(), true);
        } catch (Exception e) {
            LOG.error("Failed to save new channel: {}", event.getChannel().getId(), e);
        }
    }

    @Override
    public void onGenericChannelUpdate(@NotNull GenericChannelUpdateEvent<?> event) {
        // EXPLICIT BYPASS: Name/Topic/Position changed
        try {
            channelRepo.save(event.getChannel());
            channelCache.put(event.getChannel().getIdLong(), true);
        } catch (Exception e) {
            LOG.error("Failed to push explicit channel update: {}", event.getChannel().getId(), e);
        }
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        // EXPLICIT BYPASS
        try {
            long id = event.getChannel().getIdLong();
            channelRepo.deleteById(id);
            channelCache.invalidate(id); // Hard removal from cache
            LOG.debug("Channel deleted from DB and Cache: {}", id);
        } catch (Exception e) {
            LOG.error("Failed to delete channel: {}", event.getChannel().getId(), e);
        }
    }

    // --- USER LOGIC ---

    @Override
    public void onGenericUser(@NotNull GenericUserEvent event) {
        long id = event.getUser().getIdLong();
        // Users are "high traffic" (many events), so we keep them strictly cached
        // Unless you specifically want to catch every name change,
        // keep this as a cached "Passive" sync.
        if (userCache.getIfPresent(id) == null) {
            try {
                userRepo.save(event.getUser());
                userCache.put(id, true);
            } catch (Exception e) {
                LOG.error("User sync error: {}", id, e);
            }
        }
    }
}