package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;

public record BucketCapacityFinding(
        int count,
        int limit,
        Instant retryAt
) implements Finding {

    public BucketCapacityFinding {
        PolicySupport.requireReachedLimit(count, limit, "count", "limit");
        PolicySupport.requireRetryAt(retryAt);
    }
}
