package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.LoginRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class AccountPolicy
        implements Policy<LoginRequest, AccountBucket, MultiClientAccountFinding> {

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
    public List<MultiClientAccountFinding> evaluate(
            LoginRequest request,
            AccountBucket bucket
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(bucket, "bucket");

        AccountBucket.ClientSnapshot snapshot = bucket.clientSnapshot(request);
        boolean newClientIsOverLimit = !snapshot.containsClient()
                && snapshot.distinctClientCount() >= this.distinctClientLimit_;
        if (!newClientIsOverLimit) {
            return List.of();
        }

        return List.of(
                new MultiClientAccountFinding(
                    snapshot.distinctClientCount(),
                    this.distinctClientLimit_,
                    snapshot.permitsNewClientAt().orElseThrow()
                )
        );
    }
}
