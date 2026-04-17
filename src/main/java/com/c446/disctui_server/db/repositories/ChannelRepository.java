package com.c446.disctui_server.db.repositories;

import com.c446.disctui_server.db.AbstractRepository;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import com.c446.disctui_server.DiscordBot;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChannelRepository extends AbstractRepository<Channel, Long> {

    @Override
    public void save(Channel channel) throws Exception {
        String sql = """
            INSERT INTO channels (id, guild_id, name, type)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                type = VALUES(type);
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, channel.getIdLong());

            // Handle NULL guild_id for DM channels
            if (channel instanceof GuildChannel gc) {
                ps.setLong(2, gc.getGuild().getIdLong());
            } else {
                ps.setNull(2, Types.BIGINT);
            }

            ps.setString(3, channel.getName());
            ps.setInt(4, channel.getType().getId()); // Discord type ID (TINYINT)

            ps.executeUpdate();
        }
    }

    /**
     * Retrieves the channel from JDA's cache.
     * Searches across all types (Text, Voice, Thread, DM).
     */
    @Override
    public Optional<Channel> findById(Long id) throws Exception {
        // JDA 5.x specific: getChannelById handles all channel subtypes
        return Optional.ofNullable(DiscordBot.jda.getChannelById(Channel.class, id));
    }

    /**
     * Returns all channels currently indexed in JDA's memory.
     */
    @Override
    public List<Channel> findAll() throws Exception {
        List<Channel> allChannels = new ArrayList<>();
        allChannels.addAll(DiscordBot.jda.getForumChannels());
        allChannels.addAll(DiscordBot.jda.getNewsChannels());
        allChannels.addAll(DiscordBot.jda.getThreadChannels());
        allChannels.addAll(DiscordBot.jda.getTextChannels());
        allChannels.addAll(DiscordBot.jda.getPrivateChannels());
        allChannels.addAll(DiscordBot.jda.getMediaChannels());
        allChannels.addAll(DiscordBot.jda.getVoiceChannels());
        return allChannels;
    }

    @Override
    public void deleteById(Long id) throws Exception {
        String sql = "DELETE FROM channels WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}