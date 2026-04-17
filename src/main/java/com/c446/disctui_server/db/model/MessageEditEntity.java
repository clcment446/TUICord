package com.c446.db.model;

import java.time.Instant;

public class MessageEditEntity {
    public final long messageId;
    public final int revision;
    public final String content;
    public final Instant editedAt;

    public MessageEditEntity(long messageId, int revision, String content, Instant editedAt) {
        this.messageId = messageId;
        this.revision = revision;
        this.content = content;
        this.editedAt = editedAt;
    }
}