package com.c446.disctui_client.tui;

import com.c446.db.model.MessageEditEntity;
import com.c446.disctui_client.ClientDataManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiTuiRendererTest {

    @Test
    void collapsesConsecutiveDeletedMessagesFromSameContext() {
        AnsiTuiRenderer renderer = new AnsiTuiRenderer();

        List<TuiMessage> messages = List.of(
                new TuiMessage(1L, 10L, "HeavenlyOverseer", "first", true),
                new TuiMessage(2L, 10L, "HeavenlyOverseer", "second", true),
                new TuiMessage(3L, 11L, "HeavenlyOverseer", "live", false)
        );

        List<String> lines = renderer.renderMessages(messages);
        assertTrue(lines.get(0).contains("<deleted> (2)"));
        assertEquals("live", lines.get(1));
    }

    @Test
    void shiftHRevealsLastKnownStateFromEditHistory() {
        ClientDataManager manager = new ClientDataManager();
        manager.putEditHistory(7L, List.of(
                new MessageEditEntity(7L, 1, "before first edit", Instant.now()),
                new MessageEditEntity(7L, 2, "last known before delete", Instant.now())
        ));

        AnsiTuiRenderer renderer = new AnsiTuiRenderer();
        TuiMessage deleted = new TuiMessage(7L, 10L, "HeavenlyOverseer", null, true);

        String revealed = renderer.handleShiftH(deleted, manager).orElseThrow();
        List<String> lines = renderer.renderMessages(List.of(deleted));

        assertEquals("last known before delete", revealed);
        assertEquals("↳ last known before delete", lines.get(1));
    }
}
