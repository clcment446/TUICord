package com.c446.disctui_client.tui.layout;

import java.util.List;
import java.util.function.Function;

public class LeafFrame implements FrameNode {
    private final Function<FrameRect, List<String>> contentRenderer;
    private FrameRect bounds = new FrameRect(0, 0, 0, 0);

    public LeafFrame(Function<FrameRect, List<String>> contentRenderer) {
        this.contentRenderer = contentRenderer;
    }

    @Override
    public void layout(FrameRect bounds) {
        this.bounds = bounds;
    }

    @Override
    public FrameRect bounds() {
        return bounds;
    }

    @Override
    public List<String> render() {
        return contentRenderer.apply(bounds);
    }
}

