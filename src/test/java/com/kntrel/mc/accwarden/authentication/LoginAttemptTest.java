package com.kntrel.mc.accwarden.authentication;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.authentication.identity.NetworkKey;
import com.kntrel.mc.accwarden.authentication.identity.NetworkKeyResolver;
import com.kntrel.mc.accwarden.form.FormRenderer;
import com.kntrel.mc.accwarden.platform.Platform;
import com.kntrel.mc.accwarden.platform.PlatformKey;
import com.kntrel.mc.accwarden.platform.PlatformViews;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Platform PLATFORM = new Platform() {
        @Override
        public String displayName() {
            return "Java";
        }

        @Override
        public PlatformKey key() {
            return PlatformKey.JAVA;
        }

        @Override
        public FormRenderer formRenderer() {
            return null;
        }

        @Override
        public PlatformViews views() {
            return null;
        }
    };

    @Test
    void leavesClientUndefinedWhenBukkitHasNoAddress() {
        NetworkKeyResolver resolver = new NetworkKeyResolver(32, 64);

        LoginAttempt attempt = LoginAttempt.create(player_(null), PLATFORM, account_(), resolver);

        assertEquals(NetworkKey.undefined(), attempt.client());
        assertTrue(attempt.client().isUndefined());
        assertEquals(0, attempt.client().network().length);
        assertEquals("undefined", attempt.client().toString());
    }

    @Test
    void recoversAnUnresolvedNumericSocketAddress() {
        NetworkKeyResolver resolver = new NetworkKeyResolver(32, 64);
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("192.0.2.10", 25565);

        LoginAttempt attempt = LoginAttempt.create(player_(unresolved), PLATFORM, account_(), resolver);

        assertEquals(resolver.resolve(InetAddress.ofLiteral("192.0.2.10")), attempt.client());
        assertFalse(attempt.client().isUndefined());
    }

    @Test
    void leavesClientUndefinedForAnUnresolvedHostname() {
        NetworkKeyResolver resolver = new NetworkKeyResolver(32, 64);
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("unresolved.invalid", 25565);

        LoginAttempt attempt = LoginAttempt.create(player_(unresolved), PLATFORM, account_(), resolver);

        assertEquals(NetworkKey.undefined(), attempt.client());
    }

    private static Account account_() {
        Account account = new TestAccount();
        account.setUuid(ACCOUNT_ID);
        return account;
    }

    private static Player player_(InetSocketAddress address) {
        return (Player) Proxy.newProxyInstance(
                LoginAttemptTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAddress" -> address;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class TestAccount extends Account {

        private TestAccount() {
            super("test");
        }
    }
}
