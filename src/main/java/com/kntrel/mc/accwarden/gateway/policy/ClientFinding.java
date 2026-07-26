package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;

public sealed interface ClientFinding extends Finding {

    record BucketCapacityReached(int recordCount, int capacity, Instant retryAt) implements ClientFinding {

        public BucketCapacityReached {
            PolicySupport.requireReachedLimit(recordCount, capacity, "recordCount", "capacity");
            PolicySupport.requireRetryAt(retryAt);
        }
    }

    record ClientLimitReached(int connectionCount, int limit, Instant retryAt) implements ClientFinding {

        public ClientLimitReached {
            PolicySupport.requireReachedLimit(
                    connectionCount,
                    limit,
                    "connectionCount",
                    "limit"
            );
            PolicySupport.requireRetryAt(retryAt);
        }
    }

    record GlobalLimitReached(int connectionCount, int limit, Instant retryAt) implements ClientFinding {

        public GlobalLimitReached {
            PolicySupport.requireReachedLimit(
                    connectionCount,
                    limit,
                    "connectionCount",
                    "limit"
            );
            PolicySupport.requireRetryAt(retryAt);
        }
    }
}
