package com.c446.disctui_client.tui;

import com.c446.db.model.MessageEditEntity;
import com.c446.disctui_client.ClientDataManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiCommandRouterTest {
    @Test
    void shiftHReturnsLastKnownDeletedState() {
        ClientDataManager manager = new ClientDataManager();
        manager.putEditHistory(100L, List.of(new MessageEditEntity(100L, 1, "last known", Instant.now())));

        TuiCommandRouter router = new TuiCommandRouter(manager);
        TuiMessage focusedDeletedMessage = new TuiMessage(100L, 1L, "user", null, true);

        assertEquals("last known", router.onKeyPress('H', focusedDeletedMessage).orElseThrow());
        assertTrue(router.onKeyPress('h', focusedDeletedMessage).isEmpty());
    }
}
