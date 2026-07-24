package com.kntrel.mc.accwarden.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class MutableClock extends Clock {

    private Instant instant_;

    MutableClock(Instant instant) {
        this.instant_ = instant;
    }

    void advance(Duration duration) {
        this.instant_ = this.instant_.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (!ZoneOffset.UTC.equals(zone)) {
            throw new UnsupportedOperationException("MutableClock only supports UTC.");
        }
        return this;
    }

    @Override
    public Instant instant() {
        return this.instant_;
    }
}
