package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));
        Decision.Throttled global = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_C)
        );
        assertEquals(START.plus(WINDOW), global.penalty().until());

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

        assertInstanceOf(Decision.Throttled.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Throttled.class, gatekeeper.considerLogin(request));
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
        assertInstanceOf(Decision.Throttled.class, gatekeeper.recordFailedLogin(request));
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
