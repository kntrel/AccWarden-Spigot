package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.LoginRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AccountPolicy implements Policy<LoginRequest, AccountBucket, AccountFinding> {

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
    public List<AccountFinding> evaluate(LoginRequest request, AccountBucket bucket) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(bucket, "bucket");

        Bucket.Snapshot bucketSnapshot = bucket.snapshot(request);
        List<AccountFinding> findings = new ArrayList<>(2);
        if (bucketSnapshot.isFull()) {
            findings.add(new AccountFinding(
                    bucketSnapshot.globalCount(),
                    bucketSnapshot.maxRecords(),
                    bucketSnapshot.nextGlobalExpiration().orElseThrow(),
                    AccountFinding.Threshold.BUCKET_CAPACITY
            ));
        }

        AccountBucket.ClientSnapshot snapshot = bucket.clientSnapshot(request);
        boolean newClientIsOverLimit = !snapshot.containsClient()
                && snapshot.distinctClientCount() >= this.distinctClientLimit_;
        if (newClientIsOverLimit) {
            findings.add(new AccountFinding(
                    snapshot.distinctClientCount(),
                    this.distinctClientLimit_,
                    snapshot.permitsNewClientAt().orElseThrow(),
                    AccountFinding.Threshold.DISTINCT_CLIENTS
            ));
        }

        return List.copyOf(findings);
    }
}
