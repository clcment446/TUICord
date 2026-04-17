package com.c446.disctui_client.tui;

public record TuiMessage(
        long id,
        long channelId,
        String author,
        String content,
        boolean deleted
) {
    public String collapseContextKey() {
        return channelId + ":" + (author == null ? "" : author);
    }
}
