package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;

public sealed interface AccountFinding extends Finding {

    record BucketCapacityReached(int recordCount, int capacity, Instant retryAt) implements AccountFinding {

        public BucketCapacityReached {
            PolicySupport.requireReachedLimit(recordCount, capacity, "recordCount", "capacity");
            PolicySupport.requireRetryAt(retryAt);
        }
    }

    record DistinctClientLimitReached(int clientCount, int limit, Instant retryAt) implements AccountFinding {

        public DistinctClientLimitReached {
            PolicySupport.requireReachedLimit(clientCount, limit, "clientCount", "limit");
            PolicySupport.requireRetryAt(retryAt);
        }
    }
}