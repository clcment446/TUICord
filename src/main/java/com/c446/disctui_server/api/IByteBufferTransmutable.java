package com.c446.disctui_server.api;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface IByteBufferTransmutable<K> {
    int WIRE_MAGIC = 0x54554943; // "TUIC"
    byte WIRE_VERSION = 1;
    int WIRE_HEADER_SIZE = Integer.BYTES + Byte.BYTES + Byte.BYTES + Integer.BYTES;

    final class WireType {
        public static final byte RAW_BYTES = 0;
        public static final byte CLIENT_BOUND_MESSAGE = 1;
        public static final byte SERVER_BOUND_MESSAGE = 2;
        public static final byte CHANNEL_SELECT = 3;
        public static final byte DISCOVER = 4;
        public static final byte GUILD_UPDATE = 5;
        public static final byte CHANNEL_UPDATE = 6;
        public static final byte USER_UPDATE = 7;
        public static final byte MESSAGE_UPDATE = 8;
        public static final byte GUILD_USER_UPDATE = 9;

        private WireType() {
        }
    }

    record WireEnvelope(byte type, byte[] payload) {
    }

    byte[] toByteArray();

    K fromByteArray(byte[] bytes);

    /**
     * Wraps payload into a wire packet that includes a typed header.
     */
    static byte[] toWirePacket(byte messageType, byte[] payload) {
        if (payload == null) {
            payload = new byte[0];
        }

        ByteBuffer buffer = ByteBuffer.allocate(WIRE_HEADER_SIZE + payload.length);
        buffer.putInt(WIRE_MAGIC);
        buffer.put(WIRE_VERSION);
        buffer.put(messageType);
        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    /**
     * Parses one wire packet into typed payload bytes.
     */
    static WireEnvelope fromWirePacket(byte[] packet) {
        if (packet == null || packet.length < WIRE_HEADER_SIZE) {
            throw new IllegalArgumentException("Packet too small for wire header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(packet);
        int magic = buffer.getInt();
        if (magic != WIRE_MAGIC) {
            throw new IllegalArgumentException("Invalid wire magic: " + magic);
        }

        byte version = buffer.get();
        if (version != WIRE_VERSION) {
            throw new IllegalArgumentException("Unsupported wire version: " + version);
        }

        byte messageType = buffer.get();
        int payloadLength = buffer.getInt();

        if (payloadLength < 0 || payloadLength != buffer.remaining()) {
            throw new IllegalArgumentException("Invalid payload length in packet header: " + payloadLength);
        }

        byte[] payload = new byte[payloadLength];
        buffer.get(payload);
        return new WireEnvelope(messageType, payload);
    }

    /**
     * Writes one wire packet to a stream.
     */
    static void writeWirePacket(DataOutputStream out, byte messageType, byte[] payload) throws IOException {
        out.write(toWirePacket(messageType, payload));
        out.flush();
    }

    /**
     * Reads one wire packet from a stream.
     */
    static WireEnvelope readWirePacket(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != WIRE_MAGIC) {
            throw new IOException("Invalid wire magic: " + magic);
        }

        byte version = in.readByte();
        if (version != WIRE_VERSION) {
            throw new IOException("Unsupported wire version: " + version);
        }

        byte messageType = in.readByte();
        int payloadLength = in.readInt();

        if (payloadLength < 0) {
            throw new IOException("Negative payload length: " + payloadLength);
        }

        byte[] payload = new byte[payloadLength];
        in.readFully(payload);
        return new WireEnvelope(messageType, payload);
    }

    /**
     * Deserializes payload bytes into the concrete packet object for a wire type.
     */
    static Object decodeByType(byte wireType, byte[] payload) {
        return switch (wireType) {
            case WireType.CLIENT_BOUND_MESSAGE -> deserializeRecord(payload, ClientBoundMessage.class);
            case WireType.SERVER_BOUND_MESSAGE -> deserializeRecord(payload, ServerBoundMessage.class);
            case WireType.CHANNEL_SELECT -> deserializeRecord(payload, ChannelSelectPacket.class);
            case WireType.DISCOVER -> deserializeRecord(payload, DiscoverPacket.class);
            case WireType.GUILD_UPDATE -> deserializeRecord(payload, GuildUpdatePacket.class);
            case WireType.CHANNEL_UPDATE -> deserializeRecord(payload, ChannelUpdatePacket.class);
            case WireType.USER_UPDATE -> deserializeRecord(payload, UserUpdatePacket.class);
            case WireType.MESSAGE_UPDATE -> deserializeRecord(payload, MessageUpdatePacket.class);
            case WireType.GUILD_USER_UPDATE -> deserializeRecord(payload, GuildUserUpdatePacket.class);
            case WireType.RAW_BYTES -> payload;
            default -> throw new IllegalArgumentException("Unsupported wire type: " + wireType);
        };
    }

    /**
     * Generic serialization method for records using reflection.
     * Serializes all record components in declaration order.
     * Supports: String (UTF-8), Long, Integer, Boolean, Double, Float.
     */
    static byte[] serializeRecord(Object record) {
        ByteBuffer buffer = ByteBuffer.allocate(65536); // Initial capacity

        RecordComponent[] components = record.getClass().getRecordComponents();
        for (RecordComponent component : components) {
            try {
                Object value = component.getAccessor().invoke(record);
                serializeValue(buffer, value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to serialize record component: " + component.getName(), e);
            }
        }

        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    /**
     * Generic deserialization method for records using reflection.
     * Deserializes all record components in declaration order.
     */
    static <T> T deserializeRecord(byte[] bytes, Class<T> recordClass) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        RecordComponent[] components = recordClass.getRecordComponents();
        Object[] values = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            values[i] = deserializeValue(buffer, components[i].getType());
        }

        try {
            Class<?>[] parameterTypes = new Class[components.length];
            for (int i = 0; i < components.length; i++) {
                parameterTypes[i] = components[i].getType();
            }
            var constructor = recordClass.getDeclaredConstructor(parameterTypes);
            return constructor.newInstance(values);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize record: " + recordClass.getName(), e);
        }
    }

    private static void serializeValue(ByteBuffer buffer, Object value) {
        if (value == null) {
            buffer.put((byte) 0); // null marker
        } else if (value instanceof String str) {
            buffer.put((byte) 1); // string marker
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        } else if (value instanceof Long l) {
            buffer.put((byte) 2); // long marker
            buffer.putLong(l);
        } else if (value instanceof Integer i) {
            buffer.put((byte) 3); // int marker
            buffer.putInt(i);
        } else if (value instanceof Boolean b) {
            buffer.put((byte) 4); // boolean marker
            buffer.put((byte) (b ? 1 : 0));
        } else if (value instanceof Double d) {
            buffer.put((byte) 5); // double marker
            buffer.putDouble(d);
        } else if (value instanceof Float f) {
            buffer.put((byte) 6); // float marker
            buffer.putFloat(f);
        } else {
            throw new IllegalArgumentException("Unsupported type for serialization: " + value.getClass().getName());
        }
    }

    private static Object deserializeValue(ByteBuffer buffer, Class<?> type) {
        byte marker = buffer.get();

        return switch (marker) {
            case 0 -> null; // null
            case 1 -> { // string
                int length = buffer.getInt();
                byte[] bytes = new byte[length];
                buffer.get(bytes);
                yield new String(bytes, StandardCharsets.UTF_8);
            }
            case 2 -> buffer.getLong(); // long
            case 3 -> buffer.getInt(); // int
            case 4 -> buffer.get() != 0; // boolean
            case 5 -> buffer.getDouble(); // double
            case 6 -> buffer.getFloat(); // float
            default -> throw new IllegalArgumentException("Unknown serialization marker: " + marker);
        };
    }
}
