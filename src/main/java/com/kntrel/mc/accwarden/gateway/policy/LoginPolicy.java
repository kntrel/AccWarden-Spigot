package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.Decision;
import com.kntrel.mc.accwarden.gateway.LoginRequest;
import com.kntrel.mc.accwarden.gateway.Penalty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class LoginPolicy implements Policy<LoginRequest, LoginBucket> {

    private final Duration window_;
    private final int maxRecords_, perLoginLimit_, failedLoginsPerClientLimit_;

    public LoginPolicy(
            Duration window,
            int maxRecords,
            int perLoginLimit,
            int failedLoginsPerClientLimit
    ) {
        this.window_ = PolicySupport.requirePositiveWindow(window);
        this.maxRecords_ = PolicySupport.requirePositiveLimit(maxRecords, "maxRecords");
        this.perLoginLimit_ = PolicySupport.requirePositiveLimit(perLoginLimit, "perLoginLimit");
        this.failedLoginsPerClientLimit_ = PolicySupport.requirePositiveLimit(
                failedLoginsPerClientLimit,
                "failedLoginsPerClientLimit"
        );
    }

    @Override
    public LoginBucket newBucket(Clock clock) {
        return new LoginBucket(this.window_, this.maxRecords_, clock);
    }

    @Override
    public Decision consider(LoginRequest request, LoginBucket bucket) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(bucket, "bucket");

        Bucket.Snapshot loginSnapshot = bucket.snapshot(request);
        Bucket.Snapshot failureSnapshot = bucket.failedLoginSnapshot(request.network());
        Instant throttledUntil = null;
        if (loginSnapshot.isFull()) {
            throttledUntil = loginSnapshot.nextGlobalExpiration().orElseThrow();
        }
        if (loginSnapshot.subjectCount() >= this.perLoginLimit_) {
            throttledUntil = PolicySupport.later(
                    throttledUntil,
                    loginSnapshot.nextSubjectExpiration().orElseThrow()
            );
        }
        if (failureSnapshot.subjectCount() >= this.failedLoginsPerClientLimit_) {
            Instant failureExpiration = failureSnapshot.nextSubjectExpiration().orElseThrow();
            throttledUntil = PolicySupport.later(throttledUntil, failureExpiration);
        }

        return throttledUntil == null
                ? new Decision.Pass()
                : new Decision.Throttled(new Penalty(request.network(), throttledUntil));
    }
}
