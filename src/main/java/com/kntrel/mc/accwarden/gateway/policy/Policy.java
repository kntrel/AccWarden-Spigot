package com.kntrel.mc.accwarden.gateway.policy;

import com.kntrel.mc.accwarden.gateway.Decision;

public interface Policy<T, S extends BucketState<T>> {

    Decision consider(T subject, S state);

}
