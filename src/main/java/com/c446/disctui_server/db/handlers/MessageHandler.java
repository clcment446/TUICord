package com.c446.disctui_server.db.handlers;

import com.c446.disctui_server.api.ClientBoundMessage;
import com.c446.disctui_server.api.MessageUpdatePacket;
import com.c446.disctui_server.api.Messenger;
import com.c446.disctui_server.db.model.MessageEntity;
import com.c446.disctui_server.db.model.MessageEditEntity;
import com.c446.disctui_server.db.repositories.ChannelRepository;
import com.c446.disctui_server.db.repositories.MessageEditRepository;
import com.c446.disctui_server.db.repositories.MessageRepository;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.GenericMessageEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;

public class MessageHandler extends ListenerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageHandler.class);

    private final MessageRepository messageRepo  = new MessageRepository();
    private final MessageEditRepository editRepo = new MessageEditRepository();
    private final ChannelRepository channelRepository = new ChannelRepository();

    HashSet<Long> knownChannels = new HashSet<>();

    public MessageHandler() {
         // Constructor can be used for dependency injection if needed
    }

    @Override
    public void onGenericMessage(GenericMessageEvent event) {
        if (!knownChannels.contains(event.getChannel().getIdLong())) {
            try {
                channelRepository.save(event.getChannel());
                knownChannels.add(event.getChannel().getIdLong());
            } catch (Exception e) {
                LOG.error("Failed to save channel {} for message event {}", event.getChannel().getIdLong(), event.getMessageId(), e);
            }
        }
    }

    // =========================================================
    // CREATE MESSAGE
    // =========================================================
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        MessageEntity msg = toMessageEntity(event.getMessage());

        if (!knownChannels.contains(event.getChannel().getIdLong())) {
            try {
                channelRepository.save(event.getChannel());
                knownChannels.add(event.getChannel().getIdLong());
            } catch (Exception e) {
                LOG.error("Failed to save channel {} for message event {}", event.getChannel().getIdLong(), event.getMessageId(), e);
            }
        }
        
        try {
            msg.editedAt = null;
            msg.editCount=0;

            messageRepo.save(msg);

        } catch (Exception e) {
            LOG.error("[ON_MESSAGE_RECEIVED] Failed to handle message create {}", event.getMessageId(), e);
        }

        LOG.info("Received message {} in channel {} from user {} in guild {}: {}",
                event.getMessageId(), event.getChannel().getId(), event.getAuthor().getId(), event.getGuild().getId(), msg.content);

        String clientReadableContent = event.getMessage().getContentDisplay();

        Messenger.getInstance().send(new ClientBoundMessage(
                clientReadableContent,
                event.getAuthor().getIdLong(),
                event.getChannel().getIdLong(),
                event.getGuild().getIdLong()
        ));

        Messenger.getInstance().send(new MessageUpdatePacket(
                event.getMessageIdLong(),
                event.getChannel().getIdLong(),
                event.getGuild().getIdLong(),
                event.getAuthor().getIdLong(),
                clientReadableContent,
                false,
                false,
                0
        ));
    }

    public static @NonNull MessageEntity toMessageEntity(@NonNull Message message) {
        var id = message.getIdLong();
        var channelId = message.getChannel().getIdLong();
        var authorId = message.getAuthor().getIdLong();

        var embedStrings = message.getEmbeds().stream()
                .map(embed -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("{");
                    if (embed.getTitle() != null) sb.append("\"title\":\"").append(embed.getTitle()).append("\",");
                    if (embed.getDescription() != null) sb.append("\"description\":\"").append(embed.getDescription()).append("\",");
                    if (embed.getUrl() != null) sb.append("\"url\":\"").append(embed.getUrl()).append("\",");
                    if (embed.getImage() != null) sb.append("\"image\":\"").append(embed.getImage().getUrl()).append("\",");
                    if (embed.getThumbnail() != null) sb.append("\"thumbnail\":\"").append(embed.getThumbnail().getUrl()).append("\",");
                    if (embed.getAuthor() != null) sb.append("\"author\":\"").append(embed.getAuthor().getName()).append("\",");
                    if (embed.getFooter() != null) sb.append("\"footer\":\"").append(embed.getFooter().getText()).append("\",");
                    sb.append("}");
                    return sb.toString();
                })
                .toList();

        var embedAttachments = message.getAttachments().stream()
                .map(att -> "{" +
                        "\"filename\":\"" + att.getFileName() + "\"," +
                        "\"url\":\"" + att.getUrl() + "\"," +
                        "\"proxyUrl\":\"" + att.getProxyUrl() + "\"," +
                        "\"size\":" + att.getSize() +
                        "}")
                .toList();

        var content = "\n{\n\"raw\":\n\t\"" + message.getContentRaw() + "\",\n\"embeds\":\n\t" + embedStrings + ",\n\"attachments\":\n\t" + embedAttachments+ "}";
        var deleted = false;
        var createdAt = message.getTimeCreated().toInstant();

        return new MessageEntity(id, channelId, authorId, content,deleted, createdAt);
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

            Messenger.getInstance().send(new MessageUpdatePacket(
                    id,
                    event.getChannel().getIdLong(),
                    event.getGuild().getIdLong(),
                    event.getAuthor().getIdLong(),
                    newContent,
                    false,
                    true,
                    nextRevision
            ));

        } catch (Exception e) {
            LOG.error("[ON_MESSAGE_UPDATE] Failed to handle message update {}", event.getMessageId(), e);
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

            Messenger.getInstance().send(new MessageUpdatePacket(
                    id,
                    event.getChannel().getIdLong(),
                    event.isFromGuild() ? event.getGuild().getIdLong() : null,
                    null,
                    null,
                    true,
                    false,
                    existing.editCount
            ));

        } catch (Exception e) {
            LOG.error("Failed to handle message delete {}", event.getMessageId(), e);
        }
    }
}