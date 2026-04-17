package com.c446.disctui_client;

import com.c446.db.model.MessageEditEntity;
import com.c446.disctui_client.tui.TuiMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ClientDataManager {
    private static final Comparator<MessageEditEntity> BY_REVISION = Comparator.comparingInt(e -> e.revision);

    private final Map<Long, List<MessageEditEntity>> editsByMessageId = new ConcurrentHashMap<>();

    public void putEditHistory(long messageId, List<MessageEditEntity> edits) {
        List<MessageEditEntity> copy = new ArrayList<>(edits);
        sortEdits(copy);
        editsByMessageId.put(messageId, copy);
    }

    public void addEdit(MessageEditEntity edit) {
        editsByMessageId.computeIfAbsent(edit.messageId, ignored -> new ArrayList<>()).add(edit);
        sortEdits(editsByMessageId.get(edit.messageId));
    }

    public Optional<String> getLastKnownState(long messageId) {
        List<MessageEditEntity> edits = editsByMessageId.get(messageId);
        if (edits == null || edits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(edits.get(edits.size() - 1).content);
    }

    public Optional<String> getLastKnownStateForDeletedMessage(TuiMessage message) {
        if (message == null || !message.deleted()) {
            return Optional.empty();
        }
        return getLastKnownState(message.id());
    }

    private static void sortEdits(List<MessageEditEntity> edits) {
        edits.sort(BY_REVISION);
    }
}
