package com.c446.disctui_client;

import com.c446.db.model.MessageEditEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientDataManagerTest {
    @Test
    void returnsLatestRevisionAsLastKnownState() {
        ClientDataManager manager = new ClientDataManager();
        manager.putEditHistory(42L, List.of(
                new MessageEditEntity(42L, 2, "newer", Instant.now()),
                new MessageEditEntity(42L, 1, "older", Instant.now())
        ));

        assertEquals("newer", manager.getLastKnownState(42L).orElseThrow());
    }
}
