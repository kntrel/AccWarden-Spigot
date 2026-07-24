package com.kntrel.mc.accwarden.authentication;

import com.kntrel.mc.accwarden.authentication.identity.NetworkKey;
import com.kntrel.mc.accwarden.authentication.policy.AuthenticationPolicyConfig;
import com.kntrel.mc.accwarden.authentication.policy.AuthenticationPolicyEngine;
import com.kntrel.mc.accwarden.authentication.policy.PolicyEvaluation;
import com.kntrel.mc.accwarden.platform.PlatformKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationPolicyEngineTest {

    private static final Instant START = Instant.parse("2026-07-21T12:00:00Z");
    private static final UUID ACCOUNT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final NetworkKey CLIENT_A = new NetworkKey(new byte[] {(byte) 192, 0, 2, 1});
    private static final NetworkKey CLIENT_B = new NetworkKey(new byte[] {(byte) 192, 0, 2, 2});
    private static final NetworkKey CLIENT_C = new NetworkKey(new byte[] {(byte) 192, 0, 2, 3});

    @Test
    void exponentialBackoffIsIsolatedByLogin() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config_(false, true, false), clock);
        LoginAttempt attacker = attempt_(ACCOUNT, CLIENT_A);
        LoginAttempt owner = attempt_(ACCOUNT, CLIENT_B);

        assertFalse(fail_(engine, attacker).isDeferred());
        assertFalse(fail_(engine, attacker).isDeferred());
        PolicyEvaluation thirdFailure = fail_(engine, attacker);

        assertTrue(thirdFailure.isDeferred());
        assertEquals(ThrottleReason.LOGIN_BACKOFF, thirdFailure.reason().orElseThrow());
        assertEquals(2, thirdFailure.retryAfterSeconds(clock.instant()));
        assertInstanceOf(BeginResult.Permitted.class, engine.begin(owner));
    }

    @Test
    void blockedAttemptsDoNotExtendThePenalty() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config_(false, true, false), clock);
        LoginAttempt attempt = attempt_(ACCOUNT, CLIENT_A);

        fail_(engine, attempt);
        fail_(engine, attempt);
        Instant retryAt = fail_(engine, attempt).retryAt().orElseThrow();

        BeginResult.Deferred firstBlocked = assertInstanceOf(BeginResult.Deferred.class, engine.begin(attempt));
        clock.advance(Duration.ofSeconds(1));
        BeginResult.Deferred secondBlocked = assertInstanceOf(BeginResult.Deferred.class, engine.begin(attempt));

        assertEquals(retryAt, firstBlocked.evaluation().retryAt().orElseThrow());
        assertEquals(retryAt, secondBlocked.evaluation().retryAt().orElseThrow());
    }

    @Test
    void successClearsTheMatchingFailureStreak() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config_(false, true, false), clock);
        LoginAttempt attempt = attempt_(ACCOUNT, CLIENT_A);

        fail_(engine, attempt);
        fail_(engine, attempt);
        clock.advance(Duration.ofSeconds(1));
        engine.recordTrustedSuccess(attempt);

        assertFalse(fail_(engine, attempt).isDeferred());
    }

    @Test
    void clientBucketSpansMultipleAccountsButNotOtherClients() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyConfig defaults = AuthenticationPolicyConfig.defaults();
        AuthenticationPolicyConfig config = new AuthenticationPolicyConfig(
                defaults.state(),
                defaults.network(),
                new AuthenticationPolicyConfig.Client(true, 2, 2, Duration.ofMinutes(1), Duration.ofSeconds(10)),
                disabledLogin_(),
                disabledAccount_()
        );
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config, clock);

        succeed_(engine, attempt_(UUID.randomUUID(), CLIENT_A));
        succeed_(engine, attempt_(UUID.randomUUID(), CLIENT_A));

        BeginResult.Deferred limited = assertInstanceOf(
                BeginResult.Deferred.class,
                engine.begin(attempt_(UUID.randomUUID(), CLIENT_A))
        );
        assertEquals(ThrottleReason.CLIENT_RATE_LIMIT, limited.evaluation().reason().orElseThrow());
        assertInstanceOf(
                BeginResult.Permitted.class,
                engine.begin(attempt_(UUID.randomUUID(), CLIENT_B))
        );
    }

    @Test
    void accountDetectionPenalizesOnlyTheCurrentLogin() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyConfig defaults = AuthenticationPolicyConfig.defaults();
        AuthenticationPolicyConfig config = new AuthenticationPolicyConfig(
                defaults.state(),
                defaults.network(),
                disabledClient_(),
                disabledLogin_(),
                new AuthenticationPolicyConfig.Account(
                        true,
                        Duration.ofMinutes(2),
                        3,
                        2,
                        Duration.ofSeconds(30),
                        100,
                        true,
                        true,
                        Duration.ofMinutes(5)
                )
        );
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config, clock);

        fail_(engine, attempt_(ACCOUNT, CLIENT_A));
        fail_(engine, attempt_(ACCOUNT, CLIENT_A));
        PolicyEvaluation detected = fail_(engine, attempt_(ACCOUNT, CLIENT_B));

        assertTrue(detected.isDeferred());
        assertEquals(ThrottleReason.ACCOUNT_ATTACK, detected.reason().orElseThrow());
        assertEquals(2, detected.actions().size());
        assertInstanceOf(BeginResult.Deferred.class, engine.begin(attempt_(ACCOUNT, CLIENT_B)));
        assertInstanceOf(BeginResult.Permitted.class, engine.begin(attempt_(ACCOUNT, CLIENT_C)));
    }

    @Test
    void quietWindowExpiresTheFailureStreak() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config_(false, true, false), clock);
        LoginAttempt attempt = attempt_(ACCOUNT, CLIENT_A);

        fail_(engine, attempt);
        fail_(engine, attempt);
        clock.advance(Duration.ofMinutes(16));

        assertFalse(fail_(engine, attempt).isDeferred());
    }

    @Test
    void undefinedClientSkipsClientAndLoginPolicies() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyConfig defaults = AuthenticationPolicyConfig.defaults();
        AuthenticationPolicyConfig config = new AuthenticationPolicyConfig(
                defaults.state(),
                defaults.network(),
                new AuthenticationPolicyConfig.Client(true, 1, 1, Duration.ofMinutes(1), Duration.ofSeconds(10)),
                new AuthenticationPolicyConfig.Login(
                        true,
                        1,
                        Duration.ofSeconds(1),
                        2.0,
                        Duration.ofSeconds(10),
                        Duration.ofMinutes(1)
                ),
                disabledAccount_()
        );
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config, clock);
        LoginAttempt attempt = attemptWithoutClient_(ACCOUNT);

        assertFalse(fail_(engine, attempt).isDeferred());
        assertFalse(fail_(engine, attempt).isDeferred());
        assertInstanceOf(BeginResult.Permitted.class, engine.begin(attempt));
    }

    @Test
    void undefinedClientStillContributesToAccountDetection() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyConfig defaults = AuthenticationPolicyConfig.defaults();
        AuthenticationPolicyConfig config = new AuthenticationPolicyConfig(
                defaults.state(),
                defaults.network(),
                disabledClient_(),
                disabledLogin_(),
                new AuthenticationPolicyConfig.Account(
                        true,
                        Duration.ofMinutes(2),
                        2,
                        1,
                        Duration.ofSeconds(30),
                        100,
                        true,
                        true,
                        Duration.ofMinutes(5)
                )
        );
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config, clock);

        assertFalse(fail_(engine, attemptWithoutClient_(ACCOUNT)).isDeferred());
        PolicyEvaluation detected = fail_(engine, attempt_(ACCOUNT, CLIENT_A));

        assertTrue(detected.isDeferred());
        assertEquals(ThrottleReason.ACCOUNT_ATTACK, detected.reason().orElseThrow());
        assertEquals(2, detected.actions().size());
    }

    @Test
    void undefinedClientStillUsesGlobalEngineCapacityLimit() {
        MutableClock clock = new MutableClock(START);
        AuthenticationPolicyConfig defaults = AuthenticationPolicyConfig.defaults();
        AuthenticationPolicyConfig.State defaultState = defaults.state();
        AuthenticationPolicyConfig config = new AuthenticationPolicyConfig(
                new AuthenticationPolicyConfig.State(
                        defaultState.maxEntriesPerPolicy(),
                        1,
                        defaultState.retention(),
                        defaultState.overloadCooldown()
                ),
                defaults.network(),
                disabledClient_(),
                disabledLogin_(),
                disabledAccount_()
        );
        AuthenticationPolicyEngine engine = new AuthenticationPolicyEngine(config, clock);

        assertInstanceOf(BeginResult.Permitted.class, engine.begin(attemptWithoutClient_(ACCOUNT)));
        BeginResult.Deferred overloaded = assertInstanceOf(
                BeginResult.Deferred.class,
                engine.begin(attemptWithoutClient_(UUID.randomUUID()))
        );

        assertEquals(ThrottleReason.ENGINE_CAPACITY, overloaded.evaluation().reason().orElseThrow());
    }

    private static PolicyEvaluation fail_(AuthenticationPolicyEngine engine, LoginAttempt attempt) {
        BeginResult.Permitted permitted = assertInstanceOf(BeginResult.Permitted.class, engine.begin(attempt));
        return engine.complete(permitted.ticket(), VerificationOutcome.FAILURE);
    }

    private static void succeed_(AuthenticationPolicyEngine engine, LoginAttempt attempt) {
        BeginResult.Permitted permitted = assertInstanceOf(BeginResult.Permitted.class, engine.begin(attempt));
        engine.complete(permitted.ticket(), VerificationOutcome.SUCCESS);
    }

    private static LoginAttempt attempt_(UUID account, NetworkKey client) {
        return new LoginAttempt(account, client, PlatformKey.JAVA);
    }

    private static LoginAttempt attemptWithoutClient_(UUID account) {
        return new LoginAttempt(account, NetworkKey.undefined(), PlatformKey.JAVA);
    }

    private static AuthenticationPolicyConfig config_(boolean client, boolean login, boolean account) {
        AuthenticationPolicyConfig defaults = AuthenticationPolicyConfig.defaults();
        return new AuthenticationPolicyConfig(
                defaults.state(),
                defaults.network(),
                client ? defaults.client() : disabledClient_(),
                login ? defaults.login() : disabledLogin_(),
                account ? defaults.account() : disabledAccount_()
        );
    }

    private static AuthenticationPolicyConfig.Client disabledClient_() {
        AuthenticationPolicyConfig.Client defaults = AuthenticationPolicyConfig.defaults().client();
        return new AuthenticationPolicyConfig.Client(
                false,
                defaults.capacity(),
                defaults.refillTokens(),
                defaults.refillEvery(),
                defaults.exhaustedCooldown()
        );
    }

    private static AuthenticationPolicyConfig.Login disabledLogin_() {
        AuthenticationPolicyConfig.Login defaults = AuthenticationPolicyConfig.defaults().login();
        return new AuthenticationPolicyConfig.Login(
                false,
                defaults.startsAfter(),
                defaults.initialDelay(),
                defaults.multiplier(),
                defaults.maximumDelay(),
                defaults.resetAfter()
        );
    }

    private static AuthenticationPolicyConfig.Account disabledAccount_() {
        AuthenticationPolicyConfig.Account defaults = AuthenticationPolicyConfig.defaults().account();
        return new AuthenticationPolicyConfig.Account(
                false,
                defaults.window(),
                defaults.failuresAtLeast(),
                defaults.distinctClientsAtLeast(),
                defaults.currentLoginPenalty(),
                defaults.maxTrackedFailures(),
                defaults.log(),
                defaults.notifyAdministrators(),
                defaults.repeatEffectsAfter()
        );
    }
}
