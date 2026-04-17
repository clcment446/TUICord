package com.c446.disctui_client.tui;

import com.c446.disctui_client.ClientDataManager;
import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.GuildUpdatePacket;
import com.c446.disctui_server.api.MessageUpdatePacket;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnsiTuiRendererTest {

    @Test
    void renderShowsDeletedMarkerInRed() {
        ClientDataManager state = new ClientDataManager();
        state.applyGuildUpdate(new GuildUpdatePacket(1L, "Main", null, false));
        state.applyChannelUpdate(new ChannelUpdatePacket(10L, 1L, "general", 0, false));
        state.selectChannel(10L);
        state.applyMessageUpdate(new MessageUpdatePacket(77L, 10L, 1L, 123L, "gone", true, false, 0));

        AnsiTuiRenderer renderer = new AnsiTuiRenderer();

        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            renderer.render(state, "", 100, 16);
        } finally {
            System.setOut(previous);
        }

        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("\u001b[38;2;255;90;90m<deleted>\u001b[0m"));
    }
}

