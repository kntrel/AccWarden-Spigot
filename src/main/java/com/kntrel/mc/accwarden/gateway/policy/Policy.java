package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.Decision;

import java.time.Clock;

public interface Policy<T, B extends Bucket<T>> {

    /**
     * Creates fresh runtime state configured for this policy.
     * Ownership of the returned bucket belongs to the caller.
     */
    B newBucket(Clock clock);

    Decision consider(T subject, B bucket);

}
