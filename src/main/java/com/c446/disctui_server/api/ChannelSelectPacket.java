package com.c446.disctui_server.api;

public record ChannelSelectPacket(Long channelId)
        implements IByteBufferTransmutable<ChannelSelectPacket> {

    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public ChannelSelectPacket fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, ChannelSelectPacket.class);
    }
}

