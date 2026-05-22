package com.kntrel.mc.accwarden.form;

import java.util.Objects;

public record FormInput(
        String id,
        String label,
        String placeholder,
        String defaultValue,
        boolean secret,
        int maxLength
) implements FormElement {

    public FormInput {
        id = Form.requireId_(id, "id");
        label = Objects.requireNonNull(label, "label");
        placeholder = Objects.requireNonNull(placeholder, "placeholder");
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be greater than zero.");
        }
    }
}
