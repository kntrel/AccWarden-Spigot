package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.NetworkKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class ClientBucket extends BucketImpl<NetworkKey, NetworkKey> {

    ClientBucket(Duration window, int maxRecords, Clock clock) {
        super(window, maxRecords, clock);
    }

    @Override
    protected NetworkKey key(NetworkKey client) {
        return Objects.requireNonNull(client, "client");
    }
}
