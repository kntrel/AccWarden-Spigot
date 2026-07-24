package com.kntrel.mc.accwarden.gateway;

public sealed interface Decision {

    record Pass() implements Decision {}

    record Throttled(Penalty penalty) implements Decision {}

}
