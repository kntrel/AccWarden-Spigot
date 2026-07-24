package com.kntrel.mc.accwarden.gateway;

import java.time.Instant;
import java.util.Objects;

public record Penalty(NetworkKey client, Instant until) {

    public Penalty {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(until, "until");
    }
}
