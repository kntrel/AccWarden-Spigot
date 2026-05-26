package com.kntrel.mc.accwarden.session;

import javax.annotation.Nullable;
import java.util.Optional;

public record AuthenticationViewState(Kind kind, Optional<Failure> failure) {

    public AuthenticationViewState(@Nullable Failure failure) {
        this(Kind.REGULAR, failure);
    }

    public AuthenticationViewState(Kind kind, @Nullable Failure failure) {
        this(kind, failure == null ? Optional.empty() : Optional.of(failure));
    }

    public AuthenticationViewState {
        kind = kind == null ? Kind.REGULAR : kind;
        failure = failure == null ? Optional.empty() : failure;
    }

    public static AuthenticationViewState initial() {
        return regular();
    }

    public static AuthenticationViewState regular() {
        return new AuthenticationViewState(Kind.REGULAR, Optional.empty());
    }

    public static AuthenticationViewState platformFirstTime() {
        return new AuthenticationViewState(Kind.PLATFORM_FIRST_TIME, Optional.empty());
    }

    public static AuthenticationViewState failed(Failure failure) {
        return failed(Kind.REGULAR, failure);
    }

    public static AuthenticationViewState failed(Kind kind, Failure failure) {
        return new AuthenticationViewState(kind, Optional.of(failure));
    }

    public boolean isPlatformFirstTime() {
        return this.kind().equals(Kind.PLATFORM_FIRST_TIME);
    }

    public enum Kind {
        REGULAR,
        PLATFORM_FIRST_TIME
    }

    public sealed interface Failure {

        record IncorrectPassword() implements Failure {}

        record AccountLocked() implements Failure {}

        record AccountNotFound() implements Failure {}

        record Denied(String message) implements Failure {}

        record Error(String message) implements Failure {}
    }
}
