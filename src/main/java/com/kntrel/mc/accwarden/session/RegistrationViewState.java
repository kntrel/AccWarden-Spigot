package com.kntrel.mc.accwarden.session;

import java.util.Optional;

public record RegistrationViewState(Optional<Failure> failure) {

    public RegistrationViewState {
        failure = failure == null ? Optional.empty() : failure;
    }

    public static RegistrationViewState initial() {
        return new RegistrationViewState(Optional.empty());
    }

    public static RegistrationViewState failed(Failure failure) {
        return new RegistrationViewState(Optional.of(failure));
    }

    public sealed interface Failure {

        record PasswordTooShort(int minLength) implements Failure {}

        record PasswordTooLong(int maxLength) implements Failure {}

        record PasswordRejected() implements Failure {}

        record Error(String message) implements Failure {}
    }
}
