package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.account.Account;

public record LoginRequest(Account account, NetworkKey network) {
}
