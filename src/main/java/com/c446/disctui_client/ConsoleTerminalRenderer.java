package com.c446.disctui_client;

import com.c446.disctui_server.api.ChannelUpdatePacket;
import org.jline.keymap.KeyMap;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Reference;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsoleTerminalRenderer implements TerminalRenderer {
    private static final String[] EMOJI_SET = new String[]{"😀", "😂", "🔥", "👍", "✅", "🎉", "❤️", "😎", "🤝", "🙏"};

    @Override
    public LineReader buildLineReader(ClientDataManager dataManager) {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer((reader, line, candidates) -> completeInput(line, candidates, dataManager))
                    .build();

            AtomicInteger emojiIndex = new AtomicInteger(0);
            lineReader.getWidgets().put("emoji-picker", () -> {
                int idx = Math.floorMod(emojiIndex.getAndIncrement(), EMOJI_SET.length);
                lineReader.getBuffer().write(EMOJI_SET[idx]);
                return true;
            });

            lineReader.getWidgets().put("nav-up", () -> submitSyntheticCommand(lineReader, "/nav up"));
            lineReader.getWidgets().put("nav-down", () -> submitSyntheticCommand(lineReader, "/nav down"));
            lineReader.getWidgets().put("nav-left", () -> submitSyntheticCommand(lineReader, "/nav left"));
            lineReader.getWidgets().put("nav-right", () -> submitSyntheticCommand(lineReader, "/nav right"));
            lineReader.getKeyMaps().values()
                    .forEach(keyMap -> {
                        keyMap.bind(new Reference("emoji-picker"), KeyMap.ctrl('E'));
                        keyMap.bind(new Reference("emoji-picker"), "\u001be");
                        keyMap.bind(new Reference("nav-up"), "\u001b[A");
                        keyMap.bind(new Reference("nav-down"), "\u001b[B");
                        keyMap.bind(new Reference("nav-right"), "\u001b[C");
                        keyMap.bind(new Reference("nav-left"), "\u001b[D");
                    });

            printInfo("Emoji picker: press Ctrl+E to insert emojis.");
            return lineReader;
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize terminal renderer", e);
        }
    }

    @Override
    public void printInfo(String message) {
        System.out.println(message);
    }

    @Override
    public void printError(String message) {
        System.err.println(message);
    }

    @Override
    public void printIncomingMessage(String message) {
        System.out.println(message);
    }

    private void completeInput(ParsedLine line, List<Candidate> candidates, ClientDataManager dataManager) {
        List<String> words = line.words();
        int wordIndex = line.wordIndex();

        if (wordIndex == 0) {
            candidates.add(new Candidate("/channel"));
            candidates.add(new Candidate("/goto"));
            candidates.add(new Candidate("/guilds"));
            candidates.add(new Candidate("/channels"));
            candidates.add(new Candidate("/dms"));
            candidates.add(new Candidate("/clear"));
            candidates.add(new Candidate("/collapse"));
            candidates.add(new Candidate("/keybinds"));
            candidates.add(new Candidate("/help"));
            candidates.add(new Candidate("/send"));
            candidates.add(new Candidate("/quit"));
            return;
        }

        if (words.isEmpty()) {
            return;
        }

        String command = words.get(0);
        if (("/channel".equals(command) || "/goto".equals(command)) && wordIndex == 1) {
            for (ChannelUpdatePacket channel : dataManager.getActiveChannels()) {
                String value = String.valueOf(channel.channelId());
                String description = channel.name() == null ? "" : "#" + channel.name();
                candidates.add(new Candidate(value, value, "channels", description, null, null, true));
            }
        }
    }

    private boolean submitSyntheticCommand(LineReader lineReader, String command) {
        lineReader.getBuffer().clear();
        lineReader.getBuffer().write(command);
        lineReader.callWidget(LineReader.ACCEPT_LINE);
        return true;
    }
}

