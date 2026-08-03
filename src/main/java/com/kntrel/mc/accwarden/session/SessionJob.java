package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.Platform;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Describes the lifecycle work surrounding one attempt to acquire a player session.
 *
 * <p>The callbacks are invoked synchronously in this order:</p>
 * <ol>
 *     <li>{@link #onUncached(Uncached)} when no cached session can be claimed.</li>
 *     <li>If an interactive flow can be started, either
 *     {@link #beforeAuthentication(Authentication)} or
 *     {@link #beforeRegistration(Registration)} immediately before it.</li>
 *     <li>{@link #onComplete(Completion)} exactly once when the flow produces a result.</li>
 * </ol>
 *
 * <p>A cached session skips the first two callbacks and proceeds directly to completion.</p>
 */
public interface SessionJob {

    Player player();

    void onUncached(Uncached uncached);

    void beforeAuthentication(Authentication authentication);

    void beforeRegistration(Registration registration);

    void onComplete(Completion completion);

    static SessionJobBuilder forPlayer(Player player) {
        return new SessionJobBuilder(player);
    }

    record Uncached(Player player, Platform platform) {

        public Uncached {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(platform, "platform");
        }
    }

    record Authentication(Player player, Platform platform, Account account) {

        public Authentication {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(platform, "platform");
            Objects.requireNonNull(account, "account");
        }
    }

    record Registration(Player player, Platform platform) {

        public Registration {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(platform, "platform");
        }
    }

    record Completion(Player player, SessionResult result) {

        public Completion {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(result, "result");
        }
    }

}
