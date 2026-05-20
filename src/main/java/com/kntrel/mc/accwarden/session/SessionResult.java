package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.account.Account;

public sealed interface SessionResult {

    record Caches(OpenSession session, Account account) implements SessionResult {
        public Caches {
            validate(session, account);
        }
    }

    record Opened(OpenSession session, Account account) implements SessionResult {
        public Opened {
            validate(session, account);
        }
    }

    record Registered(OpenSession session, Account account) implements SessionResult {
        public Registered {
            validate(session, account);
        }
    }

    record Unauthenticated() implements SessionResult {}

    record Failed(Throwable cause) implements SessionResult {
        public Failed {
            if (cause == null) {
                throw new IllegalArgumentException("Failed session result requires a cause.");
            }
        }
    }

    static SessionResult caches(OpenSession session) {
        return new Caches(session, session.account());
    }

    static SessionResult opened(OpenSession session) {
        return new Opened(session, session.account());
    }

    static SessionResult registered(OpenSession session) {
        return new Registered(session, session.account());
    }

    static SessionResult unauthenticated() {
        return new Unauthenticated();
    }

    static SessionResult failed(Throwable cause) {
        if (cause == null) {
            cause = new IllegalStateException("Unknown session failure.");
        }
        return new Failed(cause);
    }

    private static void validate(OpenSession session, Account account) {
        if (session == null) {
            throw new IllegalArgumentException("Session result requires a session.");
        }
        if (account == null) {
            throw new IllegalArgumentException("Session result requires an account.");
        }
    }
}
