package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.Bucket;
import com.kntrel.mc.accwarden.gateway.policy.ClientBucket;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketTest {

    private static final Instant START = Instant.parse("2026-07-23T12:00:00Z");
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final NetworkKey CLIENT_A = client_(1);
    private static final NetworkKey CLIENT_B = client_(2);

    @Test
    void snapshotsExposeGlobalAndPerSubjectCounts() {
        MutableClock clock = new MutableClock(START);
        ClientBucket bucket = new ClientPolicy(WINDOW, 3, 10, 10).newBucket(clock);

        bucket.record(CLIENT_A);
        clock.advance(Duration.ofSeconds(2));
        bucket.record(CLIENT_A);
        bucket.record(CLIENT_B);

        Bucket.Snapshot snapshot = bucket.snapshot(CLIENT_A);

        assertEquals(3, snapshot.globalCount());
        assertEquals(2, snapshot.subjectCount());
        assertEquals(3, snapshot.maxRecords());
        assertTrue(snapshot.isFull());
        assertTrue(bucket.isFull());
        assertEquals(START.plus(WINDOW), snapshot.nextGlobalExpiration().orElseThrow());
        assertEquals(START.plus(WINDOW), snapshot.nextSubjectExpiration().orElseThrow());
        assertThrows(IllegalStateException.class, () -> bucket.record(CLIENT_B));
    }

    @Test
    void eventsDisappearAsTheWindowMovesIncludingAtItsBoundary() {
        MutableClock clock = new MutableClock(START);
        ClientBucket bucket = new ClientPolicy(WINDOW, 2, 10, 10).newBucket(clock);

        bucket.record(CLIENT_A);
        clock.advance(Duration.ofSeconds(2));
        bucket.record(CLIENT_A);
        clock.advance(Duration.ofSeconds(8));

        Bucket.Snapshot partiallyExpired = bucket.snapshot(CLIENT_A);
        assertEquals(1, partiallyExpired.globalCount());
        assertEquals(1, partiallyExpired.subjectCount());
        assertFalse(partiallyExpired.isFull());
        assertFalse(bucket.isFull());

        clock.advance(Duration.ofSeconds(2));
        Bucket.Snapshot empty = bucket.snapshot(CLIENT_A);
        assertEquals(0, empty.globalCount());
        assertEquals(0, empty.subjectCount());
        assertTrue(empty.nextGlobalExpiration().isEmpty());
        assertTrue(empty.nextSubjectExpiration().isEmpty());
    }

    private static NetworkKey client_(int lastByte) {
        return new NetworkKey(new byte[] {(byte) 192, 0, 2, (byte) lastByte});
    }
}
