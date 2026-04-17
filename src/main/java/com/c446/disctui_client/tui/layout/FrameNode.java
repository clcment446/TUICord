package com.c446.disctui_client.tui.layout;

import java.util.List;

public interface FrameNode {
    void layout(FrameRect bounds);

    FrameRect bounds();

    List<String> render();
}

