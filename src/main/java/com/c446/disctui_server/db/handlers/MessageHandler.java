package com.c446.db.handlers;

import com.c446.db.model.MessageEntity;
import com.c446.db.model.MessageEditEntity;
import com.c446.db.repositories.MessageEditRepository;
import com.c446.db.repositories.MessageRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
            MessageEntity msg = toMessageEntity(event.getMessage());
            msg.deleted = false;

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

            String newContent = toMessageEntity(event.getMessage()).content;
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
            editRepo.insertEdit(edit, event.getMessage());

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

    public static MessageEntity toMessageEntity(@NotNull Message message) {
        JsonObject content = new JsonObject();
        content.addProperty("raw", message.getContentRaw());

        JsonArray embeds = new JsonArray();
        for (MessageEmbed embed : message.getEmbeds()) {
            JsonObject embedJson = new JsonObject();
            embedJson.addProperty("title", embed.getTitle());
            embedJson.addProperty("description", embed.getDescription());
            embedJson.addProperty("url", embed.getUrl());

            if (embed.getAuthor() != null) {
                JsonObject author = new JsonObject();
                author.addProperty("name", embed.getAuthor().getName());
                author.addProperty("url", embed.getAuthor().getUrl());
                author.addProperty("iconUrl", embed.getAuthor().getIconUrl());
                embedJson.add("author", author);
            }

            JsonArray fields = new JsonArray();
            for (MessageEmbed.Field field : embed.getFields()) {
                JsonObject fieldJson = new JsonObject();
                fieldJson.addProperty("name", field.getName());
                fieldJson.addProperty("value", field.getValue());
                fieldJson.addProperty("inline", field.isInline());
                fields.add(fieldJson);
            }
            embedJson.add("fields", fields);

            embeds.add(embedJson);
        }
        content.add("embeds", embeds);

        JsonArray attachments = new JsonArray();
        for (Message.Attachment attachment : message.getAttachments()) {
            JsonObject attachmentJson = new JsonObject();
            attachmentJson.addProperty("id", attachment.getIdLong());
            attachmentJson.addProperty("filename", attachment.getFileName());
            attachmentJson.addProperty("url", attachment.getUrl());
            attachmentJson.addProperty("proxyUrl", attachment.getProxyUrl());
            attachmentJson.addProperty("contentType", attachment.getContentType());
            attachmentJson.addProperty("image", attachment.isImage());
            attachments.add(attachmentJson);
        }
        content.add("attachments", attachments);

        MessageEntity entity = new MessageEntity(
                message.getIdLong(),
                message.getChannel().getIdLong(),
                message.getAuthor().getIdLong(),
                content.toString(),
                false,
                message.getTimeCreated().toInstant()
        );
        if (message.getTimeEdited() != null) {
            entity.editedAt = message.getTimeEdited().toInstant();
        }
        return entity;
    }
}
