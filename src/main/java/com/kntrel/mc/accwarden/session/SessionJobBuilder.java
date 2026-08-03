package com.kntrel.mc.accwarden.session;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.function.Consumer;

public final class SessionJobBuilder {

    private static final Consumer<SessionJob.Uncached> NO_UNCACHED_ACTION = _ -> {};
    private static final Consumer<SessionJob.Authentication> NO_AUTHENTICATION_ACTION = _ -> {};
    private static final Consumer<SessionJob.Registration> NO_REGISTRATION_ACTION = _ -> {};
    private static final Consumer<SessionJob.Completion> NO_COMPLETION_ACTION = _ -> {};

    private final Player player_;
    private Consumer<SessionJob.Uncached> uncached_ = NO_UNCACHED_ACTION;
    private Consumer<SessionJob.Authentication> authentication_ = NO_AUTHENTICATION_ACTION;
    private Consumer<SessionJob.Registration> registration_ = NO_REGISTRATION_ACTION;
    private Consumer<SessionJob.Completion> completion_ = NO_COMPLETION_ACTION;

    SessionJobBuilder(Player player) {
        this.player_ = Objects.requireNonNull(player, "player");
    }

    public SessionJobBuilder onUncached(Consumer<SessionJob.Uncached> callback) {
        this.uncached_ = Objects.requireNonNull(callback, "callback");
        return this;
    }

    public SessionJobBuilder beforeAuthentication(Consumer<SessionJob.Authentication> callback) {
        this.authentication_ = Objects.requireNonNull(callback, "callback");
        return this;
    }

    public SessionJobBuilder beforeRegistration(Consumer<SessionJob.Registration> callback) {
        this.registration_ = Objects.requireNonNull(callback, "callback");
        return this;
    }

    public SessionJobBuilder onComplete(Consumer<SessionJob.Completion> callback) {
        this.completion_ = Objects.requireNonNull(callback, "callback");
        return this;
    }

    public SessionJob build() {
        Player player = this.player_;
        Consumer<SessionJob.Uncached> uncached = this.uncached_;
        Consumer<SessionJob.Authentication> authentication = this.authentication_;
        Consumer<SessionJob.Registration> registration = this.registration_;
        Consumer<SessionJob.Completion> completion = this.completion_;

        return new SessionJob() {

            @Override
            public Player player() {
                return player;
            }

            @Override
            public void onUncached(Uncached context) {
                uncached.accept(context);
            }

            @Override
            public void beforeAuthentication(Authentication context) {
                authentication.accept(context);
            }

            @Override
            public void beforeRegistration(Registration context) {
                registration.accept(context);
            }

            @Override
            public void onComplete(Completion context) {
                completion.accept(context);
            }
        };
    }
}
