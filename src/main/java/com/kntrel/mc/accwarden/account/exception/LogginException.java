package com.kntrel.mc.accwarden.account.exception;

import com.kntrel.mc.accwarden.account.Account;

import java.util.Optional;

public class LogginException extends Exception {

    public enum Reason {
        ACCOUNT_NOT_FOUND,
        INCORRECT_PASSWORD,
        DENIED
    }

    private final Reason reason_;
    private final Account account_;
    private final String publicMessage_;

    public LogginException(Reason reason) {
        this(reason, null, null);
    }

    public LogginException(Reason reason, Account account) {
        this(reason, account, null);
    }

    public LogginException(Reason reason, Account account, String publicMessage) {
        super(publicMessage == null || publicMessage.isEmpty() ? reason.name() : publicMessage);
        this.reason_ = reason;
        this.account_ = account;
        this.publicMessage_ = publicMessage;
    }

    public Reason getReason() {
        return this.reason_;
    }

    public Optional<Account> getAccount() {
        return Optional.ofNullable(this.account_);
    }

    public Optional<String> getPublicMessage() {
        return Optional.ofNullable(this.publicMessage_).filter(message -> !message.isEmpty());
    }
}
