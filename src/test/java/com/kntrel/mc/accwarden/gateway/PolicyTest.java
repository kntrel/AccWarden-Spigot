package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.gateway.policy.AccountBucket;
import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.ClientBucket;
import com.kntrel.mc.accwarden.gateway.policy.ClientFinding;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.LoginBucket;
import com.kntrel.mc.accwarden.gateway.policy.LoginFinding;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;
import com.kntrel.mc.accwarden.gateway.policy.MultiClientAccountFinding;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        LoginFinding finding = assertInstanceOf(
                LoginFinding.class,
                policy.evaluate(aliceFromA, bucket).getFirst()
        );

        assertEquals(LoginFinding.Threshold.ACCOUNT_CLIENT_ATTEMPTS, finding.threshold());
        assertEquals(2, finding.count());
        assertEquals(2, finding.limit());
        assertTrue(policy.evaluate(request_("alice", CLIENT_B), bucket).isEmpty());
        assertTrue(policy.evaluate(request_("bob", CLIENT_A), bucket).isEmpty());
        assertEquals(2, bucket.snapshot(aliceFromA).globalCount());
    }

    @Test
    void failedLoginLimitSpansAccountsForTheSameClient() {
        MutableClock clock = new MutableClock(START);
        LoginPolicy policy = new LoginPolicy(WINDOW, 100, 10, 2);
        LoginBucket bucket = policy.newBucket(clock);

        bucket.recordFailedLogin(request_("alice", CLIENT_A));
        bucket.recordFailedLogin(request_("bob", CLIENT_A));

        LoginFinding finding = assertInstanceOf(
                LoginFinding.class,
                policy.evaluate(request_("charlie", CLIENT_A), bucket).getFirst()
        );

        assertEquals(LoginFinding.Threshold.CLIENT_FAILED_LOGINS, finding.threshold());
        assertEquals(2, finding.count());
        assertEquals(2, finding.limit());
        assertTrue(policy.evaluate(request_("charlie", CLIENT_B), bucket).isEmpty());
    }

    @Test
    void clientPolicyLeavesBucketCapacityToTheGatekeeper() {
        MutableClock clock = new MutableClock(START);
        ClientPolicy policy = new ClientPolicy(WINDOW, 2, 10, 10);
        ClientBucket bucket = policy.newBucket(clock);
        bucket.record(CLIENT_A);
        bucket.record(CLIENT_B);

        assertTrue(policy.evaluate(client_(3), bucket).isEmpty());
    }

    @Test
    void loginBucketUsesOneCapacityForAttemptsAndFailures() {
        MutableClock clock = new MutableClock(START);
        LoginPolicy policy = new LoginPolicy(WINDOW, 2, 10, 10);
        LoginBucket bucket = policy.newBucket(clock);
        bucket.record(request_("alice", CLIENT_A));
        bucket.recordFailedLogin(request_("bob", CLIENT_B));

        assertEquals(2, bucket.size());
        assertTrue(policy.evaluate(request_("charlie", client_(3)), bucket).isEmpty());
    }

    @Test
    void accountPolicyReportsMultipleClientsForTheSameAccount() {
        MutableClock clock = new MutableClock(START);
        AccountPolicy policy = new AccountPolicy(WINDOW, 10, 1);
        AccountBucket bucket = policy.newBucket(clock);
        bucket.record(request_("alice", CLIENT_A));

        MultiClientAccountFinding finding = assertInstanceOf(
                MultiClientAccountFinding.class,
                policy.evaluate(request_("alice", CLIENT_B), bucket).getFirst()
        );

        assertEquals(1, finding.count());
        assertEquals(1, finding.limit());
        assertEquals(START.plus(WINDOW), finding.retryAt());
    }

    @Test
    void clientPolicyReportsEverySimultaneousFindingWithItsOwnRetryTime() {
        MutableClock clock = new MutableClock(START);
        ClientPolicy policy = new ClientPolicy(WINDOW, 10, 2, 3);
        ClientBucket bucket = policy.newBucket(clock);
        bucket.record(CLIENT_A);
        clock.advance(Duration.ofSeconds(2));
        bucket.record(CLIENT_B);
        bucket.record(CLIENT_B);

        List<ClientFinding> findings = policy.evaluate(CLIENT_B, bucket);
        ClientFinding clientLimit = finding_(
                findings,
                ClientFinding.Threshold.CLIENT_CONNECTIONS
        );
        ClientFinding globalLimit = finding_(
                findings,
                ClientFinding.Threshold.GLOBAL_CONNECTIONS
        );

        assertEquals(2, findings.size());
        assertEquals(START.plusSeconds(12), clientLimit.retryAt());
        assertEquals(START.plusSeconds(10), globalLimit.retryAt());
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
        assertTrue(policy.evaluate(CLIENT_A, second).isEmpty());
    }

    @Test
    void loginPoliciesRequireAnAccountUuid() {
        MutableClock clock = new MutableClock(START);
        LoginPolicy policy = new LoginPolicy(WINDOW, 10, 10, 10);
        LoginBucket bucket = policy.newBucket(clock);
        LoginRequest request = new LoginRequest(new UnidentifiedAccount("alice"), CLIENT_A);

        assertThrows(IllegalArgumentException.class, () -> policy.evaluate(request, bucket));
    }

    private static ClientFinding finding_(
            List<ClientFinding> findings,
            ClientFinding.Threshold threshold
    ) {
        return findings.stream()
                .filter(finding -> finding.threshold() == threshold)
                .findFirst()
                .orElseThrow();
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
