package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.Decision;
import com.kntrel.mc.accwarden.gateway.NetworkKey;

public class ClientPolicy implements Policy<NetworkKey, ClientBucketState> {

    @Override
    public Decision consider(NetworkKey subject, ClientBucketState state) {
        return null;
    }

}