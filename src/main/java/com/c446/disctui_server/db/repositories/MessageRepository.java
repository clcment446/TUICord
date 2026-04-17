package com.c446.disctui_server.db.repositories;

import com.c446.disctui_server.db.AbstractRepository;
import com.c446.disctui_server.db.model.MessageEntity;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MessageRepository extends AbstractRepository<MessageEntity, Long> {

    @Override
    public void save(MessageEntity msg) throws Exception {
        String sql = """
            INSERT INTO messages (
                id, channel_id, author_id,
                content, deleted, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                content = VALUES(content),
                deleted = VALUES(deleted)
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, msg.id);
            ps.setLong(2, msg.channelId);
            ps.setLong(3, msg.authorId);

            if (msg.content != null) {
                ps.setString(4, msg.content);
            } else {
                ps.setNull(4, Types.VARCHAR);
            }

            ps.setBoolean(5, msg.deleted);

            if (msg.createdAt != null) {
                ps.setTimestamp(6, Timestamp.from(msg.createdAt));
            } else {
                ps.setTimestamp(6, Timestamp.from(Instant.now()));
            }

            ps.executeUpdate();
        }
    }

    @Override
    public Optional<MessageEntity> findById(Long id) throws Exception {
        String sql = """
            SELECT id, channel_id, author_id,
                   content, deleted, created_at
            FROM messages
            WHERE id = ?
            LIMIT 1
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) return Optional.empty();

            long mID = rs.getLong("id");
            long channelId = rs.getLong("channel_id");
            long authorId = rs.getLong("author_id");
            String content = rs.getString("content");
            boolean deleted = rs.getBoolean("deleted");
            Timestamp ts = rs.getTimestamp("created_at");

            var msg = new MessageEntity(mID, channelId, authorId, content, deleted, ts.toInstant());


            return Optional.of(msg);
        }
    }

    public List<MessageEntity> findByChannel(Long channelId, int limit) throws Exception {
        String sql = """
            SELECT id, channel_id, author_id,
                   content, deleted, created_at
            FROM messages
            WHERE channel_id = ?
            ORDER BY id DESC
            LIMIT ?
        """;

        List<MessageEntity> out = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, channelId);
            ps.setInt(2, limit);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                var mID = rs.getLong("id");
                var cID = rs.getLong("channel_id");
                var aID = rs.getLong("author_id");
                var content = rs.getString("content");
                var deleted = rs.getBoolean("deleted");
                Timestamp ts = rs.getTimestamp("created_at");
                var createdAt = ts != null ? ts.toInstant() : null;

                var msg = new MessageEntity(mID, cID, aID, content, deleted, createdAt);

                out.add(msg);
            }
        }

        return out;
    }

    @Override
    public List<MessageEntity> findAll() throws Exception {
        String sql = """
            SELECT id, channel_id, author_id,
                   content, deleted, created_at
            FROM messages
        """;

        List<MessageEntity> out = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            while (rs.next()) {
                var id = rs.getLong("id");
                var channelId = rs.getLong("channel_id");
                var authorId = rs.getLong("author_id");
                var content = rs.getString("content");
                var deleted = rs.getBoolean("deleted");
                var  ts = rs.getTimestamp("created_at");
                var createdAt = ts != null ? ts.toInstant() : null;

                MessageEntity msg = new MessageEntity(id, channelId, authorId, content, deleted, createdAt);

                out.add(msg);
            }
        }

        return out;
    }

    @Override
    public void deleteById(Long id) throws Exception {
        String sql = """
            UPDATE messages
            SET deleted = TRUE
            WHERE id = ?
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}