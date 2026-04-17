package com.c446.disctui_server.db.repositories;

import com.c446.disctui_server.db.AbstractRepository;
import com.c446.disctui_server.db.model.MessageEditEntity;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MessageEditRepository extends AbstractRepository<MessageEditEntity, Long> {

    /**
     * Inserts an edit snapshot (append-only).
     */
    public void insertEdit(MessageEditEntity edit) throws Exception {
        String sql = """
            INSERT INTO message_edits (
                message_id, revision, content, edited_at
            )
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE message_id = message_id
        """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, edit.messageId);
            ps.setInt(2, edit.revision);
            ps.setString(3, edit.content);

            if (edit.editedAt != null) {
                ps.setTimestamp(4, Timestamp.from(edit.editedAt));
            } else {
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
            }

            ps.executeUpdate();
        }
    }

    public List<MessageEditEntity> findByMessageId(long messageId) throws Exception {
        String sql = """
            SELECT message_id, revision, content, edited_at
            FROM message_edits
            WHERE message_id = ?
            ORDER BY revision ASC
        """;

        List<MessageEditEntity> out = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, messageId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                var mID = rs.getLong("message_id");
                var revision = rs.getInt("revision");
                var content = rs.getString("content");
                var editedAt = rs.getTimestamp("edited_at").toInstant();

                MessageEditEntity e = new MessageEditEntity(mID, revision, content, editedAt);

                out.add(e);
            }
        }

        return out;
    }

    @Override
    public Optional<MessageEditEntity> findById(Long id) {
        throw new UnsupportedOperationException("Use findByMessageId()");
    }

    @Override
    public List<MessageEditEntity> findAll() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void save(MessageEditEntity entity) {
        throw new UnsupportedOperationException("Use insertEdit()");
    }

    @Override
    public void deleteById(Long id) {
        throw new UnsupportedOperationException("Append-only table");
    }
}