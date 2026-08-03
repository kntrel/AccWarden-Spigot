package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionJobTest {

    private final Player player_ = proxy_(Player.class);
    private final Platform platform_ = proxy_(Platform.class);

    @Test
    void builderDispatchesEachConfiguredLifecycleCallback() {
        Account account = new TestAccount();
        SessionResult result = new SessionResult.Unauthenticated();
        List<String> callbacks = new ArrayList<>();

        SessionJob job = SessionJob.forPlayer(this.player_)
                .onUncached(uncached -> {
                    assertSame(this.player_, uncached.player());
                    assertSame(this.platform_, uncached.platform());
                    callbacks.add("uncached");
                })
                .beforeAuthentication(authentication -> {
                    assertSame(this.player_, authentication.player());
                    assertSame(this.platform_, authentication.platform());
                    assertSame(account, authentication.account());
                    callbacks.add("authentication");
                })
                .beforeRegistration(registration -> {
                    assertSame(this.player_, registration.player());
                    assertSame(this.platform_, registration.platform());
                    callbacks.add("registration");
                })
                .onComplete(completion -> {
                    assertSame(this.player_, completion.player());
                    assertSame(result, completion.result());
                    callbacks.add("complete");
                })
                .build();

        job.onUncached(new SessionJob.Uncached(this.player_, this.platform_));
        job.beforeAuthentication(new SessionJob.Authentication(this.player_, this.platform_, account));
        job.beforeRegistration(new SessionJob.Registration(this.player_, this.platform_));
        job.onComplete(new SessionJob.Completion(this.player_, result));

        assertSame(this.player_, job.player());
        assertEquals(List.of("uncached", "authentication", "registration", "complete"), callbacks);
    }

    @Test
    void omittedCallbacksDefaultToNoOperations() {
        SessionJob job = SessionJob.forPlayer(this.player_).build();
        Account account = new TestAccount();
        SessionResult result = new SessionResult.Unauthenticated();

        assertDoesNotThrow(() -> {
            job.onUncached(new SessionJob.Uncached(this.player_, this.platform_));
            job.beforeAuthentication(new SessionJob.Authentication(this.player_, this.platform_, account));
            job.beforeRegistration(new SessionJob.Registration(this.player_, this.platform_));
            job.onComplete(new SessionJob.Completion(this.player_, result));
        });
    }

    @Test
    void builderRejectsNullPlayersAndCallbacks() {
        assertThrows(NullPointerException.class, () -> SessionJob.forPlayer(null));

        SessionJobBuilder builder = SessionJob.forPlayer(this.player_);
        assertThrows(NullPointerException.class, () -> builder.onUncached(null));
        assertThrows(NullPointerException.class, () -> builder.beforeAuthentication(null));
        assertThrows(NullPointerException.class, () -> builder.beforeRegistration(null));
        assertThrows(NullPointerException.class, () -> builder.onComplete(null));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy_(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (_, method, _) -> defaultValue_(method.getReturnType())
        );
    }

    private static Object defaultValue_(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class TestAccount extends Account {

        TestAccount() {
            super("test");
        }
    }
}
