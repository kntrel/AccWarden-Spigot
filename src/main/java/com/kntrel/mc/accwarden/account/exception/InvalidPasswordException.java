package com.kntrel.mc.accwarden.account.exception;

import com.kntrel.mc.accwarden.account.Account;

public abstract class InvalidPasswordException extends AccountException {

    //FIELDS
    private final String password_;

    public InvalidPasswordException(Account account, String inputPassword) {
        super(account);
        this.password_ = inputPassword;
    }

    //GETTERS
    public String getInputPassword() {
        return this.password_;
    }
}
