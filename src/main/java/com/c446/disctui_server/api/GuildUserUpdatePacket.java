package com.c446.disctui_server.api;

public record GuildUserUpdatePacket(Long guildId,
                                    Long userId,
                                    String nickname,
                                    String effectiveName,
                                    Boolean deleted) implements IByteBufferTransmutable<GuildUserUpdatePacket> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public GuildUserUpdatePacket fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, GuildUserUpdatePacket.class);
    }
}

