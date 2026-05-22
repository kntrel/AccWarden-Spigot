package com.kntrel.mc.accwarden.form;

import java.util.Objects;

public record FormAction(
        String id,
        String label,
        FormActionRole role
) {

    public FormAction {
        id = Form.requireId_(id, "id");
        label = Objects.requireNonNull(label, "label");
        role = Objects.requireNonNull(role, "role");
    }
}
