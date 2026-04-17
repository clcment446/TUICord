package com.c446.disctui_server.api;

public record DiscoverPacket(String serverName,
                             String protocolVersion,
                             Integer guildCount,
                             Integer channelCount,
                             Integer userCount) implements IByteBufferTransmutable<DiscoverPacket> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public DiscoverPacket fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, DiscoverPacket.class);
    }
}

