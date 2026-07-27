package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;
import java.util.Objects;

public record ClientFinding(
        int count,
        int limit,
        Instant retryAt,
        Threshold threshold
) implements Finding {

    public ClientFinding {
        PolicySupport.requireReachedLimit(count, limit, "count", "limit");
        PolicySupport.requireRetryAt(retryAt);
        Objects.requireNonNull(threshold, "threshold");
    }

    public enum Threshold {
        BUCKET_CAPACITY,
        CLIENT_CONNECTIONS,
        GLOBAL_CONNECTIONS
    }
}
