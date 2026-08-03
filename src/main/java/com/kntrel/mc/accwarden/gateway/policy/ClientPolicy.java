package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.NetworkKey;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class ClientPolicy implements Policy<NetworkKey, ClientBucket, ClientFinding> {

    private final Duration window_, penalty_;
    private final int connectionLimit_, attemptLimit_;
    private final boolean attemptLimitEnabled_;

    public ClientPolicy(
            Duration window,
            int connectionLimit,
            boolean attemptLimitEnabled,
            int attemptLimit,
            Duration penalty
    ) {
        this.window_ = PolicySupport.requirePositiveWindow(window);
        this.connectionLimit_ = PolicySupport.requirePositiveLimit(
                connectionLimit,
                "connectionLimit"
        );
        this.attemptLimitEnabled_ = attemptLimitEnabled;
        this.attemptLimit_ = PolicySupport.requirePositiveLimit(attemptLimit, "attemptLimit");
        this.penalty_ = PolicySupport.requirePositiveDuration(penalty, "penalty");
    }

    @Override
    public ClientBucket newBucket(Clock clock) {
        return new ClientBucket(this.window_, this.connectionLimit_, clock);
    }

    @Override
    public List<ClientFinding> evaluate(NetworkKey client, ClientBucket bucket) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(bucket, "bucket");

        if (!this.attemptLimitEnabled_) {
            return List.of();
        }

        Bucket.Snapshot snapshot = bucket.snapshot(client);
        if (snapshot.subjectCount() < this.attemptLimit_) {
            return List.of();
        }

        return List.of(new ClientFinding(
                snapshot.subjectCount(),
                this.attemptLimit_,
                snapshot.observedAt().plus(this.penalty_),
                ClientFinding.Threshold.CLIENT_CONNECTIONS
        ));
    }
}
