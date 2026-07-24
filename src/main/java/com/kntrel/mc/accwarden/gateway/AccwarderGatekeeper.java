package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.AccountBucket;
import com.kntrel.mc.accwarden.gateway.policy.ClientBucket;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.LoginBucket;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Owns the moving-window state and applies the injected policies to it.
 * Only permitted connection and login attempts are recorded.
 */
public final class AccwarderGatekeeper {

    private final AccountPolicy accountPolicy_;
    private final ClientPolicy clientPolicy_;
    private final LoginPolicy loginPolicy_;
    private final AccountBucket accountBucket_;
    private final ClientBucket clientBucket_;
    private final LoginBucket loginBucket_;
    private final Clock clock_;
    private final Map<NetworkKey, Penalty> penaltiesByClient_ = new HashMap<>();
    private final PriorityQueue<Penalty> penaltiesByExpiration_ = new PriorityQueue<>(
            Comparator.comparing(Penalty::until)
    );

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

    /**
     * Evaluates and, when permitted, records a connection attempt.
     */
    public synchronized Decision considerConnection(NetworkKey client) {
        Objects.requireNonNull(client, "client");
        Optional<Penalty> activePenalty = this.activePenalty_(client);
        if (activePenalty.isPresent()) {
            return new Decision.Throttled(activePenalty.orElseThrow());
        }

        Decision decision = this.clientPolicy_.consider(client, this.clientBucket_);
        if (decision instanceof Decision.Pass) {
            this.clientBucket_.record(client);
        }
        return decision;
    }

    /**
     * Evaluates and, when permitted, records a login attempt.
     */
    public synchronized Decision considerLogin(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<Penalty> activePenalty = this.activePenalty_(request.network());
        if (activePenalty.isPresent()) {
            return new Decision.Throttled(activePenalty.orElseThrow());
        }

        Decision decision = merge_(
                this.loginPolicy_.consider(request, this.loginBucket_),
                this.accountPolicy_.consider(request, this.accountBucket_)
        );
        if (decision instanceof Decision.Pass) {
            this.loginBucket_.record(request);
            this.accountBucket_.record(request);
        }
        return decision;
    }

    /**
     * Records the failed outcome of a previously permitted login.
     * Any resulting throttle applies to both subsequent connections and logins
     * from the same network key until the penalty expires.
     */
    public synchronized Decision recordFailedLogin(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        if (!this.loginBucket_.isFull()) {
            this.loginBucket_.recordFailedLogin(request);
        }

        Decision decision = merge_(
                this.activePenalty_(request.network())
                        .<Decision>map(Decision.Throttled::new)
                        .orElseGet(Decision.Pass::new),
                this.loginPolicy_.consider(request, this.loginBucket_),
                this.accountPolicy_.consider(request, this.accountBucket_)
        );
        this.remember_(decision);
        return decision;
    }

    private Optional<Penalty> activePenalty_(NetworkKey client) {
        this.discardExpiredPenalties_();
        Penalty penalty = this.penaltiesByClient_.get(client);
        return Optional.ofNullable(penalty);
    }

    private void remember_(Decision decision) {
        if (!(decision instanceof Decision.Throttled throttled)) {
            return;
        }

        Penalty candidate = throttled.penalty();
        Penalty current = this.penaltiesByClient_.get(candidate.client());
        if (current == null || candidate.until().isAfter(current.until())) {
            this.penaltiesByClient_.put(candidate.client(), candidate);
            this.penaltiesByExpiration_.add(candidate);
        }
    }

    private void discardExpiredPenalties_() {
        Instant now = this.clock_.instant();
        while (!this.penaltiesByExpiration_.isEmpty()
                && !this.penaltiesByExpiration_.peek().until().isAfter(now)) {
            Penalty expired = this.penaltiesByExpiration_.remove();
            this.penaltiesByClient_.computeIfPresent(
                    expired.client(),
                    (client, current) -> current.equals(expired) ? null : current
            );
        }
    }

    private static Decision merge_(Decision... decisions) {
        Penalty strictest = null;
        for (Decision decision : decisions) {
            Objects.requireNonNull(decision, "decision");
            if (decision instanceof Decision.Throttled throttled
                    && (strictest == null || throttled.penalty().until().isAfter(strictest.until()))) {
                strictest = throttled.penalty();
            }
        }
        return strictest == null ? new Decision.Pass() : new Decision.Throttled(strictest);
    }

}
