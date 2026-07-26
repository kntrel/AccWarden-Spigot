package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Instant;

/**
 * A condition reported by a gateway policy.
 */
public interface Finding {

    /**
     * The earliest time at which this condition should be reconsidered.
     */
    Instant retryAt();

}
