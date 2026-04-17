package com.c446.disctui_server.api;

public record ChannelUpdatePacket(Long channelId,
                                  Long guildId,
                                  String name,
                                  Integer type,
                                  Boolean deleted) implements IByteBufferTransmutable<ChannelUpdatePacket> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public ChannelUpdatePacket fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, ChannelUpdatePacket.class);
    }
}

