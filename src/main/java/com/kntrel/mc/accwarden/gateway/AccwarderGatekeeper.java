package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.*;

import javax.annotation.Nullable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Owns the moving-window state and applies the injected policies to it.
 * Only permitted connection and login attempts are recorded.
 */
public final class AccwarderGatekeeper {

    // FIELDS
    private final AccountPolicy accountPolicy_;
    private final ClientPolicy clientPolicy_;
    private final LoginPolicy loginPolicy_;
    private final AccountBucket accountBucket_;
    private final ClientBucket clientBucket_;
    private final LoginBucket loginBucket_;
    private final Clock clock_;
    private final Map<NetworkKey, Decision.Throttled> throttlesByClient_ = new HashMap<>();
    private final PriorityQueue<Decision.Throttled> throttlesByExpiration_ = new PriorityQueue<>(
            Comparator.comparing(throttled -> throttled.penalty().until())
    );


    // CONSTRUCTORS
    public AccwarderGatekeeper(
            AccountPolicy accountPolicy,
            ClientPolicy clientPolicy,
            LoginPolicy loginPolicy
    ) {
        this(accountPolicy, clientPolicy, loginPolicy, Clock.systemUTC());
    }
    public AccwarderGatekeeper(
            AccountPolicy accountPolicy,
            ClientPolicy clientPolicy,
            LoginPolicy loginPolicy,
            Clock clock
    ) {
        this.accountPolicy_ = Objects.requireNonNull(accountPolicy, "accountPolicy");
        this.clientPolicy_ = Objects.requireNonNull(clientPolicy, "clientPolicy");
        this.loginPolicy_ = Objects.requireNonNull(loginPolicy, "loginPolicy");
        this.clock_ = Objects.requireNonNull(clock, "clock");
        this.accountBucket_ = this.accountPolicy_.newBucket(this.clock_);
        this.clientBucket_ = this.clientPolicy_.newBucket(this.clock_);
        this.loginBucket_ = this.loginPolicy_.newBucket(this.clock_);
    }


    // CONTRACT
    public synchronized Decision considerConnection(NetworkKey client) {
        Objects.requireNonNull(client, "client");
        Optional<Decision.Throttled> activeThrottle = this.activeThrottle_(client);
        if (activeThrottle.isPresent()) {
            return activeThrottle.orElseThrow();
        }

        List<Finding> findings = new ArrayList<>();
        addCapacityFinding_(findings, this.clientBucket_.snapshot(client));
        findings.addAll(this.clientPolicy_.evaluate(client, this.clientBucket_));
        Decision decision = decide_(client, findings);
        if (decision instanceof Decision.Pass) {
            this.clientBucket_.record(client);
        }
        this.remember_(decision);
        return decision;
    }

    public synchronized Decision considerLogin(LoginRequest request) {
        Decision decision = this.evaluateLogin_(Objects.requireNonNull(request, "request"));
        if (decision instanceof Decision.Pass) {
            this.loginBucket_.record(request);
            this.accountBucket_.record(request);
        }
        return decision;
    }
    public synchronized Decision recordFailedLogin(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.loginBucket_.isFull()) {
            this.loginBucket_.recordFailedLogin(request);
        }

        List<Finding> findings = new ArrayList<>();
        this.activeThrottle_(request.network())
                .ifPresent(throttled -> findings.addAll(throttled.findings()));
        addCapacityFinding_(findings, this.loginBucket_.snapshot(request));
        addCapacityFinding_(findings, this.accountBucket_.snapshot(request));
        findings.addAll(this.loginPolicy_.evaluate(request, this.loginBucket_));
        findings.addAll(this.accountPolicy_.evaluate(request, this.accountBucket_));
        Decision decision = decide_(
                request.network(),
                findings
        );
        this.remember_(decision);
        return decision;
    }


    // HELPERS
    private Decision evaluateLogin_(LoginRequest request) {
        Optional<Decision.Throttled> activeThrottle = this.activeThrottle_(request.network());
        if (activeThrottle.isPresent()) {
            return activeThrottle.orElseThrow();
        }

        List<Finding> findings = new ArrayList<>();
        addCapacityFinding_(findings, this.loginBucket_.snapshot(request));
        addCapacityFinding_(findings, this.accountBucket_.snapshot(request));
        findings.addAll(this.loginPolicy_.evaluate(request, this.loginBucket_));
        findings.addAll(this.accountPolicy_.evaluate(request, this.accountBucket_));
        return decide_(request.network(), findings);
    }

    private Optional<Decision.Throttled> activeThrottle_(NetworkKey client) {
        this.discardExpiredThrottles_();
        Decision.Throttled throttle = this.throttlesByClient_.get(client);
        return Optional.ofNullable(throttle);
    }

    private void remember_(Decision decision) {
        if (!(decision instanceof Decision.Throttled throttled)) {
            return;
        }

        NetworkKey client = throttled.penalty().client();
        Decision.Throttled current = this.throttlesByClient_.get(client);
        if (!throttled.equals(current)) {
            this.throttlesByClient_.put(client, throttled);
            this.throttlesByExpiration_.add(throttled);
        }
    }

    private void discardExpiredThrottles_() {
        Instant now = this.clock_.instant();
        while (!this.throttlesByExpiration_.isEmpty()
                && !this.throttlesByExpiration_.peek().penalty().until().isAfter(now)) {
            Decision.Throttled expired = this.throttlesByExpiration_.remove();
            this.throttlesByClient_.computeIfPresent(
                    expired.penalty().client(),
                    (client, current) -> current.equals(expired) ? null : current
            );
        }
    }

    private static void addCapacityFinding_(
            Collection<Finding> findings,
            Bucket.Snapshot snapshot
    ) {
        if (!snapshot.isFull()) {
            return;
        }

        findings.add(new BucketCapacityFinding(
                snapshot.globalCount(),
                snapshot.maxRecords(),
                snapshot.nextGlobalExpiration().orElseThrow()
        ));
    }

    private static Decision decide_(
            NetworkKey client,
            Collection<? extends Finding> findings
    ) {
        Objects.requireNonNull(client, "client");
        List<Finding> collectedFindings = List.copyOf(
                Objects.requireNonNull(findings, "findings")
        );
        if (collectedFindings.isEmpty()) {
            return new Decision.Pass();
        }

        Finding cause = computeCause_(collectedFindings);

        if (cause == null) {
            return new Decision.Pass(collectedFindings);
        }

        return new Decision.Throttled(
                new Penalty(client, cause.retryAt(), cause),
                collectedFindings
        );
    }

    private static @Nullable Finding computeCause_(Collection<? extends Finding> findings) {
        Finding max = null;

        for (Finding f : findings) {
            if (f instanceof MultiClientAccountFinding) {
                continue;
            }
            if (max == null || f.retryAt().isAfter(max.retryAt())) {
                max = f;
            }
        }

        return max;
    }

}
