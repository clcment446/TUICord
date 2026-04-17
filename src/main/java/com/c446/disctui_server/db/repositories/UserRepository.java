package com.c446.disctui_server.db.repositories;

import com.c446.disctui_server.db.AbstractRepository;
import net.dv8tion.jda.api.entities.User;
import com.c446.disctui_server.DiscordBot;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserRepository extends AbstractRepository<User, Long> {

    @Override
    public void save(User user) throws Exception {
        String sql = """
            INSERT INTO users (id, username, global_name, discriminator, avatar_url, bot)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                global_name = VALUES(global_name),
                discriminator = VALUES(discriminator),
                avatar_url = VALUES(avatar_url);
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, user.getIdLong());
            ps.setString(2, user.getName());
            ps.setString(3, user.getGlobalName());

            // Handle legacy discriminator ("0000" for new Pomelo usernames)
            String disc = user.getDiscriminator();
            ps.setString(4, "0000".equals(disc) ? null : disc);

            ps.setString(5, user.getEffectiveAvatarUrl());
            ps.setBoolean(6, user.isBot());

            ps.executeUpdate();
        }
    }

    /**
     * Source of truth: JDA Cache.
     */
    @Override
    public Optional<User> findById(Long id) throws Exception {
        return Optional.ofNullable(DiscordBot.jda.getUserById(id));
    }

    /**
     * Returns all users currently known to the JDA instance.
     */
    @Override
    public List<User> findAll() throws Exception {
        return new ArrayList<>(DiscordBot.jda.getUserCache().asList());
    }

    @Override
    public void deleteById(Long id) throws Exception {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}