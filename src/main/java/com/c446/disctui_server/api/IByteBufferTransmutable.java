package com.c446.disctui_server.api;

import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public interface ByteBufferTransmutable<K> {
    byte[] toByteArray();

    K fromByteArray(byte[] bytes);

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
