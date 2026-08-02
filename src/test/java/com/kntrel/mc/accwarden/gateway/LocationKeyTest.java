package com.kntrel.mc.accwarden.gateway;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationKeyTest {

    private static final UUID WORLD_ID = UUID.fromString("b84b7238-7277-4a5d-a631-6d109c62f954");

    @Test
    void serializesAndParsesLocation() {
        LocationKey location = new LocationKey(WORLD_ID, 12.5, -64.0, 903.25, 135.5f, -42.25f);
        byte[] serialized = location.toBytes();

        assertEquals(48, LocationKey.BYTE_LENGTH);
        assertEquals(LocationKey.BYTE_LENGTH, serialized.length);
        assertEquals(location, LocationKey.fromBytes(serialized));

        ByteBuffer buffer = ByteBuffer.wrap(serialized);
        assertEquals(WORLD_ID.getMostSignificantBits(), buffer.getLong());
        assertEquals(WORLD_ID.getLeastSignificantBits(), buffer.getLong());
    }

    @Test
    void rejectsMalformedLocations() {
        assertThrows(IllegalArgumentException.class, () -> LocationKey.fromBytes(new byte[1]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocationKey(WORLD_ID, 1, 2, 3, Float.NaN, 0)
        );
    }
}
