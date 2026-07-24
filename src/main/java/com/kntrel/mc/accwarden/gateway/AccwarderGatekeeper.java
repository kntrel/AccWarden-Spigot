package com.kntrel.mc.accwarden.gateway;

import com.kntrel.mc.accwarden.gateway.policy.AccountPolicy;
import com.kntrel.mc.accwarden.gateway.policy.ClientPolicy;
import com.kntrel.mc.accwarden.gateway.policy.LoginPolicy;

public class AccwarderGatekeeper {

    //FIELDS
    private final AccountPolicy accountPolicy_;
    private final ClientPolicy clientPolicy_;
    private final LoginPolicy loginPolicy_;


    //CONSTRUCTORS
    public AccwarderGatekeeper(AccountPolicy accountPolicy, ClientPolicy clientPolicy, LoginPolicy loginPolicy) {
        this.accountPolicy_ = accountPolicy;
        this.clientPolicy_ = clientPolicy;
        this.loginPolicy_ = loginPolicy;
    }


    //CONTRACT
    public Decision considerConnection(NetworkKey request) {
        return null;
    }
    public Decision considerLogin(LoginRequest request) {
        return null;
    }
}
