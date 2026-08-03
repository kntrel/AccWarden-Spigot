package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.BucketCapacityFinding;
import com.kntrel.mc.accwarden.gateway.policy.ClientFinding;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.Finding;
import com.kntrel.mc.accwarden.gateway.policy.LoginFinding;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;
import com.kntrel.mc.accwarden.gateway.policy.MultiClientAccountFinding;
import org.junit.jupiter.api.BeforeEach;
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

    private MutableClock clock_;

    @BeforeEach
    void setUp() {
        this.clock_ = new MutableClock(START);
    }

    @Test
    void clientPolicyLimitsIndividualClientsAndBucketCapacityLimitsGlobally() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(2, 3, 10, 10);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));

        Decision.Throttled individual = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        assertEquals(START.plus(WINDOW), individual.penalty().until());
        ClientFinding individualCause = assertInstanceOf(
                ClientFinding.class,
                individual.penalty().cause()
        );
        assertEquals(ClientFinding.Threshold.CLIENT_CONNECTIONS, individualCause.threshold());

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));
        Decision.Throttled global = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_C)
        );
        BucketCapacityFinding globalCause = assertInstanceOf(
                BucketCapacityFinding.class,
                global.penalty().cause()
        );
        assertEquals(3, globalCause.limit());

        this.clock_.advance(WINDOW);
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
    }

    @Test
    void clientConnectionThrottleAffectsLaterLoginFromTheSameClient() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(1, 100, 10, 10);
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        Decision.Throttled connectionThrottle = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        Decision.Throttled loginThrottle = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerLogin(request)
        );

        assertEquals(connectionThrottle, loginThrottle);
        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("bob"), CLIENT_B))
        );
    }

    @Test
    void failedLoginDecisionAffectsLaterConnectionsAndLogins() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(10, 100, 2, 10);
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.recordFailedLogin(request)
        );
        Decision.Throttled failed = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.recordFailedLogin(request)
        );

        LoginFinding failedCause = assertInstanceOf(
                LoginFinding.class,
                failed.penalty().cause()
        );
        assertEquals(LoginFinding.Threshold.CLIENT_FAILED_LOGINS, failedCause.threshold());
        assertEquals(CLIENT_A, failed.penalty().client());
        assertEquals(START.plus(WINDOW), failed.penalty().until());
        assertEquals(
                failed,
                gatekeeper.considerConnection(CLIENT_A)
        );
        assertEquals(
                failed,
                gatekeeper.considerLogin(request)
        );

        this.clock_.advance(WINDOW);
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
    }

    @Test
    void accountPolicyReportsWithoutThrottling() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(10, 100, 10, 1);

        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("Alice"), CLIENT_A))
        );
        Decision.Pass multipleClients = assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("alice"), CLIENT_B))
        );
        MultiClientAccountFinding finding = assertInstanceOf(
                MultiClientAccountFinding.class,
                multipleClients.findings().getFirst()
        );

        assertEquals(1, finding.count());
        assertEquals(1, finding.limit());
    }

    @Test
    void failedLoginsBelowTheLimitDoNotProduceFindings() {
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, 100, 10),
                new ClientPolicy(WINDOW, 100, true, 100, WINDOW),
                new LoginPolicy(WINDOW, Integer.MAX_VALUE, true, 5, WINDOW),
                this.clock_
        );
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.recordFailedLogin(request)
        );
        Decision.Pass decision = assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.recordFailedLogin(request)
        );

        assertEquals(List.of(), decision.findings());
    }

    @Test
    void blockedCallsDoNotExtendTheirPenalty() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(1, 100, 10, 10);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        Decision.Throttled first = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        this.clock_.advance(Duration.ofSeconds(3));
        Decision.Throttled second = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );

        assertEquals(first.penalty().until(), second.penalty().until());
    }

    @Test
    void configuredClientPenaltyControlsThrottleExpiration() {
        Duration penalty = Duration.ofSeconds(3);
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, 100, 10),
                new ClientPolicy(WINDOW, 100, true, 1, penalty),
                new LoginPolicy(WINDOW, Integer.MAX_VALUE, true, 100, WINDOW),
                this.clock_
        );

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        Decision.Throttled throttled = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );

        assertEquals(START.plus(penalty), throttled.penalty().until());
    }

    @Test
    void configuredLoginPenaltyControlsThrottleExpiration() {
        Duration penalty = Duration.ofSeconds(4);
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, 100, 10),
                new ClientPolicy(WINDOW, 100, true, 100, WINDOW),
                new LoginPolicy(WINDOW, Integer.MAX_VALUE, true, 1, penalty),
                this.clock_
        );
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
        Decision.Throttled throttled = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerLogin(request)
        );

        assertEquals(START.plus(penalty), throttled.penalty().until());
    }

    @Test
    void disabledClientAttemptLimitStillThrottlesAtBucketCapacity() {
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, 2, 10),
                new ClientPolicy(WINDOW, 2, false, 1, WINDOW),
                new LoginPolicy(WINDOW, Integer.MAX_VALUE, false, 1, WINDOW),
                this.clock_
        );

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        Decision.Throttled throttled = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );

        assertInstanceOf(BucketCapacityFinding.class, throttled.penalty().cause());
    }

    @Test
    void disabledLoginAttemptLimitDoesNotThrottleAtTheClientCapacity() {
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, 2, 10),
                new ClientPolicy(WINDOW, 2, false, 1, WINDOW),
                new LoginPolicy(WINDOW, Integer.MAX_VALUE, false, 1, WINDOW),
                this.clock_
        );
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.recordFailedLogin(request));
        assertInstanceOf(Decision.Pass.class, gatekeeper.recordFailedLogin(request));
        assertInstanceOf(Decision.Pass.class, gatekeeper.recordFailedLogin(request));
    }

    @Test
    void sharedBucketCapacityIsACommonGatewayFinding() {
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, 1, 10),
                new ClientPolicy(WINDOW, 1, true, 10, WINDOW),
                new LoginPolicy(WINDOW, Integer.MAX_VALUE, true, 10, WINDOW),
                this.clock_
        );

        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("alice"), CLIENT_A))
        );
        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerLogin(new LoginRequest(account_("bob"), CLIENT_B))
        );
        BucketCapacityFinding finding = assertInstanceOf(
                BucketCapacityFinding.class,
                decision.penalty().cause()
        );

        assertEquals(1, finding.count());
        assertEquals(1, finding.limit());
        assertEquals(START.plus(WINDOW), finding.retryAt());
    }

    @Test
    void failedLoginStillHonorsAFullAccountBucket() {
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, 1, 10),
                new ClientPolicy(WINDOW, 1, true, 10, WINDOW),
                new LoginPolicy(WINDOW, Integer.MAX_VALUE, true, 10, WINDOW),
                this.clock_
        );
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.recordFailedLogin(request)
        );
        BucketCapacityFinding finding = assertInstanceOf(
                BucketCapacityFinding.class,
                decision.findings().getFirst()
        );
        assertEquals(1, finding.count());
        assertEquals(1, finding.limit());
    }

    @Test
    void passDecisionCanRetainFindings() {
        Finding finding = () -> START.plus(WINDOW);

        Decision.Pass decision = new Decision.Pass(List.of(finding));

        assertEquals(List.of(finding), decision.findings());
    }

    private AccwarderGatekeeper gatekeeper_(
            int clientLimit,
            int clientCapacity,
            int loginLimit,
            int distinctClientLimit
    ) {
        return new AccwarderGatekeeper(
                new AccountPolicy(WINDOW, clientCapacity, distinctClientLimit),
                new ClientPolicy(WINDOW, clientCapacity, true, clientLimit, WINDOW),
                new LoginPolicy(
                        WINDOW,
                        Integer.MAX_VALUE,
                        true,
                        loginLimit,
                        WINDOW
                ),
                this.clock_
        );
    }

    private static UUID account_(String name) {
        return UUID.nameUUIDFromBytes(
                name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static NetworkKey client_(int lastByte) {
        return new NetworkKey(new byte[] {(byte) 192, 0, 2, (byte) lastByte});
    }
}
