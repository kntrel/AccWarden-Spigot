package com.kntrel.mc.accwarden.view;

import java.util.Objects;
import java.util.function.Predicate;

public interface Validation extends Predicate<ViewResult> {

    String id();

    static Validation of(String id, Predicate<ViewResult> predicate) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(predicate, "predicate");
        return new Validation() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean test(ViewResult result) {
                return predicate.test(result);
            }
        };
    }
}
