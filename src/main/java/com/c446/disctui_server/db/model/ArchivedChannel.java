package com.c446.disctui_server.db.model;

import net.dv8tion.jda.api.entities.channel.ChannelType;

public record ArchivedChannel(
        long id,
        Long guildId, // Nullable for DMs
        String name,
        ChannelType type
) {}