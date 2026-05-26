package com.kntrel.mc.accwarden.session;

public sealed interface AuthenticationAction {

    record Password(String password) implements AuthenticationAction {}

    record Quit() implements AuthenticationAction {}

    record Error(String message) implements AuthenticationAction {}
}
