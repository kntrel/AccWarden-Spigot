package com.kntrel.mc.accwarden.gateway.policy;

import java.time.Clock;
import java.util.List;

public interface Policy<T, B extends Bucket<T>, F extends Finding> {

    /**
     * Creates fresh runtime state configured for this policy.
     * Ownership of the returned bucket belongs to the caller.
     */
    B newBucket(Clock clock);

    /**
     * Returns every condition currently affecting {@code subject}.
     * The caller owns the final decision and may permit an operation with findings.
     */
    List<F> evaluate(T subject, B bucket);

}
