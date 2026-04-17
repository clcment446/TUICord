package com.c446.disctui_client.tui;

import com.c446.disctui_client.ClientDataManager;

import java.util.Optional;

public class TuiCommandRouter {

    private final ClientDataManager dataManager;

    public TuiCommandRouter(ClientDataManager dataManager) {
        this.dataManager = dataManager;
    }

    /**
     * Shift+H (uppercase H) reveals history only for deleted messages.
     */
    public Optional<String> onKeyPress(char key, TuiMessage message) {
        if (key != 'H') {
            return Optional.empty();
        }
        return dataManager.getLastKnownStateForDeletedMessage(message);
    }
}
