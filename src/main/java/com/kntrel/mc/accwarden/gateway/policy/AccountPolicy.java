package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.Decision;
import com.kntrel.mc.accwarden.gateway.LoginRequest;
import com.kntrel.mc.accwarden.gateway.Penalty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class AccountPolicy implements Policy<LoginRequest, AccountBucket> {

    private final Duration window_;
    private final int maxRecords_, distinctClientLimit_;

    public AccountPolicy(Duration window, int maxRecords, int distinctClientLimit) {
        this.window_ = PolicySupport.requirePositiveWindow(window);
        this.maxRecords_ = PolicySupport.requirePositiveLimit(maxRecords, "maxRecords");
        this.distinctClientLimit_ = PolicySupport.requirePositiveLimit(
                distinctClientLimit,
                "distinctClientLimit"
        );
    }

    @Override
    public AccountBucket newBucket(Clock clock) {
        return new AccountBucket(this.window_, this.maxRecords_, clock);
    }

    @Override
    public Decision consider(LoginRequest request, AccountBucket bucket) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(bucket, "bucket");

        Bucket.Snapshot bucketSnapshot = bucket.snapshot(request);
        if (bucketSnapshot.isFull()) {
            Instant throttledUntil = bucketSnapshot.nextGlobalExpiration().orElseThrow();
            return new Decision.Throttled(new Penalty(request.network(), throttledUntil));
        }

        AccountBucket.ClientSnapshot snapshot = bucket.clientSnapshot(request);
        boolean newClientIsOverLimit = !snapshot.containsClient()
                && snapshot.distinctClientCount() >= this.distinctClientLimit_;
        if (!newClientIsOverLimit) {
            return new Decision.Pass();
        }

        Instant throttledUntil = snapshot.permitsNewClientAt().orElseThrow();
        return new Decision.Throttled(new Penalty(request.network(), throttledUntil));
    }
}
