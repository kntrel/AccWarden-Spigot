package com.kntrel.mc.accwarden.gateway;

import java.util.Objects;
import java.util.UUID;

public record LoginRequest(UUID accountId, NetworkKey network) {

    public LoginRequest {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(network, "network");
    }
}
