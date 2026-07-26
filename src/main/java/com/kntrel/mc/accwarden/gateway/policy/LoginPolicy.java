package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.LoginRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LoginPolicy implements Policy<LoginRequest, LoginBucket, LoginFinding> {

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
    public List<LoginFinding> evaluate(LoginRequest request, LoginBucket bucket) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(bucket, "bucket");

        Bucket.Snapshot loginSnapshot = bucket.snapshot(request);
        Bucket.Snapshot failureSnapshot = bucket.failedLoginSnapshot(request.network());
        List<LoginFinding> findings = new ArrayList<>(3);
        if (loginSnapshot.isFull()) {
            findings.add(new LoginFinding.BucketCapacityReached(
                    loginSnapshot.globalCount(),
                    loginSnapshot.maxRecords(),
                    loginSnapshot.nextGlobalExpiration().orElseThrow()
            ));
        }
        if (loginSnapshot.subjectCount() >= this.perLoginLimit_) {
            findings.add(new LoginFinding.AccountClientLimitReached(
                    loginSnapshot.subjectCount(),
                    this.perLoginLimit_,
                    loginSnapshot.nextSubjectExpiration().orElseThrow()
            ));
        }
        if (failureSnapshot.subjectCount() >= this.failedLoginsPerClientLimit_) {
            findings.add(new LoginFinding.FailedLoginLimitReached(
                    failureSnapshot.subjectCount(),
                    this.failedLoginsPerClientLimit_,
                    failureSnapshot.nextSubjectExpiration().orElseThrow()
            ));
        }

        return List.copyOf(findings);
    }
}
