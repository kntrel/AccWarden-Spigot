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
    void clientPolicyLimitsIndividualClientsAndTheGlobalWindow() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(2, 3, 10, 10, 10);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));

        Decision.Throttled individual = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        assertEquals(START.plus(WINDOW), individual.penalty().until());
        ClientFinding individualCause = assertInstanceOf(
                ClientFinding.class,
                individual.findings().getFirst()
        );
        assertEquals(ClientFinding.Threshold.CLIENT_CONNECTIONS, individualCause.threshold());
        assertEquals(individualCause, individual.penalty().cause());

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));
        Decision.Throttled global = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_C)
        );
        assertEquals(START.plus(WINDOW), global.penalty().until());
        ClientFinding globalCause = assertInstanceOf(
                ClientFinding.class,
                global.findings().getFirst()
        );
        assertEquals(ClientFinding.Threshold.GLOBAL_CONNECTIONS, globalCause.threshold());
        assertEquals(globalCause, global.penalty().cause());

        this.clock_.advance(WINDOW);
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
    }

    @Test
    void clientConnectionThrottleAffectsLaterLoginFromTheSameClient() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(1, 100, 10, 10, 10);
        LoginRequest request = new LoginRequest(account_("alice"), CLIENT_A);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        Decision.Throttled connectionThrottle = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_A)
        );
        ClientFinding cause = assertInstanceOf(
                ClientFinding.class,
                connectionThrottle.penalty().cause()
        );
        assertEquals(ClientFinding.Threshold.CLIENT_CONNECTIONS, cause.threshold());

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
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(10, 100, 10, 2, 10);
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
        LoginFinding failedCause = assertInstanceOf(
                LoginFinding.class,
                failed.findings().getFirst()
        );
        assertEquals(LoginFinding.Threshold.CLIENT_FAILED_LOGINS, failedCause.threshold());
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

        this.clock_.advance(WINDOW);
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerLogin(request));
    }

    @Test
    void accountPolicyRecognizesTheSameAccountId() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(10, 100, 10, 10, 1);

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
        MultiClientAccountFinding finding = assertInstanceOf(
                MultiClientAccountFinding.class,
                multipleClients.findings().getFirst()
        );
        assertEquals(1, finding.count());
        assertEquals(1, finding.limit());

        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("alice"), CLIENT_A))
        );

        this.clock_.advance(WINDOW);
        assertInstanceOf(
                Decision.Pass.class,
                gatekeeper.considerLogin(new LoginRequest(account_("alice"), CLIENT_B))
        );
    }

    @Test
    void blockedCallsDoNotExtendTheirPenalty() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(1, 100, 10, 10, 10);

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
    void decisionKeepsAllFindingsAndUsesTheLatestRetryTime() {
        AccwarderGatekeeper gatekeeper = this.gatekeeper_(2, 3, 10, 10, 10);

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        this.clock_.advance(Duration.ofSeconds(2));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));

        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_B)
        );
        ClientFinding clientLimit = clientFinding_(
                decision.findings(),
                ClientFinding.Threshold.CLIENT_CONNECTIONS
        );
        ClientFinding globalLimit = clientFinding_(
                decision.findings(),
                ClientFinding.Threshold.GLOBAL_CONNECTIONS
        );

        assertEquals(2, decision.findings().size());
        assertEquals(START.plusSeconds(12), clientLimit.retryAt());
        assertEquals(START.plusSeconds(10), globalLimit.retryAt());
        assertEquals(clientLimit.retryAt(), decision.penalty().until());
        assertEquals(clientLimit, decision.penalty().cause());
    }

    @Test
    void clientBucketCapacityIsACommonGatewayFinding() {
        AccountPolicy accountPolicy = new AccountPolicy(WINDOW, 10, 10);
        ClientPolicy clientPolicy = new ClientPolicy(WINDOW, 2, 10, 10);
        LoginPolicy loginPolicy = new LoginPolicy(WINDOW, 10, 10, 10);
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                accountPolicy,
                clientPolicy,
                loginPolicy,
                this.clock_
        );

        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_A));
        assertInstanceOf(Decision.Pass.class, gatekeeper.considerConnection(CLIENT_B));
        Decision.Throttled decision = assertInstanceOf(
                Decision.Throttled.class,
                gatekeeper.considerConnection(CLIENT_C)
        );
        BucketCapacityFinding finding = assertInstanceOf(
                BucketCapacityFinding.class,
                decision.penalty().cause()
        );

        assertEquals(2, finding.count());
        assertEquals(2, finding.limit());
        assertEquals(START.plus(WINDOW), finding.retryAt());
    }

    @Test
    void accountBucketCapacityIsACommonGatewayFinding() {
        AccountPolicy accountPolicy = new AccountPolicy(WINDOW, 1, 10);
        ClientPolicy clientPolicy = new ClientPolicy(WINDOW, 10, 10, 10);
        LoginPolicy loginPolicy = new LoginPolicy(WINDOW, 10, 10, 10);
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                accountPolicy,
                clientPolicy,
                loginPolicy,
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
    void failedLoginDoesNotOverflowAFullLoginBucket() {
        AccountPolicy accountPolicy = new AccountPolicy(WINDOW, 10, 10);
        ClientPolicy clientPolicy = new ClientPolicy(WINDOW, 10, 10, 100);
        LoginPolicy loginPolicy = new LoginPolicy(WINDOW, 1, 10, 10);
        AccwarderGatekeeper gatekeeper = new AccwarderGatekeeper(
                accountPolicy,
                clientPolicy,
                loginPolicy,
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

    private static ClientFinding clientFinding_(
            List<Finding> findings,
            ClientFinding.Threshold threshold
    ) {
        return findings.stream()
                .filter(ClientFinding.class::isInstance)
                .map(ClientFinding.class::cast)
                .filter(finding -> finding.threshold() == threshold)
                .findFirst()
                .orElseThrow();
    }

    private AccwarderGatekeeper gatekeeper_(
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
