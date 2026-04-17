package com.c446.disctui_server.api;

public record MessageUpdatePacket(Long messageId,
                                  Long channelId,
                                  Long guildId,
                                  Long authorId,
                                  String content,
                                  Boolean deleted,
                                  Boolean edited,
                                  Integer editCount) implements IByteBufferTransmutable<MessageUpdatePacket> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public MessageUpdatePacket fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, MessageUpdatePacket.class);
    }
}

