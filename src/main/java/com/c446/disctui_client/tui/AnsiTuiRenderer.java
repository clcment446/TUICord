package com.c446.disctui_client.tui;

import com.c446.disctui_client.ClientDataManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AnsiTuiRenderer {
    private static final String RED = "\u001B[38;2;255;90;90m";
    private static final String RESET = "\u001B[0m";

    private final Map<Long, String> revealedDeletedStates = new LinkedHashMap<>();

    public Optional<String> handleShiftH(TuiMessage focusedMessage, ClientDataManager dataManager) {
        Optional<String> lastKnown = dataManager.getLastKnownStateForDeletedMessage(focusedMessage);
        lastKnown.ifPresent(content -> revealedDeletedStates.put(focusedMessage.id(), content));
        return lastKnown;
    }

    public List<String> renderMessages(List<TuiMessage> messages) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            TuiMessage current = messages.get(i);
            if (!current.deleted()) {
                out.add(current.content() == null ? "" : current.content());
                i++;
                continue;
            }

            String context = current.collapseContextKey();
            int runCount = 1;
            int j = i + 1;
            while (j < messages.size()) {
                TuiMessage next = messages.get(j);
                if (!next.deleted() || !context.equals(next.collapseContextKey())) {
                    break;
                }
                runCount++;
                j++;
            }

            String deletedLine = runCount > 1 ? "<deleted> (" + runCount + ")" : "<deleted>";
            out.add(RED + deletedLine + RESET);

            for (int idx = i; idx < j; idx++) {
                TuiMessage grouped = messages.get(idx);
                String revealed = revealedDeletedStates.get(grouped.id());
                if (revealed != null) {
                    out.add("↳ " + revealed);
                }
            }

            i = j;
        }
        return out;
    }
}
