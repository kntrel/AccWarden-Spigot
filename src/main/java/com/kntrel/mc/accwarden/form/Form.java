package com.kntrel.mc.accwarden.form;

import java.util.List;
import java.util.Objects;

public record Form(
        String id,
        String title,
        List<FormElement> elements,
        List<FormAction> actions,
        boolean canClose
) {

    public Form {
        id = requireId_(id, "id");
        title = Objects.requireNonNull(title, "title");
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }

    static String requireId_(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank.");
        }
        return value;
    }
}
