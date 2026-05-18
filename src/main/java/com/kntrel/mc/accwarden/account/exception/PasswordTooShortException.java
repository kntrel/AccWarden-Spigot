package com.kntrel.mc.accwarden.account.exception;

import com.kntrel.mc.accwarden.account.Account;

public class PasswordTooShortException extends InvalidPasswordException{

    //FIELDS
    private final int minLength_;

    public PasswordTooShortException(Account account, String inputPassword, int minLength) {
        super(account, inputPassword);
        this.minLength_ = minLength;
    }

    //GETTERS
    public int getMinLength() {
        return this.minLength_;
    }
}
