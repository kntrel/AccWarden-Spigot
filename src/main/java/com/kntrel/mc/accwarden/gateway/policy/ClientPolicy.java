package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.Decision;
import com.kntrel.mc.accwarden.gateway.NetworkKey;
import com.kntrel.mc.accwarden.gateway.Penalty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ClientPolicy implements Policy<NetworkKey, ClientBucket> {

    private final Duration window_;
    private final int maxRecords_, perClientLimit_, globalLimit_;

    public ClientPolicy(Duration window, int maxRecords, int perClientLimit, int globalLimit) {
        this.window_ = PolicySupport.requirePositiveWindow(window);
        this.maxRecords_ = PolicySupport.requirePositiveLimit(maxRecords, "maxRecords");
        this.perClientLimit_ = PolicySupport.requirePositiveLimit(perClientLimit, "perClientLimit");
        this.globalLimit_ = PolicySupport.requirePositiveLimit(globalLimit, "globalLimit");
    }

    @Override
    public ClientBucket newBucket(Clock clock) {
        return new ClientBucket(this.window_, this.maxRecords_, clock);
    }

    @Override
    public Decision consider(NetworkKey client, ClientBucket bucket) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(bucket, "bucket");

        Bucket.Snapshot snapshot = bucket.snapshot(client);
        Instant throttledUntil = null;
        if (snapshot.isFull()) {
            throttledUntil = snapshot.nextGlobalExpiration().orElseThrow();
        }
        if (snapshot.subjectCount() >= this.perClientLimit_) {
            throttledUntil = PolicySupport.later(
                    throttledUntil,
                    snapshot.nextSubjectExpiration().orElseThrow()
            );
        }
        if (snapshot.globalCount() >= this.globalLimit_) {
            Instant globalExpiration = snapshot.nextGlobalExpiration().orElseThrow();
            throttledUntil = PolicySupport.later(throttledUntil, globalExpiration);
        }

        return throttledUntil == null
                ? new Decision.Pass()
                : new Decision.Throttled(new Penalty(client, throttledUntil));
    }

}
