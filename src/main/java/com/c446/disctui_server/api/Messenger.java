package com.c446.disctui_server.api;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Messenger {

    private static final Messenger INSTANCE = new Messenger();

    private final Object lifecycleLock = new Object();
    private final Queue<byte[]> pendingMessages = new ConcurrentLinkedQueue<>();

    private Socket clientSocket;
    private DataOutputStream clientOutput;

    private Messenger() {
    }

    public static Messenger getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a new client socket as the active session.
     * If another client is already connected, it is disconnected first.
     */
    public void registerSocket(Socket socket) throws IOException {
        synchronized (lifecycleLock) {
            closeCurrentSocket();
            clientSocket = socket;
            clientOutput = new DataOutputStream(socket.getOutputStream());
        }

        flushQueue();
    }

    public void send(ClientBoundMessage message) {
        send(IByteBufferTransmutable.WireType.CLIENT_BOUND_MESSAGE, message.toByteArray());
    }

    public void send(DiscoverPacket message) {
        send(IByteBufferTransmutable.WireType.DISCOVER, message.toByteArray());
    }

    public void send(GuildUpdatePacket message) {
        send(IByteBufferTransmutable.WireType.GUILD_UPDATE, message.toByteArray());
    }

    public void send(ChannelUpdatePacket message) {
        send(IByteBufferTransmutable.WireType.CHANNEL_UPDATE, message.toByteArray());
    }

    public void send(UserUpdatePacket message) {
        send(IByteBufferTransmutable.WireType.USER_UPDATE, message.toByteArray());
    }

    public void send(MessageUpdatePacket message) {
        send(IByteBufferTransmutable.WireType.MESSAGE_UPDATE, message.toByteArray());
    }

    public void send(GuildUserUpdatePacket message) {
        send(IByteBufferTransmutable.WireType.GUILD_USER_UPDATE, message.toByteArray());
    }

    /**
     * Sends payload to the active client using typed wire packets.
     * If no client is connected (or a write fails), payload is queued.
     */
    public void send(byte[] payload) {
        send(IByteBufferTransmutable.WireType.RAW_BYTES, payload);
    }

    public void send(byte messageType, byte[] payload) {
        if (payload == null) {
            return;
        }

        byte[] packet = IByteBufferTransmutable.toWirePacket(messageType, payload);

        synchronized (lifecycleLock) {
            if (!hasActiveSocketLocked()) {
                pendingMessages.offer(packet);
                return;
            }

            try {
                writePacket(packet);
            } catch (IOException e) {
                pendingMessages.offer(packet);
                closeCurrentSocket();
            }
        }
    }

    public void flushQueue() {
        synchronized (lifecycleLock) {
            if (!hasActiveSocketLocked()) {
                return;
            }

            while (!pendingMessages.isEmpty()) {
                byte[] nextPayload = pendingMessages.peek();
                if (nextPayload == null) {
                    pendingMessages.poll();
                    continue;
                }

                try {
                    writePacket(nextPayload);
                    pendingMessages.poll();
                } catch (IOException e) {
                    // Keep the message queued for the next successful reconnect.
                    closeCurrentSocket();
                    return;
                }
            }
        }
    }

    public boolean hasActiveSocket() {
        synchronized (lifecycleLock) {
            return hasActiveSocketLocked();
        }
    }

    public int queuedMessageCount() {
        return pendingMessages.size();
    }

    public void disconnect() {
        synchronized (lifecycleLock) {
            closeCurrentSocket();
        }
    }

    private boolean hasActiveSocketLocked() {
        return clientSocket != null && clientOutput != null && !clientSocket.isClosed();
    }

    private void writePacket(byte[] packet) throws IOException {
        clientOutput.write(packet);
        clientOutput.flush();
    }

    private void closeCurrentSocket() {
        if (clientOutput != null) {
            try {
                clientOutput.close();
            } catch (IOException ignored) {
                // no-op
            }
        }

        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
                // no-op
            }
        }

        clientOutput = null;
        clientSocket = null;
    }
}
