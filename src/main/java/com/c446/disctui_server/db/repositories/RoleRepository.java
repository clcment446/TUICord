package com.c446.disctui_server.db.repositories;

import com.c446.disctui_server.db.AbstractRepository;
import net.dv8tion.jda.api.entities.Role;
import com.c446.disctui_server.DiscordBot;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoleRepository extends AbstractRepository<Role, Long> {

    @Override
    public void save(Role role) throws Exception {
        String sql = """
            INSERT INTO roles (id, guild_id, name, color, permissions, deleted)
            VALUES (?, ?, ?, ?, ?, FALSE)
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                color = VALUES(color),
                permissions = VALUES(permissions),
                deleted = FALSE;
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, role.getIdLong());
            ps.setLong(2, role.getGuild().getIdLong());
            ps.setString(3, role.getName());
            // Role color is an RGB integer; JDA returns null if no color is set
            ps.setInt(4, role.getColor() != null ? role.getColorRaw() : 0);
            // Permissions are stored as a raw bitfield (long)
            ps.setLong(5, role.getPermissionsRaw());

            ps.executeUpdate();
        }
    }

    /**
     * Source of truth: JDA Cache.
     */
    @Override
    public Optional<Role> findById(Long id) throws Exception {
        return Optional.ofNullable(DiscordBot.jda.getRoleById(id));
    }

    /**
     * Implementation of soft-delete: flags the role as deleted
     * instead of removing the row.
     */
    @Override
    public void deleteById(Long id) throws Exception {
        String sql = "UPDATE roles SET deleted = TRUE WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    @Override
    public List<Role> findAll() throws Exception {
        return new ArrayList<>(DiscordBot.jda.getRoleCache().asList());
    }
}