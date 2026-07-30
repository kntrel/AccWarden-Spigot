package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.LoginRequest;
import com.kntrel.mc.accwarden.gateway.NetworkKey;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class LoginBucket extends BucketImpl<LoginRequest, LoginBucket.RecordKey> {

    LoginBucket(Duration window, int maxRecords, Clock clock) {
        super(window, maxRecords, clock);
    }

    @Override
    protected RecordKey key(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        return new RecordKey.Attempt(
                request.accountId(),
                request.network()
        );
    }

    public Snapshot failedLoginSnapshot(NetworkKey client) {
        Objects.requireNonNull(client, "client");
        return this.snapshotOf(new RecordKey.Failure(client));
    }

    public void recordFailedLogin(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        this.record_(request, new RecordKey.Failure(request.network()));
    }

    public sealed interface RecordKey {

        record Attempt(UUID accountId, NetworkKey client) implements RecordKey {}

        record Failure(NetworkKey client) implements RecordKey {}
    }
}
