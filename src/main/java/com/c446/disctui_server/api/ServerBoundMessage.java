package com.c446.disctui_server.api;

public record ServerBoundMessage(String contents, Long channelId) implements IByteBufferTransmutable<ServerBoundMessage> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public ServerBoundMessage fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, ServerBoundMessage.class);
    }
}
