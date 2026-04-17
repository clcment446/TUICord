package com.c446.db.handlers;

import com.c446.db.model.MessageEntity;
import com.c446.db.model.MessageEditEntity;
import com.c446.db.repositories.MessageEditRepository;
import com.c446.db.repositories.MessageRepository;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class MessageHandler extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageHandler.class);

    private final MessageRepository messageRepo;
    private final MessageEditRepository editRepo;

    public MessageHandler(MessageRepository messageRepo,
                          MessageEditRepository editRepo) {
        this.messageRepo = messageRepo;
        this.editRepo = editRepo;
    }

    // =========================================================
    // CREATE MESSAGE
    // =========================================================
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        try {
            var id = event.getMessageIdLong();
            var channelId = event.getChannel().getIdLong();
            var authorId = event.getAuthor().getIdLong();
            var content = event.getMessage().getContentRaw();
            var deleted = false;
            var createdAt = event.getMessage().getTimeCreated().toInstant();

            MessageEntity msg = new MessageEntity(id, channelId, authorId, content,deleted, createdAt);

            msg.editedAt = null;
            msg.editCount=0;

            messageRepo.save(msg);

        } catch (Exception e) {
            LOG.error("Failed to handle message create {}", event.getMessageId(), e);
        }
    }

    // =========================================================
    // EDIT MESSAGE
    // =========================================================
    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        try {
            long id = event.getMessageIdLong();

            MessageEntity existing = messageRepo.findById(id).orElse(null);
            if (existing == null) return;

            String newContent = event.getMessage().getContentRaw();
            String oldContent = existing.content;

            // NO-OP GUARD (Discord sometimes triggers fake updates)
            if (oldContent != null && oldContent.equals(newContent)) {
                return;
            }

            int nextRevision = existing.editCount + 1;
            Instant editTime = event.getMessage().getTimeEdited() != null
                    ? event.getMessage().getTimeEdited().toInstant()
                    : Instant.now();

            // 1. WRITE HISTORY FIRST (append-only guarantee)

            MessageEditEntity edit = new MessageEditEntity(id, nextRevision, oldContent, editTime);

            editRepo.insertEdit(edit);

            // 2. UPDATE CURRENT STATE
            existing.content = newContent;
            existing.editCount = nextRevision;
            existing.editedAt = editTime;

            messageRepo.save(existing);

        } catch (Exception e) {
            LOG.error("Failed to handle message update {}", event.getMessageId(), e);
        }
    }

    // =========================================================
    // DELETE MESSAGE
    // =========================================================
    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        try {
            long id = event.getMessageIdLong();

            MessageEntity existing = messageRepo.findById(id).orElse(null);
            if (existing == null) return;

            existing.deleted = true;

            messageRepo.save(existing);

        } catch (Exception e) {
            LOG.error("Failed to handle message delete {}", event.getMessageId(), e);
        }
    }
}