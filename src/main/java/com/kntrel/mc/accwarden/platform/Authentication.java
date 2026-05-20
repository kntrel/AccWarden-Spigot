package com.kntrel.mc.accwarden.platform;

import com.kntrel.mc.accwarden.account.Account;

public sealed interface Authentication {

    record Passed(Account account) implements Authentication {
        public Passed {
            if (account == null) {
                throw new IllegalArgumentException("Passed authentication requires an account.");
            }
        }
    }

    record Rejected() implements Authentication {}

    record Unexistent() implements Authentication {}

    static Authentication passed(Account account) {
        return new Passed(account);
    }

    static Authentication rejected() {
        return new Rejected();
    }

    static Authentication unexistent() {
        return new Unexistent();
    }
}
