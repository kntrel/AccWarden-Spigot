package com.kntrel.mc.accwarden.gateway;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

record LocationKey(
        UUID worldId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    static final int BYTE_LENGTH = (Long.BYTES * 2) + (Double.BYTES * 3) + (Float.BYTES * 2);

    LocationKey {
        Objects.requireNonNull(worldId, "worldId");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Location coordinates must be finite.");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Location rotation must be finite.");
        }
    }

    byte[] toBytes() {
        return ByteBuffer.allocate(BYTE_LENGTH)
                .putLong(this.worldId.getMostSignificantBits())
                .putLong(this.worldId.getLeastSignificantBits())
                .putDouble(this.x)
                .putDouble(this.y)
                .putDouble(this.z)
                .putFloat(this.yaw)
                .putFloat(this.pitch)
                .array();
    }

    static LocationKey fromBytes(byte[] value) {
        Objects.requireNonNull(value, "value");
        if (value.length != BYTE_LENGTH) {
            throw new IllegalArgumentException(
                    "Invalid location key length: expected " + BYTE_LENGTH + " bytes, got " + value.length + "."
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(value);
        return new LocationKey(
                new UUID(buffer.getLong(), buffer.getLong()),
                buffer.getDouble(),
                buffer.getDouble(),
                buffer.getDouble(),
                buffer.getFloat(),
                buffer.getFloat()
        );
    }
}
