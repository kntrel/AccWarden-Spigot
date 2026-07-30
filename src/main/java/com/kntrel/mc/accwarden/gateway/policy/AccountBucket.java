package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.LoginRequest;
import com.kntrel.mc.accwarden.gateway.NetworkKey;

import java.io.Serial;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AccountBucket extends BucketImpl<LoginRequest, UUID> {

    private final Map<UUID, AccountClients> clientsByAccount_ = new HashMap<>();

    AccountBucket(Duration window, int maxRecords, Clock clock) {
        super(window, maxRecords, clock);
    }

    @Override
    protected UUID key(LoginRequest request) {
        return Objects.requireNonNull(request, "request").accountId();
    }

    public synchronized ClientSnapshot clientSnapshot(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = this.refresh();
        AccountClients accountClients = this.clientsByAccount_.get(request.accountId());
        if (accountClients == null) {
            return new ClientSnapshot(now, 0, false, Optional.empty());
        }

        Instant permitsNewClientAt = accountClients.values().stream()
                .map(Deque::peekLast)
                .filter(Objects::nonNull)
                .map(recordedAt -> recordedAt.plus(this.window()))
                .min(Instant::compareTo)
                .orElseThrow();
        return new ClientSnapshot(
                now,
                accountClients.size(),
                accountClients.containsKey(request.network()),
                Optional.of(permitsNewClientAt)
        );
    }

    @Override
    protected void onRecord(LoginRequest request, UUID account, Instant recordedAt) {
        this.clientsByAccount_
                .computeIfAbsent(account, ignored -> new AccountClients())
                .computeIfAbsent(request.network(), ignored -> new ArrayDeque<>())
                .addLast(recordedAt);
    }

    @Override
    protected void onDiscard(LoginRequest request, UUID account, Instant recordedAt) {
        AccountClients accountClients = this.clientsByAccount_.get(account);
        Deque<Instant> clientEvents = accountClients.get(request.network());
        clientEvents.removeFirst();
        if (clientEvents.isEmpty()) {
            accountClients.remove(request.network());
        }
        if (accountClients.isEmpty()) {
            this.clientsByAccount_.remove(account);
        }
    }

    public record ClientSnapshot(
            Instant observedAt,
            int distinctClientCount,
            boolean containsClient,
            Optional<Instant> permitsNewClientAt
    ) {

        public ClientSnapshot {
            Objects.requireNonNull(observedAt, "observedAt");
            Objects.requireNonNull(permitsNewClientAt, "permitsNewClientAt");
            if (distinctClientCount < 0) {
                throw new IllegalArgumentException("Distinct client count cannot be negative.");
            }
            if (distinctClientCount == 0 && (containsClient || permitsNewClientAt.isPresent())) {
                throw new IllegalArgumentException("An empty account bucket cannot contain a client or expiration.");
            }
        }
    }

    private static final class AccountClients extends HashMap<NetworkKey, Deque<Instant>> {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
