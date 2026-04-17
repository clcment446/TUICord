package com.c446.disctui_client;

import org.jline.reader.LineReader;

public interface TerminalRenderer {
    LineReader buildLineReader(ClientDataManager dataManager);

    void printInfo(String message);

    void printError(String message);

    void printIncomingMessage(String message);
}

