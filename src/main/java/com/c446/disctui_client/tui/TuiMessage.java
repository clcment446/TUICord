package com.c446.disctui_client.tui;

import java.util.List;

public record TuiMessage(Long messageId,
                         Long guildId,
                         Long channelId,
                         Long userId,
                         String username,
                         String avatarUrl,
                         String content,
                         List<TuiEmbed> embeds,
                         List<TuiAttachment> attachments,
                         long timestamp,
                         boolean edited,
                         boolean deleted) {
}

