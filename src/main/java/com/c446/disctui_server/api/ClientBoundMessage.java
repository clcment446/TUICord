package com.c446.disctui_server.api;

public record ClientBoundMessage(String content,
                                 Long authorId,
                                 Long channelId,
                                 Long guildId) implements IByteBufferTransmutable<ClientBoundMessage> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public ClientBoundMessage fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, ClientBoundMessage.class);
    }
}
