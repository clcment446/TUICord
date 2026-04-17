package com.c446.disctui_server.db.handlers;

import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.GuildUserUpdatePacket;
import com.c446.disctui_server.api.GuildUpdatePacket;
import com.c446.disctui_server.api.Messenger;
import com.c446.disctui_server.api.UserUpdatePacket;
import com.c446.disctui_server.db.repositories.ChannelRepository;
import com.c446.disctui_server.db.repositories.GuildRepository;
import com.c446.disctui_server.db.repositories.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.GenericChannelUpdateEvent;
import net.dv8tion.jda.api.events.guild.GenericGuildEvent;
import net.dv8tion.jda.api.events.guild.update.GenericGuildUpdateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.user.GenericUserEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.Member;

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
                Messenger.getInstance().send(new GuildUpdatePacket(
                        event.getGuild().getIdLong(),
                        event.getGuild().getName(),
                        event.getGuild().getIconUrl(),
                        false
                ));
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
            Messenger.getInstance().send(new GuildUpdatePacket(
                    event.getGuild().getIdLong(),
                    event.getGuild().getName(),
                    event.getGuild().getIconUrl(),
                    false
            ));
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
            Long guildId = event.getChannel() instanceof GuildChannel guildChannel
                    ? guildChannel.getGuild().getIdLong()
                    : null;
            Messenger.getInstance().send(new ChannelUpdatePacket(
                    event.getChannel().getIdLong(),
                    guildId,
                    event.getChannel().getName(),
                    event.getChannel().getType().getId(),
                    false
            ));
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
            Long guildId = event.getChannel() instanceof GuildChannel guildChannel
                    ? guildChannel.getGuild().getIdLong()
                    : null;
            Messenger.getInstance().send(new ChannelUpdatePacket(
                    event.getChannel().getIdLong(),
                    guildId,
                    event.getChannel().getName(),
                    event.getChannel().getType().getId(),
                    false
            ));
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
            Long guildId = event.getChannel() instanceof GuildChannel guildChannel
                    ? guildChannel.getGuild().getIdLong()
                    : null;
            Messenger.getInstance().send(new ChannelUpdatePacket(
                    id,
                    guildId,
                    event.getChannel().getName(),
                    event.getChannel().getType().getId(),
                    true
            ));
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
                Messenger.getInstance().send(new UserUpdatePacket(
                        event.getUser().getIdLong(),
                        event.getUser().getName(),
                        event.getUser().getGlobalName(),
                        event.getUser().getEffectiveAvatarUrl(),
                        event.getUser().isBot()
                ));
            } catch (Exception e) {
                LOG.error("User sync error: {}", id, e);
            }
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        try {
            userRepo.save(event.getUser());
            userCache.put(event.getUser().getIdLong(), true);
            emitUserUpdate(event.getMember());
            emitGuildUserUpdate(event.getMember(), false);
        } catch (Exception e) {
            LOG.error("Failed to handle guild member join for user {} in guild {}",
                    event.getUser().getId(), event.getGuild().getId(), e);
        }
    }

    @Override
    public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
        try {
            userRepo.save(event.getUser());
            userCache.put(event.getUser().getIdLong(), true);
            emitUserUpdate(event.getMember());
            emitGuildUserUpdate(event.getMember(), false);
        } catch (Exception e) {
            LOG.error("Failed to handle guild member nickname update for user {} in guild {}",
                    event.getUser().getId(), event.getGuild().getId(), e);
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        try {
            Messenger.getInstance().send(new GuildUserUpdatePacket(
                    event.getGuild().getIdLong(),
                    event.getUser().getIdLong(),
                    null,
                    event.getUser().getName(),
                    true
            ));
        } catch (Exception e) {
            LOG.error("Failed to handle guild member remove for user {} in guild {}",
                    event.getUser().getId(), event.getGuild().getId(), e);
        }
    }

    private void emitUserUpdate(Member member) {
        Messenger.getInstance().send(new UserUpdatePacket(
                member.getUser().getIdLong(),
                member.getUser().getName(),
                member.getUser().getGlobalName(),
                member.getUser().getEffectiveAvatarUrl(),
                member.getUser().isBot()
        ));
    }

    private void emitGuildUserUpdate(Member member, boolean deleted) {
        Messenger.getInstance().send(new GuildUserUpdatePacket(
                member.getGuild().getIdLong(),
                member.getUser().getIdLong(),
                member.getNickname(),
                member.getEffectiveName(),
                deleted
        ));
    }
}