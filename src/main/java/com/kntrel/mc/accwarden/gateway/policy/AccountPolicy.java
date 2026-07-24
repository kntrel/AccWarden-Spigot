package com.kntrel.mc.accwarden.gateway.policy;


import com.kntrel.mc.accwarden.account.Account;
import com.kntrel.mc.accwarden.gateway.Decision;

public class AccountPolicy implements Policy<Account, AccountBucketState> {

    @Override
    public Decision consider(Account account, AccountBucketState state) {
        return null;
    }
}
