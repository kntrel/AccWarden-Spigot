package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.LoginRequest;

import java.util.Objects;
import java.util.UUID;

final class AccountIdentity {

    private AccountIdentity() {}

    static UUID uuid(LoginRequest request) {
        Objects.requireNonNull(request, "request");
        return request.account()
                .getUuid()
                .orElseThrow(() -> new IllegalArgumentException(
                        "A login account must have a UUID."
                ));
    }
}
