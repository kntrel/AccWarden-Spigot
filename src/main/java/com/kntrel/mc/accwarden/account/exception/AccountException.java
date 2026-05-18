package com.kntrel.mc.accwarden.account.exception;

import com.kntrel.mc.accwarden.account.Account;

public abstract class AccountException extends RuntimeException {

    //FIELDS
    private final Account account_;

    //CONSTRUCTOR
    public AccountException(Account account) {
        this.account_ = account;
    }

    //GETTERS
    public Account getAccount() {
        return this.account_;
    }
}
