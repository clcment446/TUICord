package com.c446.db.model;

import java.time.Instant;



public class MessageEntity {
    public final long id;
    public final long channelId;
    public final long authorId;
    public String content;
    public boolean deleted;
    public Instant createdAt;

    // optional but VERY useful for UI consistency
    public int editCount;
    public Instant editedAt;

    public MessageEntity(long id, long channelId, long authorId, String content, boolean deleted, Instant createdAt) {
        this.authorId=authorId;
        this.channelId=channelId;
        this.id=id;
        this.content=content;
        this.deleted=deleted;
        this.createdAt=createdAt;

        editCount=0;
        editedAt=null;
    }
}