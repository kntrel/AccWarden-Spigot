package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;
import java.util.Objects;

public record AccountFinding(
        int count,
        int limit,
        Instant retryAt,
        Threshold threshold
) implements Finding {

    public AccountFinding {
        PolicySupport.requireReachedLimit(count, limit, "count", "limit");
        PolicySupport.requireRetryAt(retryAt);
        Objects.requireNonNull(threshold, "threshold");
    }

    public enum Threshold {
        BUCKET_CAPACITY,
        DISTINCT_CLIENTS
    }
}
