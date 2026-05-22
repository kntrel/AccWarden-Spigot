package com.kntrel.mc.accwarden.form;

import java.util.Objects;

public record FormText(
        String id,
        String text,
        FormTextTone tone
) implements FormElement {

    public FormText {
        id = Form.requireId_(id, "id");
        text = Objects.requireNonNull(text, "text");
        tone = Objects.requireNonNull(tone, "tone");
    }
}
