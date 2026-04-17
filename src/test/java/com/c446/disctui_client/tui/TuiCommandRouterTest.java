package com.c446.disctui_client.tui;

import com.c446.disctui_client.ClientDataManager;
import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.GuildUpdatePacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiCommandRouterTest {

    @Test
    void gotoSelectsChannelByName() {
        ClientDataManager state = new ClientDataManager();
        state.applyGuildUpdate(new GuildUpdatePacket(1L, "Main Guild", null, false));
        state.applyChannelUpdate(new ChannelUpdatePacket(10L, 1L, "general", 0, false));

        TuiCommandRouter router = new TuiCommandRouter();
        TuiCommandRouter.CommandResult result = router.route("/goto general", state);

        assertTrue(result.handled());
        assertEquals(10L, result.channelToSendTo());
        assertEquals(10L, state.getActiveChannelId());
        assertEquals(1L, state.getActiveGuildId());
    }

    @Test
    void clearEmptiesCurrentBuffer() {
        ClientDataManager state = new ClientDataManager();
        state.applyChannelUpdate(new ChannelUpdatePacket(10L, 1L, "general", 0, false));
        state.selectChannel(10L);
        state.applyMessageUpdate(new com.c446.disctui_server.api.MessageUpdatePacket(99L, 10L, 1L, 123L, "hello", false, false, 0));

        TuiCommandRouter router = new TuiCommandRouter();
        router.route("/clear", state);

        assertTrue(state.getActiveMessages().isEmpty());
    }

    @Test
    void collapseCommandTogglesState() {
        ClientDataManager state = new ClientDataManager();
        TuiCommandRouter router = new TuiCommandRouter();

        assertTrue(!state.isCollapseMessages());
        router.route("/collapse", state);
        assertTrue(state.isCollapseMessages());
        router.route("/collapse", state);
        assertTrue(!state.isCollapseMessages());
    }

    @Test
    void keybindsCommandReturnsHelpText() {
        ClientDataManager state = new ClientDataManager();
        TuiCommandRouter router = new TuiCommandRouter();

        TuiCommandRouter.CommandResult result = router.route("/keybinds", state);

        assertTrue(result.handled());
        assertNotNull(result.status());
        assertTrue(result.status().contains("Up/Down"));
        assertTrue(result.status().contains("Result mode"));
    }
}
