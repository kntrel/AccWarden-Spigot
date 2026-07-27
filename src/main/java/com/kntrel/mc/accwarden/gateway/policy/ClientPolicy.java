package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.NetworkKey;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClientPolicy implements Policy<NetworkKey, ClientBucket, ClientFinding> {

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
    public List<ClientFinding> evaluate(NetworkKey client, ClientBucket bucket) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(bucket, "bucket");

        Bucket.Snapshot snapshot = bucket.snapshot(client);
        List<ClientFinding> findings = new ArrayList<>(2);
        if (snapshot.subjectCount() >= this.perClientLimit_) {
            findings.add(new ClientFinding(
                    snapshot.subjectCount(),
                    this.perClientLimit_,
                    snapshot.nextSubjectExpiration().orElseThrow(),
                    ClientFinding.Threshold.CLIENT_CONNECTIONS
            ));
        }
        if (snapshot.globalCount() >= this.globalLimit_) {
            findings.add(new ClientFinding(
                    snapshot.globalCount(),
                    this.globalLimit_,
                    snapshot.nextGlobalExpiration().orElseThrow(),
                    ClientFinding.Threshold.GLOBAL_CONNECTIONS
            ));
        }

        return List.copyOf(findings);
    }

}
