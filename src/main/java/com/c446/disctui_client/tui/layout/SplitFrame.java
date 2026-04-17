package com.c446.disctui_client.tui.layout;

import java.util.ArrayList;
import java.util.List;

public class SplitFrame implements FrameNode {
    private final SplitOrientation orientation;
    private final FrameNode first;
    private final FrameNode second;
    private final int separatorSize;
    private final Character separatorChar;
    private final Integer secondFixedSize;
    private final double firstRatio;

    private FrameRect bounds = new FrameRect(0, 0, 0, 0);

    public SplitFrame(SplitOrientation orientation,
                      FrameNode first,
                      FrameNode second,
                      int separatorSize,
                      Character separatorChar,
                      Integer secondFixedSize,
                      double firstRatio) {
        this.orientation = orientation;
        this.first = first;
        this.second = second;
        this.separatorSize = Math.max(0, separatorSize);
        this.separatorChar = separatorChar;
        this.secondFixedSize = secondFixedSize;
        this.firstRatio = firstRatio <= 0 ? 0.5d : firstRatio;
    }

    @Override
    public void layout(FrameRect bounds) {
        this.bounds = bounds;

        if (orientation == SplitOrientation.HORIZONTAL) {
            int available = Math.max(0, bounds.width() - separatorSize);
            int secondWidth = secondFixedSize != null ? Math.min(Math.max(0, secondFixedSize), available) : 0;
            int firstWidth = secondFixedSize != null
                    ? Math.max(0, available - secondWidth)
                    : Math.max(0, (int) Math.round(available * firstRatio));
            if (secondFixedSize == null) {
                secondWidth = Math.max(0, available - firstWidth);
            }

            first.layout(new FrameRect(bounds.x(), bounds.y(), firstWidth, bounds.height()));
            second.layout(new FrameRect(bounds.x() + firstWidth + separatorSize, bounds.y(), secondWidth, bounds.height()));
            return;
        }

        int available = Math.max(0, bounds.height() - separatorSize);
        int secondHeight = secondFixedSize != null ? Math.min(Math.max(0, secondFixedSize), available) : 0;
        int firstHeight = secondFixedSize != null
                ? Math.max(0, available - secondHeight)
                : Math.max(0, (int) Math.round(available * firstRatio));
        if (secondFixedSize == null) {
            secondHeight = Math.max(0, available - firstHeight);
        }

        first.layout(new FrameRect(bounds.x(), bounds.y(), bounds.width(), firstHeight));
        second.layout(new FrameRect(bounds.x(), bounds.y() + firstHeight + separatorSize, bounds.width(), secondHeight));
    }

    @Override
    public FrameRect bounds() {
        return bounds;
    }

    @Override
    public List<String> render() {
        List<String> firstLines = first.render();
        List<String> secondLines = second.render();
        List<String> out = new ArrayList<>();

        if (orientation == SplitOrientation.HORIZONTAL) {
            int rows = Math.max(firstLines.size(), secondLines.size());
            String separator = separatorChar == null ? "" : String.valueOf(separatorChar).repeat(separatorSize);
            for (int row = 0; row < rows; row++) {
                String left = row < firstLines.size() ? firstLines.get(row) : " ".repeat(first.bounds().width());
                String right = row < secondLines.size() ? secondLines.get(row) : " ".repeat(second.bounds().width());
                out.add(left + separator + right);
            }
            return out;
        }

        out.addAll(firstLines);
        if (separatorChar != null && separatorSize > 0) {
            for (int i = 0; i < separatorSize; i++) {
                out.add(String.valueOf(separatorChar).repeat(Math.max(0, bounds.width())));
            }
        }
        out.addAll(secondLines);
        return out;
    }
}

