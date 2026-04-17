package com.c446.disctui_client;

import com.c446.disctui_server.api.ChannelSelectPacket;
import com.c446.disctui_server.api.IByteBufferTransmutable;
import com.c446.disctui_server.api.ServerBoundMessage;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import java.util.function.Consumer;

public class SocketEventClient implements Closeable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private Thread readThread;

    public SocketEventClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
    }

    public void startReadLoop(Consumer<Object> packetHandler, Consumer<String> infoHandler) {
        Objects.requireNonNull(packetHandler, "packetHandler");
        Objects.requireNonNull(infoHandler, "infoHandler");

        readThread = new Thread(() -> {
            while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                try {
                    IByteBufferTransmutable.WireEnvelope envelope = IByteBufferTransmutable.readWirePacket(input);
                    Object packet = IByteBufferTransmutable.decodeByType(envelope.type(), envelope.payload());
                    packetHandler.accept(packet);
                } catch (EOFException e) {
                    infoHandler.accept("Server closed the connection.");
                    break;
                } catch (SocketException e) {
                    infoHandler.accept("Connection lost.");
                    break;
                } catch (Exception e) {
                    infoHandler.accept("Failed to parse packet: " + e.getMessage());
                }
            }
        }, "master-client-reader");

        readThread.setDaemon(true);
        readThread.start();
    }

    public void sendChannelSelect(long channelId) throws IOException {
        ChannelSelectPacket packet = new ChannelSelectPacket(channelId);
        IByteBufferTransmutable.writeWirePacket(
                output,
                IByteBufferTransmutable.WireType.CHANNEL_SELECT,
                packet.toByteArray()
        );
    }

    public void sendMessage(String content, Long channelId) throws IOException {
        if (content == null || content.isBlank()) {
            return;
        }

        ServerBoundMessage packet = new ServerBoundMessage(content, channelId);
        IByteBufferTransmutable.writeWirePacket(
                output,
                IByteBufferTransmutable.WireType.SERVER_BOUND_MESSAGE,
                packet.toByteArray()
        );
    }

    public boolean isConnected() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        if (readThread != null) {
            readThread.interrupt();
        }
        socket.close();
    }
}

