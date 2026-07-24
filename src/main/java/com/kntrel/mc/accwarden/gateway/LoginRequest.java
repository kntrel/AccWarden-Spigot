package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.account.Account;

import java.util.Objects;

public record LoginRequest(Account account, NetworkKey network) {

    public LoginRequest {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(network, "network");
    }
}
