package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.gateway.policy.AccountFinding;
import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.ClientFinding;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.Finding;
import com.kntrel.mc.accwarden.gateway.policy.LoginFinding;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AccwarderGatekeeperTest {

    private static final Instant START = Instant.parse("2026-07-23T12:00:00Z");
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final NetworkKey CLIENT_A = client_(1);
    private static final NetworkKey CLIENT_B = client_(2);
    private static final NetworkKey CLIENT_C = client_(3);

    @Test
    void clientPolicyLimitsIndividualClientsAndTheGlobalWindow() {
        MutableClock clock = new MutableClock(START);
        AccwarderGatekeeper gatekeeper = gatekeeper_(clock, 2, 3, 10, 10, 10);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));

        Decision.Throttled individual = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        assertEquals(START.plus(WINDOW), individual.penalty().until());
        ClientFinding.ClientLimitReached individualCause = assertInstanceOf(
                ClientFinding.ClientLimitReached.class,
                individual.findings().getFirst()
        );
        assertEquals(individualCause, individual.penalty().cause());

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));
        Decision.Throttled global = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_C)
        );
        assertEquals(START.plus(WINDOW), global.penalty().until());
        ClientFinding.GlobalLimitReached globalCause = assertInstanceOf(
                ClientFinding.GlobalLimitReached.class,
                global.findings().getFirst()
        );
        assertEquals(globalCause, global.penalty().cause());

        clock.advance(WINDOW);
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
    }

    @Test
    void failedLoginDecisionAffectsLaterConnectionsAndLogins() {
        MutableClock clock = new MutableClock(START);
        AccwarderGatekeeper gatekeeper = gatekeeper_(clock, 10, 100, 10, 2, 10);
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
        assertInstanceOf(Decision.Pass.class, gatekeeper.recordFailedLogin(request));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));

        Decision.Throttled failed = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.recordFailedLogin(request)
        );
        assertEquals(CLIENT_A, failed.penalty().client());
        assertEquals(START.plus(WINDOW), failed.penalty().until());
        LoginFinding.FailedLoginLimitReached failedCause = assertInstanceOf(
                LoginFinding.FailedLoginLimitReached.class,
                failed.findings().getFirst()
        );
        assertEquals(failedCause, failed.penalty().cause());

        Decision.Throttled blockedConnection = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        Decision.Throttled blockedLogin = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerLogin(request)
        );
        assertEquals(failed.findings(), blockedConnection.findings());
        assertEquals(failed.findings(), blockedLogin.findings());
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));

        clock.advance(WINDOW);
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
    }

    @Test
    void accountPolicyRecognizesTheSameAccountAcrossObjectInstances() {
        MutableClock clock = new MutableClock(START);
        AccwarderGatekeeper gatekeeper = gatekeeper_(clock, 10, 100, 10, 10, 1);

        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("Alice"), CLIENT_A))
        );
        Decision.Throttled multipleClients = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerLogin(new LoginRequest(account_("alice"), CLIENT_B))
        );
        assertEquals(CLIENT_B, multipleClients.penalty().client());
        assertEquals(START.plus(WINDOW), multipleClients.penalty().until());
        assertInstanceOf(
                AccountFinding.DistinctClientLimitReached.class,
                multipleClients.findings().getFirst()
        );

        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("alice"), CLIENT_A))
        );

        clock.advance(WINDOW);
        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("alice"), CLIENT_B))
        );
    }

    @Test
    void blockedCallsDoNotExtendTheirPenalty() {
        MutableClock clock = new MutableClock(START);
        AccwarderGatekeeper gatekeeper = gatekeeper_(clock, 1, 100, 10, 10, 10);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        Decision.Throttled first = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        clock.advance(Duration.ofSeconds(3));
        Decision.Throttled second = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );

        assertEquals(first.penalty().until(), second.penalty().until());
    }

    @Test
    void decisionKeepsAllCausesAndUsesTheLatestRetryTime() {
        MutableClock clock = new MutableClock(START);
        AccwarderGatekeeper gatekeeper = gatekeeper_(clock, 2, 3, 10, 10, 10);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        clock.advance(Duration.ofSeconds(2));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));

        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_B)
        );
        ClientFinding.ClientLimitReached clientLimit = cause_(
                decision.findings(),
                ClientFinding.ClientLimitReached.class
        );
        ClientFinding.GlobalLimitReached globalLimit = cause_(
                decision.findings(),
                ClientFinding.GlobalLimitReached.class
        );

        assertEquals(2, decision.findings().size());
        assertEquals(START.plusSeconds(12), clientLimit.retryAt());
        assertEquals(START.plusSeconds(10), globalLimit.retryAt());
        assertEquals(clientLimit.retryAt(), decision.penalty().until());
        assertEquals(clientLimit, decision.penalty().cause());
    }

    @Test
    void failedLoginDoesNotOverflowAFullLoginBucket() {
        MutableClock clock = new MutableClock(START);
        AccountPolicy accountPolicy = new AccountPolicy(WINDOW, 10, 10);
        ClientPolicy clientPolicy = new ClientPolicy(WINDOW, 10, 10, 100);
        LoginPolicy loginPolicy = new LoginPolicy(WINDOW, 1, 10, 10);
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                accountPolicy,
                clientPolicy,
                loginPolicy,
                clock
        );
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.recordFailedLogin(request)
        );
        assertInstanceOf(
                LoginFinding.BucketCapacityReached.class,
                decision.findings().getFirst()
        );
    }

    @Test
    void passDecisionCanRetainFindings() {
        Finding finding = () -> START.plus(WINDOW);

        Decision.Pass decision = new Decision.Pass(List.of(finding));

        assertEquals(List.of(finding), decision.findings());
    }

    private static <F extends Finding> F cause_(
            List<Finding> findings,
            Class<F> type
    ) {
        return findings.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    private static AccwarderGatekeeper gatekeeper_(
            MutableClock clock,
            int perClientLimit,
            int globalLimit,
            int perLoginLimit,
            int failedLoginLimit,
            int distinctClientLimit
    ) {
        AccountPolicy accountPolicy = new AccountPolicy(WINDOW, 1_000, distinctClientLimit);
        ClientPolicy clientPolicy = new ClientPolicy(WINDOW, 1_000, perClientLimit, globalLimit);
        LoginPolicy loginPolicy = new LoginPolicy(WINDOW, 1_000, perLoginLimit, failedLoginLimit);
        return new AccwarderGatekeeper(
                accountPolicy,
                clientPolicy,
                loginPolicy,
                clock
        );
    }

    private static Account account_(String name) {
        return new TestAccount(name);
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
}
