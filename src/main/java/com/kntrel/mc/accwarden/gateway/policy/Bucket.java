package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public interface Bucket<T> {

    /**
     * The amount of time for which a recorded event remains in this bucket.
     */
    Duration window();

    /**
     * The maximum number of live records this bucket can retain.
     */
    int maxRecords();

    /**
     * The number of live records currently retained by this bucket.
     * Implementations discard expired records before returning the count.
     */
    int size();

    /**
     * Returns whether this bucket has reached its record limit.
     */
    boolean isFull();

    /**
     * Returns an immutable view of the current window for {@code subject}.
     * Implementations discard expired events before creating the snapshot.
     */
    Snapshot snapshot(T subject);

    /**
     * Adds one event for {@code subject} to the current window.
     *
     * @throws IllegalStateException when the bucket is full
     */
    void record(T subject);

    record Snapshot(
            Instant observedAt,
            int globalCount,
            int subjectCount,
            int maxRecords,
            Optional<Instant> nextGlobalExpiration,
            Optional<Instant> nextSubjectExpiration
    ) {

        public Snapshot {
            Objects.requireNonNull(observedAt, "observedAt");
            Objects.requireNonNull(nextGlobalExpiration, "nextGlobalExpiration");
            Objects.requireNonNull(nextSubjectExpiration, "nextSubjectExpiration");
            if (maxRecords < 1) {
                throw new IllegalArgumentException("Maximum records must be at least 1.");
            }
            if (   globalCount > maxRecords
                || subjectCount < 0
                || subjectCount > globalCount
            ) {
                throw new IllegalArgumentException("Bucket counts are inconsistent.");
            }
            if (globalCount == 0 && nextGlobalExpiration.isPresent()) {
                throw new IllegalArgumentException("An empty bucket cannot have a global expiration.");
            }
            if (subjectCount == 0 && nextSubjectExpiration.isPresent()) {
                throw new IllegalArgumentException("An empty subject cannot have an expiration.");
            }
        }

        public boolean isFull() {
            return this.globalCount == this.maxRecords;
        }
    }
}
