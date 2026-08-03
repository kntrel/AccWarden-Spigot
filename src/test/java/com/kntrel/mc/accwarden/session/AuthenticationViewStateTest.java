package com.kntrel.mc.accwarden.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthenticationViewStateTest {

    @Test
    void incorrectPasswordAttemptsAdvanceWithinTheFormFlow() {
        AuthenticationViewState first = AuthenticationViewState
                .platformFirstTime()
                .nextIncorrectPassword();
        AuthenticationViewState second = first.nextIncorrectPassword();

        assertEquals(AuthenticationViewState.Kind.PLATFORM_FIRST_TIME, second.kind());
        AuthenticationViewState.Failure.IncorrectPassword failure = assertInstanceOf(
                AuthenticationViewState.Failure.IncorrectPassword.class,
                second.failure().orElseThrow()
        );
        assertEquals(2, failure.attempt());
    }
}
