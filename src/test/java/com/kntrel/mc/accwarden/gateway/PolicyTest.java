package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.gateway.policy.AccountBucket;
import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.ClientBucket;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.LoginBucket;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyTest {

    private static final Instant START = Instant.parse("2026-07-23T12:00:00Z");
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final NetworkKey CLIENT_A = client_(1);
    private static final NetworkKey CLIENT_B = client_(2);

    @Test
    void loginAttemptLimitIsSpecificToTheAccountAndClientPair() {
        MutableClock clock = new MutableClock(START);
        LoginPolicy policy = new LoginPolicy(WINDOW, 100, 2, 10);
        LoginBucket bucket = policy.newBucket(clock);
        LoginRequest aliceFromA = request_("alice", CLIENT_A);

        bucket.record(aliceFromA);
        bucket.record(aliceFromA);

        assertInstanceOf(Decision.Throttled.class, policy.consider(aliceFromA, bucket));
        assertInstanceOf(Decision.Pass.class, policy.consider(request_("alice", CLIENT_B), bucket));
        assertInstanceOf(Decision.Pass.class, policy.consider(request_("bob", CLIENT_A), bucket));
        assertEquals(2, bucket.snapshot(aliceFromA).globalCount());
    }

    @Test
    void failedLoginLimitSpansAccountsForTheSameClient() {
        MutableClock clock = new MutableClock(START);
        LoginPolicy policy = new LoginPolicy(WINDOW, 100, 10, 2);
        LoginBucket bucket = policy.newBucket(clock);

        bucket.recordFailedLogin(request_("alice", CLIENT_A));
        bucket.recordFailedLogin(request_("bob", CLIENT_A));

        assertInstanceOf(
                Decision.Throttled.class,
                policy.consider(request_("charlie", CLIENT_A), bucket)
        );
        assertInstanceOf(
                Decision.Pass.class,
                policy.consider(request_("charlie", CLIENT_B), bucket)
        );
    }

    @Test
    void clientPolicyRejectsEveryClientWhenItsBucketIsFull() {
        MutableClock clock = new MutableClock(START);
        ClientPolicy policy = new ClientPolicy(WINDOW, 2, 10, 10);
        ClientBucket bucket = policy.newBucket(clock);
        bucket.record(CLIENT_A);
        bucket.record(CLIENT_B);

        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                policy.consider(client_(3), bucket)
        );

        assertEquals(START.plus(WINDOW), decision.penalty().until());
    }

    @Test
    void loginPolicyUsesOneCapacityForAttemptsAndFailures() {
        MutableClock clock = new MutableClock(START);
        LoginPolicy policy = new LoginPolicy(WINDOW, 2, 10, 10);
        LoginBucket bucket = policy.newBucket(clock);
        bucket.record(request_("alice", CLIENT_A));
        bucket.recordFailedLogin(request_("bob", CLIENT_B));

        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                policy.consider(request_("charlie", client_(3)), bucket)
        );

        assertEquals(2, bucket.size());
        assertEquals(START.plus(WINDOW), decision.penalty().until());
    }

    @Test
    void accountPolicyRejectsUnrelatedAccountsWhenItsBucketIsFull() {
        MutableClock clock = new MutableClock(START);
        AccountPolicy policy = new AccountPolicy(WINDOW, 1, 10);
        AccountBucket bucket = policy.newBucket(clock);
        bucket.record(request_("alice", CLIENT_A));

        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                policy.consider(request_("bob", CLIENT_B), bucket)
        );

        assertEquals(START.plus(WINDOW), decision.penalty().until());
    }

    @Test
    void policyCreatesConfiguredButIndependentBuckets() {
        MutableClock clock = new MutableClock(START);
        ClientPolicy policy = new ClientPolicy(WINDOW, 2, 10, 10);
        ClientBucket first = policy.newBucket(clock);
        ClientBucket second = policy.newBucket(clock);

        first.record(CLIENT_A);

        assertEquals(WINDOW, first.window());
        assertEquals(2, first.maxRecords());
        assertEquals(1, first.size());
        assertEquals(0, second.size());
    }

    @Test
    void loginPoliciesRequireAnAccountUuid() {
        MutableClock clock = new MutableClock(START);
        LoginPolicy policy = new LoginPolicy(WINDOW, 10, 10, 10);
        LoginBucket bucket = policy.newBucket(clock);
        LoginRequest request = new LoginRequest(new UnidentifiedAccount("alice"), CLIENT_A);

        assertThrows(IllegalArgumentException.class, () -> policy.consider(request, bucket));
    }

    private static LoginRequest request_(String account, NetworkKey client) {
        return new LoginRequest(new TestAccount(account), client);
    }

    private static NetworkKey client_(int lastByte) {
        return new NetworkKey(new byte[] {(byte) 192, 0, 2, (byte) lastByte});
    }

    private static final class TestAccount extends Account {

        private TestAccount(String name) {
            super(name);
            this.setUuid(UUID.nameUUIDFromBytes(
                    name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
            ));
        }
    }

    private static final class UnidentifiedAccount extends Account {

        private UnidentifiedAccount(String name) {
            super(name);
        }
    }
}
