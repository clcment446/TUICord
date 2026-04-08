package com.c446.db.repositories;

import com.c446.db.AbstractRepository;
import com.c446.db.model.ArchivedChannel;
import net.dv8tion.jda.api.entities.Guild;
import com.c446.DiscordBot; // Assuming this is where your JDA instance lives
import net.dv8tion.jda.api.entities.channel.ChannelType;

import java.sql.*;
import java.util.*;

public class GuildRepository extends AbstractRepository<Guild, Long> {
    public static final HashSet<Long> PRESENT_IN_CACHE = new HashSet<>();
    public static final GuildRepository REPOSITORY = new GuildRepository();

    /**
     * Persists a JDA Guild's minimal identity to the database.
     * Uses UPSERT logic to handle re-connections and updates.
     */
    @Override
    public void save(Guild guild) throws Exception {
        String sql = """
            INSERT INTO guilds (id, name, icon_url)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                icon_url = VALUES(icon_url);
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, guild.getIdLong());
            ps.setString(2, guild.getName());
            ps.setString(3, guild.getIconUrl());
            ps.executeUpdate();
        }
    }

    /**
     * Retrieves the Guild from JDA's memory cache.
     * Note: This does not query MySQL for the object, as JDA is the source of truth.
     */
    @Override
    public Optional<Guild> findById(Long id) throws Exception {
        return Optional.ofNullable(DiscordBot.jda.getGuildById(id));
    }

    /**
     * Returns all guilds currently in the JDA cache.
     */
    @Override
    public List<Guild> findAll() throws Exception {
        return new ArrayList<>(DiscordBot.jda.getGuilds());
    }

    /**
     * Deletes the guild record from the database.
     * Does not affect the live JDA state (which is controlled by Discord).
     */
    @Override
    public void deleteById(Long id) throws Exception {
        String sql = "DELETE FROM guilds WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public Optional<Long> findArchivedById(Long id) throws Exception {
        String sql = "SELECT id FROM guilds WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong("id")) : Optional.empty();
            }
        }
    }

    public List<ArchivedChannel> findArchivedChannelsByGuild(long guildId) throws Exception {
        String sql = "SELECT id, guild_id, name, type FROM channels WHERE guild_id = ?";
        return queryArchived(sql, ps -> ps.setLong(1, guildId));
    }

    /**
     * Finds all Private (DM) channels recorded in the database.
     * This allows the TUI to show DM history for users not currently in the JDA cache.
     */
    public List<ArchivedChannel> findArchivedPrivateChannels() throws Exception {
        // In your schema, DM channels have a NULL guild_id
        String sql = "SELECT id, guild_id, name, type FROM channels WHERE guild_id IS NULL";
        return queryArchived(sql, ps -> {});
    }

    /**
     * Helper to map DB rows to our thin ArchivedChannel DTO.
     */
    private List<ArchivedChannel> queryArchived(String sql, ThrowingConsumer<PreparedStatement> preparer) throws Exception {
        List<ArchivedChannel> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            preparer.accept(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long gid = rs.getLong("guild_id");
                    results.add(new ArchivedChannel(
                            rs.getLong("id"),
                            rs.wasNull() ? null : gid,
                            rs.getString("name"),
                            ChannelType.fromId(rs.getInt("type"))
                    ));
                }
            }
        }
        return results;
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }
}