package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

final class PolicySupport {

    private PolicySupport() {}

    static Duration requirePositiveWindow(Duration window) {
        Objects.requireNonNull(window, "window");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("Policy window must be positive.");
        }
        return window;
    }

    static int requirePositiveLimit(int limit, String name) {
        if (limit < 1) {
            throw new IllegalArgumentException(name + " must be at least 1.");
        }
        return limit;
    }

    static void requireReachedLimit(int count, int limit, String countName, String limitName) {
        requirePositiveLimit(limit, limitName);
        if (count < limit) {
            throw new IllegalArgumentException(
                    countName + " must be at least " + limitName + "."
            );
        }
    }

    static Instant requireRetryAt(Instant retryAt) {
        return Objects.requireNonNull(retryAt, "retryAt");
    }
}
