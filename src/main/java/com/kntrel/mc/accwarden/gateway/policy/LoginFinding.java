package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;
import java.util.Objects;

public record LoginFinding(
        int count,
        int limit,
        Instant retryAt,
        Threshold threshold
) implements Finding {

    public LoginFinding {
        PolicySupport.requireReachedLimit(count, limit, "count", "limit");
        PolicySupport.requireRetryAt(retryAt);
        Objects.requireNonNull(threshold, "threshold");
    }

    public enum Threshold {
        ACCOUNT_CLIENT_ATTEMPTS,
        CLIENT_FAILED_LOGINS
    }
}
