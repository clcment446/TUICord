package com.c446.disctui_client;

import com.c446.disctui_server.api.ChannelUpdatePacket;
import com.c446.disctui_server.api.MessageUpdatePacket;
import com.c446.disctui_server.api.UserUpdatePacket;
import com.c446.disctui_client.tui.TuiAttachment;
import com.c446.disctui_client.tui.TuiMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientDataManagerTest {

    @Test
    void buildMessageExtractsImageAttachmentMetadata() {
        ClientDataManager state = new ClientDataManager();
        state.applyChannelUpdate(new ChannelUpdatePacket(10L, 1L, "general", 0, false));
        state.selectChannel(10L);

        String content = """
                {
                "raw":
                    "hello",
                "embeds":
                    [],
                "attachments":
                    [{"filename":"pic.png","url":"https://cdn.example/pic.png","proxyUrl":"https://media.example/pic.png","size":1234}]
                }
                """;

        state.applyMessageUpdate(new MessageUpdatePacket(99L, 10L, 1L, 123L, content, false, false, 0));

        List<TuiMessage> messages = state.getActiveMessages();
        assertEquals(1, messages.size());
        List<TuiAttachment> attachments = messages.getFirst().attachments();
        assertEquals(1, attachments.size());
        assertEquals("pic.png", attachments.getFirst().fileName());
        assertTrue(attachments.getFirst().image());
    }

    @Test
    void deleteUpdateKeepsExistingAuthorAndContent() {
        ClientDataManager state = new ClientDataManager();
        state.applyChannelUpdate(new ChannelUpdatePacket(10L, 1L, "general", 0, false));
        state.applyUserUpdate(new UserUpdatePacket(123L, "author", "Display Name", "https://cdn.example/avatar.png", false));
        state.selectChannel(10L);

        state.applyMessageUpdate(new MessageUpdatePacket(55L, 10L, 1L, 123L, "hello world", false, false, 0));
        state.applyMessageUpdate(new MessageUpdatePacket(55L, 10L, 1L, null, null, true, false, 0));

        TuiMessage deleted = state.getActiveMessages().getFirst();
        assertTrue(deleted.deleted());
        assertEquals(123L, deleted.userId());
        assertEquals("Display Name", deleted.username());
        assertEquals("hello world", deleted.content());
        assertFalse(deleted.avatarUrl() == null || deleted.avatarUrl().isBlank());
    }

    @Test
    void showCommandResultEnablesResultModeAndReturnsVisibleSlice() {
        ClientDataManager state = new ClientDataManager();
        state.showCommandResult("line1\nline2\nline3");

        assertTrue(state.isResultMode());
        assertEquals(List.of("line1", "line2"), state.getVisibleResultLines(2));
    }

    @Test
    void resultModeUpDownNavigationScrollsResultLines() {
        ClientDataManager state = new ClientDataManager();
        state.showCommandResult("line1\nline2\nline3");

        state.navigateDown();
        assertEquals(List.of("line2", "line3"), state.getVisibleResultLines(2));

        state.navigateUp();
        assertEquals(List.of("line1", "line2"), state.getVisibleResultLines(2));
    }
}
