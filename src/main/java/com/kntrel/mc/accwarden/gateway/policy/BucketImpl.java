package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Common bounded moving-window implementation for gateway buckets.
 * Concrete buckets supply their subject key and may maintain secondary indexes
 * through the record and discard hooks.
 */
abstract class BucketImpl<T, K> implements Bucket<T> {

    private final Duration window_;
    private final int maxRecords_;
    private final Clock clock_;
    private final Deque<Event<T, K>> events_ = new ArrayDeque<>();
    private final Map<K, Deque<Instant>> eventsBySubject_ = new HashMap<>();

    protected BucketImpl(Duration window, int maxRecords, Clock clock) {
        this.window_ = requirePositiveWindow_(window);
        if (maxRecords < 1) {
            throw new IllegalArgumentException("maxRecords must be at least 1.");
        }
        this.maxRecords_ = maxRecords;
        this.clock_ = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public final Duration window() {
        return this.window_;
    }

    @Override
    public final int maxRecords() {
        return this.maxRecords_;
    }

    @Override
    public final synchronized int size() {
        this.refresh();
        return this.events_.size();
    }

    @Override
    public final synchronized boolean isFull() {
        return this.size() == this.maxRecords_;
    }

    @Override
    public final synchronized Snapshot snapshot(T subject) {
        Objects.requireNonNull(subject, "subject");
        return this.snapshotOf(this.key(subject));
    }

    @Override
    public final synchronized void record(T subject) {
        Objects.requireNonNull(subject, "subject");
        this.record_(subject, this.key(subject));
    }

    protected abstract K key(T subject);

    protected final synchronized Snapshot snapshotOf(K key) {
        Objects.requireNonNull(key, "key");
        Instant now = this.refresh();
        Deque<Instant> subjectEvents = this.eventsBySubject_.get(key);
        return new Snapshot(
                now,
                this.events_.size(),
                subjectEvents == null ? 0 : subjectEvents.size(),
                this.maxRecords_,
                this.expirationOf_(this.events_.peekFirst()),
                this.expirationOf_(subjectEvents == null ? null : subjectEvents.peekFirst())
        );
    }

    protected final synchronized void record_(T subject, K key) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(key, "key");
        Instant now = this.refresh();
        if (this.events_.size() == this.maxRecords_) {
            throw new IllegalStateException("Bucket is full.");
        }

        Event<T, K> event = new Event<>(subject, key, now);
        this.events_.addLast(event);
        this.eventsBySubject_
                .computeIfAbsent(key, ignored -> new ArrayDeque<>())
                .addLast(now);
        this.onRecord(subject, key, now);
    }

    /**
     * Discards expired records and returns the observation time.
     * This method is safe to call from synchronized subclass operations.
     */
    protected final synchronized Instant refresh() {
        Instant now = this.clock_.instant();
        while (!this.events_.isEmpty() && this.hasExpired_(this.events_.peekFirst().recordedAt(), now)) {
            Event<T, K> expired = this.events_.removeFirst();
            Deque<Instant> subjectEvents = this.eventsBySubject_.get(expired.key());
            subjectEvents.removeFirst();
            if (subjectEvents.isEmpty()) {
                this.eventsBySubject_.remove(expired.key());
            }
            this.onDiscard(expired.subject(), expired.key(), expired.recordedAt());
        }
        return now;
    }

    protected void onRecord(T subject, K key, Instant recordedAt) {}

    protected void onDiscard(T subject, K key, Instant recordedAt) {}

    private boolean hasExpired_(Instant recordedAt, Instant now) {
        return !recordedAt.plus(this.window_).isAfter(now);
    }

    private Optional<Instant> expirationOf_(Event<T, K> event) {
        return event == null
                ? Optional.empty()
                : Optional.of(event.recordedAt().plus(this.window_));
    }

    private Optional<Instant> expirationOf_(Instant recordedAt) {
        return recordedAt == null
                ? Optional.empty()
                : Optional.of(recordedAt.plus(this.window_));
    }

    private static Duration requirePositiveWindow_(Duration window) {
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Bucket window must be positive.");
        }
        return window;
    }

    private record Event<T, K>(T subject, K key, Instant recordedAt) {}
}
