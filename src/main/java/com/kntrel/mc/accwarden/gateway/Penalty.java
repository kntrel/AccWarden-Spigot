package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.Finding;

import java.time.Instant;
import java.util.Objects;

public record Penalty(NetworkKey client, Instant until, Finding cause) {

    public Penalty {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(cause, "cause");
        if (!until.equals(cause.retryAt())) {
            throw new IllegalArgumentException("Penalty expiration must match its cause retry time.");
        }
    }
}
