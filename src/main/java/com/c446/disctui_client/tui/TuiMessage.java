package com.c446.disctui_client.tui;

public record TuiMessage(
        long id,
        long channelId,
        String author,
        String content,
        boolean deleted
) {
    public String collapseContextKey() {
        String safeAuthor = author == null ? "" : author;
        return channelId + "|" + safeAuthor.length() + "|" + safeAuthor;
    }
}
