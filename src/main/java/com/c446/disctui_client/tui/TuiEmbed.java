package com.c446.disctui_client.tui;

import java.util.List;

public record TuiEmbed(String title,
                       String description,
                       List<Field> fields,
                       String footer,
                       String imageUrl,
                       String thumbnailUrl,
                       String url,
                       String color) {
    public record Field(String name, String value, boolean inline) {
    }
}

