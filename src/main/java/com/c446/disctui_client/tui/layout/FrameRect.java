package com.c446.disctui_client.tui.layout;

public record FrameRect(int x, int y, int width, int height) {
    public FrameRect {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }
}

