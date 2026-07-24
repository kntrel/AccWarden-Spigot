package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.Decision;
import com.kntrel.mc.accwarden.gateway.LoginRequest;

public class LoginPolicy implements Policy<LoginRequest, LoginBucketState> {
    @Override
    public Decision consider(LoginRequest subject, LoginBucketState state) {
        return null;
    }
}
