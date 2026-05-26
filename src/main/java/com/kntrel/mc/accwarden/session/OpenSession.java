package com.kntrel.mc.accwarden.session;

import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.platform.Platform;

import java.net.InetSocketAddress;

public record OpenSession(Account account, InetSocketAddress address, Platform platform) {

    //CUSTOM GETTERS
    public boolean isJava() {
        return this.platform.isJava();
    }
    public boolean isBedrock() {
        return this.platform.isBedrock();
    }
}
