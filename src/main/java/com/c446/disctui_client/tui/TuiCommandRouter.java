package com.c446.disctui_client.tui;

import com.c446.disctui_client.ClientDataManager;

import java.util.Optional;

public class TuiCommandRouter {

    private final ClientDataManager dataManager;

    public TuiCommandRouter(ClientDataManager dataManager) {
        this.dataManager = dataManager;
    }

    public Optional<String> onKeyPress(char key, TuiMessage focusedMessage) {
        if (key != 'H') {
            return Optional.empty();
        }
        return dataManager.getLastKnownState(focusedMessage);
    }
}
