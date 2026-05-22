package com.kntrel.mc.accwarden.form;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record FormResponse(
        FormResponseKind kind,
        Optional<String> actionId,
        Map<String, String> values
) {

    public FormResponse {
        kind = Objects.requireNonNull(kind, "kind");
        actionId = Objects.requireNonNull(actionId, "actionId");
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    public static FormResponse action(String actionId, Map<String, String> values) {
        return new FormResponse(FormResponseKind.ACTION, Optional.of(Form.requireId_(actionId, "actionId")), values);
    }

    public static FormResponse closed() {
        return new FormResponse(FormResponseKind.CLOSED, Optional.empty(), Map.of());
    }

    public static FormResponse invalid() {
        return new FormResponse(FormResponseKind.INVALID, Optional.empty(), Map.of());
    }

    public boolean isAction(String expectedActionId) {
        return this.kind().equals(FormResponseKind.ACTION)
                && this.actionId().filter(expectedActionId::equals).isPresent();
    }
}
