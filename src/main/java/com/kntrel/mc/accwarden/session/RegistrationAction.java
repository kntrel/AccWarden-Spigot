package com.kntrel.mc.accwarden.session;

public sealed interface RegistrationAction {

    record Password(String password) implements RegistrationAction {}

    record Quit() implements RegistrationAction {}

    record Error(String message) implements RegistrationAction {}
}
