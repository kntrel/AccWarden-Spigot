package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;

public record MultiClientAccountFinding(
        int count,
        int limit,
        Instant retryAt
) implements Finding {

    public MultiClientAccountFinding {
        PolicySupport.requireReachedLimit(count, limit, "count", "limit");
        PolicySupport.requireRetryAt(retryAt);
    }
}
