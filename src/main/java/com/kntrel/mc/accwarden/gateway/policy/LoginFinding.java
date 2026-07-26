package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;

public sealed interface LoginFinding extends Finding {

    record BucketCapacityReached(int recordCount, int capacity, Instant retryAt) implements LoginFinding {

        public BucketCapacityReached {
            PolicySupport.requireReachedLimit(recordCount, capacity, "recordCount", "capacity");
            PolicySupport.requireRetryAt(retryAt);
        }
    }

    record AccountClientLimitReached(int attemptCount, int limit, Instant retryAt) implements LoginFinding {

        public AccountClientLimitReached {
            PolicySupport.requireReachedLimit(attemptCount, limit, "attemptCount", "limit");
            PolicySupport.requireRetryAt(retryAt);
        }
    }

    record FailedLoginLimitReached(int failureCount, int limit, Instant retryAt) implements LoginFinding {

        public FailedLoginLimitReached {
            PolicySupport.requireReachedLimit(failureCount, limit, "failureCount", "limit");
            PolicySupport.requireRetryAt(retryAt);
        }
    }
}
