package com.c446.disctui_client.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayWidthTest {

    @Test
    void emojiUsesDisplayWidthNotCodePointLength() {
        String value = "#🎟️ tickets";
        assertTrue(DisplayWidth.width(value) >= 10);
        assertTrue(DisplayWidth.width(value) != value.length());
    }

    @Test
    void fitPadsToExactTargetWidth() {
        String fitted = DisplayWidth.fit("abc", 6);
        assertEquals(6, DisplayWidth.width(fitted));
    }

    @Test
    void wrapSplitsLongTokens() {
        var wrapped = DisplayWidth.wrap("supercalifragilisticexpialidocious", 8);
        assertTrue(wrapped.size() > 1);
        for (String line : wrapped) {
            assertTrue(DisplayWidth.width(line) <= 8);
        }
    }

    @Test
    void cjkCharactersCountAsDoubleWidth() {
        String value = "須彌";
        assertEquals(4, DisplayWidth.width(value));
    }

    @Test
    void wrapPreservesLeadingWhitespace() {
        var wrapped = DisplayWidth.wrap("  #channel", 16);
        assertTrue(wrapped.getFirst().startsWith("  "));
    }
}

