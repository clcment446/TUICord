package com.c446.disctui_server.api;

public record UserUpdatePacket(Long userId,
                               String username,
                               String globalName,
                               String avatarUrl,
                               Boolean bot) implements IByteBufferTransmutable<UserUpdatePacket> {
    @Override
    public byte[] toByteArray() {
        return IByteBufferTransmutable.serializeRecord(this);
    }

    @Override
    public UserUpdatePacket fromByteArray(byte[] bytes) {
        return IByteBufferTransmutable.deserializeRecord(bytes, UserUpdatePacket.class);
    }
}

