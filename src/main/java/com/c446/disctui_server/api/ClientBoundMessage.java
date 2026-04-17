package com.c446.disctui_server.api;

public record SerializableMessage(String content,
                                  Long authorId,
                                  Long channelId,
                                  Long guildId) implements IByteBufferTransmutable<SerializableMessage> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public SerializableMessage fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, SerializableMessage.class);
    }
}
