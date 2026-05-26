package com.kntrel.mc.accwarden.view;

import com.kntrel.mc.accwarden.form.FormElement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

public record ViewBody<T>(
        List<FormElement> elements,
        List<ViewAction<? extends T>> actions,
        boolean allowExit,
        Optional<ViewAction<? extends T>> exitAction
) {

    public ViewBody {
        elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        Objects.requireNonNull(exitAction, "exitAction");
    }

    public ViewBody(List<FormElement> elements, List<ViewAction<? extends T>> actions) {
        this(elements, actions, true, Optional.empty());
    }

    public ViewBody(Iterable<? extends FormElement> elements, Iterable<? extends ViewAction<? extends T>> actions) {
        this(elements, actions, true, Optional.empty());
    }

    public ViewBody(
            Iterable<? extends FormElement> elements,
            Iterable<? extends ViewAction<? extends T>> actions,
            boolean allowExit,
            Optional<? extends ViewAction<? extends T>> exitAction
    ) {
        this(
                StreamSupport.stream(Objects.requireNonNull(elements, "elements").spliterator(), false)
                        .<FormElement>map(element -> element)
                        .toList(),
                StreamSupport.stream(Objects.requireNonNull(actions, "actions").spliterator(), false)
                        .<ViewAction<? extends T>>map(action -> action)
                        .toList(),
                allowExit,
                Objects.requireNonNull(exitAction, "exitAction").map(action -> action)
        );
    }
}
