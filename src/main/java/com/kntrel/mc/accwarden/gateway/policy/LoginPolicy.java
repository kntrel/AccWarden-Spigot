package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.LoginRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LoginPolicy implements Policy<LoginRequest, LoginBucket, LoginFinding> {

    private final Duration window_, penalty_;
    private final int maxRecords_, attemptLimit_;
    private final boolean attemptLimitEnabled_;

    public LoginPolicy(
            Duration window,
            int maxRecords,
            boolean attemptLimitEnabled,
            int attemptLimit,
            Duration penalty
    ) {
        this.window_ = PolicySupport.requirePositiveWindow(window);
        this.maxRecords_ = PolicySupport.requirePositiveLimit(maxRecords, "maxRecords");
        this.attemptLimitEnabled_ = attemptLimitEnabled;
        this.attemptLimit_ = PolicySupport.requirePositiveLimit(attemptLimit, "attemptLimit");
        this.penalty_ = PolicySupport.requirePositiveDuration(penalty, "penalty");
    }

    @Override
    public LoginBucket newBucket(Clock clock) {
        return new LoginBucket(this.window_, this.maxRecords_, clock);
    }

    @Override
    public List<LoginFinding> evaluate(LoginRequest request, LoginBucket bucket) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(bucket, "bucket");

        if (!this.attemptLimitEnabled_) {
            return List.of();
        }

        Bucket.Snapshot loginSnapshot = bucket.snapshot(request);
        Bucket.Snapshot failureSnapshot = bucket.failedLoginSnapshot(request.network());
        List<LoginFinding> findings = new ArrayList<>(2);
        if (loginSnapshot.subjectCount() >= this.attemptLimit_) {
            findings.add(new LoginFinding(
                    loginSnapshot.subjectCount(),
                    this.attemptLimit_,
                    loginSnapshot.observedAt().plus(this.penalty_),
                    LoginFinding.Threshold.ACCOUNT_CLIENT_ATTEMPTS
            ));
        }

        if (failureSnapshot.subjectCount() >= this.attemptLimit_) {
            findings.add(new LoginFinding(
                    failureSnapshot.subjectCount(),
                    this.attemptLimit_,
                    failureSnapshot.observedAt().plus(this.penalty_),
                    LoginFinding.Threshold.CLIENT_FAILED_LOGINS
            ));
        }

        return List.copyOf(findings);
    }
}
