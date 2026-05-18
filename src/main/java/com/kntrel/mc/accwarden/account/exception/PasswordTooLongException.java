package com.kntrel.mc.accwarden.account.exception;

import com.kntrel.mc.accwarden.account.Account;

public class PasswordTooLongException extends InvalidPasswordException {

    //FIELDS
    private final int maxLength_;

    public PasswordTooLongException(Account account, String inputPassword, int maxLength) {
        super(account, inputPassword);
        this.maxLength_ = maxLength;
    }

    //GETTERS
    public int getMaxLength() {
        return this.maxLength_;
    }

}
