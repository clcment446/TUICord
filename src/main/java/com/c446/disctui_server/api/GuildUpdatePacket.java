package com.c446.disctui_server.api;

public record GuildUpdatePacket(Long guildId,
                                String name,
                                String iconUrl,
                                Boolean deleted) implements IByteBufferTransmutable<GuildUpdatePacket> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public GuildUpdatePacket fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, GuildUpdatePacket.class);
    }
}

