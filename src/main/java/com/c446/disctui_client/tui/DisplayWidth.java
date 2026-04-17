package com.c446.disctui_client.tui;

import org.jline.utils.WCWidth;

import java.util.ArrayList;
import java.util.List;

public final class DisplayWidth {
    private static final String ANSI_REGEX = "\\u001B\\[[;\\d]*m";

    private DisplayWidth() {
    }

    public static String stripAnsi(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll(ANSI_REGEX, "");
    }

    public static int width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String clean = stripAnsi(text);
        int total = 0;
        for (int i = 0; i < clean.length();) {
            int cp = clean.codePointAt(i);
            int w = WCWidth.wcwidth(cp);
            if (w > 0) {
                total += w;
            }
            i += Character.charCount(cp);
        }
        return total;
    }

    public static String fit(String text, int targetWidth) {
        String safe = text == null ? "" : text;
        if (targetWidth <= 0) {
            return "";
        }

        int current = width(safe);
        if (current == targetWidth) {
            return safe;
        }
        if (current < targetWidth) {
            return safe + " ".repeat(targetWidth - current);
        }

        String clean = stripAnsi(safe);
        if (targetWidth == 1) {
            return "…";
        }

        int keepWidth = Math.max(0, targetWidth - 1);
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < clean.length();) {
            int cp = clean.codePointAt(i);
            int w = Math.max(0, WCWidth.wcwidth(cp));
            if (used + w > keepWidth) {
                break;
            }
            sb.appendCodePoint(cp);
            used += w;
            i += Character.charCount(cp);
        }
        sb.append('…');

        int after = width(sb.toString());
        if (after < targetWidth) {
            sb.append(" ".repeat(targetWidth - after));
        }
        return sb.toString();
    }

    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (width <= 0) {
            out.add("");
            return out;
        }

        if (text == null || text.isBlank()) {
            out.add("");
            return out;
        }

        String clean = stripAnsi(text);
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;

        for (int i = 0; i < clean.length();) {
            int cp = clean.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == '\r') {
                continue;
            }

            if (cp == '\n') {
                out.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
                continue;
            }

            if (cp == '\t') {
                int tabSpaces = 4;
                for (int t = 0; t < tabSpaces; t++) {
                    if (lineWidth >= width) {
                        out.add(line.toString());
                        line.setLength(0);
                        lineWidth = 0;
                    }
                    line.append(' ');
                    lineWidth += 1;
                }
                continue;
            }

            int cpWidth = Math.max(1, WCWidth.wcwidth(cp));
            if (lineWidth + cpWidth > width && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
            }

            line.appendCodePoint(cp);
            lineWidth += cpWidth;

            if (lineWidth >= width) {
                out.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
            }
        }

        if (!line.isEmpty()) {
            out.add(line.toString());
        }

        if (out.isEmpty()) {
            out.add("");
        }

        return out;
    }
}

