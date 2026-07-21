package com.kntrel.mc.accwarden.account.exception;

import com.kntrel.mc.accwarden.account.Account;

public class PasswordConfirmationFailedException extends InvalidPasswordException {

    public PasswordConfirmationFailedException(Account account, String inputPassword) {
        super(account, inputPassword);
    }
}
