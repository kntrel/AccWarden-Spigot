package com.kntrel.mc.accwarden.view;

import com.kntrel.mc.accwarden.form.FormElement;
import com.kntrel.mc.accwarden.form.FormInput;
import com.kntrel.mc.accwarden.form.FormText;
import com.kntrel.mc.accwarden.form.FormTextTone;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ViewBuilder {

    private static final int DEFAULT_INPUT_MAX_LENGTH = 256;

    private ViewBuilder() {}

    static <T> Body<T> create() {
        return new BodyStage<>(new State<>());
    }

    public interface Elements<T, S> {
        S text(String text);
        S text(FormTextTone tone, String text);
        S input(String id, String label);
        S input(String id, String label, String placeholder, String defaultValue, boolean secret, int maxLength);
        S element(FormElement element);
        Conditional<T, S> ifFailed(String validationId);
    }

    public interface Conditional<T, S> {
        S text(String text);
        S text(FormTextTone tone, String text);
        S input(String id, String label);
        S input(String id, String label, String placeholder, String defaultValue, boolean secret, int maxLength);
        S element(FormElement element);
    }

    public interface Body<T> extends Elements<T, Body<T>> {
        Ready<T> action(ViewAction<? extends T> action);
        <U extends T, A extends ViewAction<U>> Ready<T> action(ViewActionBuilder.Finisher<U, A> action);
    }

    public interface Ready<T> extends Elements<T, Ready<T>> {
        Ready<T> action(ViewAction<? extends T> action);
        <U extends T, A extends ViewAction<U>> Ready<T> action(ViewActionBuilder.Finisher<U, A> action);
        View<T> end();
        default View<T> get() { return this.end(); }
    }

    private record ElementEntry(@Nullable String failedValidation, FormElement element) {}

    private static final class State<T> {
        private final List<ElementEntry> elements_ = new ArrayList<>();
        private final List<ViewAction<? extends T>> actions_ = new ArrayList<>();
        private int textCount_ = 0;

        private String nextTextId() {
            return "text_" + this.textCount_++;
        }
    }

    private abstract static class AbstractStage<T, S> implements Elements<T, S> {
        protected final State<T> state_;

        private AbstractStage(State<T> state) {
            this.state_ = state;
        }

        @Override
        public S text(String text) {
            return this.text(FormTextTone.NORMAL, text);
        }

        @Override
        public S text(FormTextTone tone, String text) {
            return this.element(new FormText(this.state_.nextTextId(), text, tone));
        }

        @Override
        public S input(String id, String label) {
            return this.input(id, label, "", "", false, DEFAULT_INPUT_MAX_LENGTH);
        }

        @Override
        public S input(String id, String label, String placeholder, String defaultValue, boolean secret, int maxLength) {
            return this.element(new FormInput(id, label, placeholder, defaultValue, secret, maxLength));
        }

        @Override
        public S element(FormElement element) {
            this.addElement_(null, element);
            return this.self_();
        }

        @Override
        public Conditional<T, S> ifFailed(String validationId) {
            return new ConditionalStage<>(this.state_, this.self_(), validationId);
        }

        protected void addElement_(@Nullable String failedValidation, FormElement element) {
            this.state_.elements_.add(new ElementEntry(
                    failedValidation,
                    Objects.requireNonNull(element, "element")
            ));
        }

        protected Ready<T> addAction_(ViewAction<? extends T> action) {
            this.state_.actions_.add(Objects.requireNonNull(action, "action"));
            return new ReadyStage<>(this.state_);
        }

        protected <U extends T, A extends ViewAction<U>> Ready<T> addAction_(ViewActionBuilder.Finisher<U, A> action) {
            return this.addAction_(Objects.requireNonNull(action, "action").end());
        }

        protected abstract S self_();
    }

    private static final class BodyStage<T> extends AbstractStage<T, Body<T>> implements Body<T> {

        private BodyStage(State<T> state) {
            super(state);
        }

        @Override
        public Ready<T> action(ViewAction<? extends T> action) {
            return this.addAction_(action);
        }

        @Override
        public <U extends T, A extends ViewAction<U>> Ready<T> action(ViewActionBuilder.Finisher<U, A> action) {
            return this.addAction_(action);
        }

        @Override
        protected Body<T> self_() {
            return this;
        }
    }

    private static final class ReadyStage<T> extends AbstractStage<T, Ready<T>> implements Ready<T> {

        private ReadyStage(State<T> state) {
            super(state);
        }

        @Override
        public Ready<T> action(ViewAction<? extends T> action) {
            this.state_.actions_.add(Objects.requireNonNull(action, "action"));
            return this;
        }

        @Override
        public <U extends T, A extends ViewAction<U>> Ready<T> action(ViewActionBuilder.Finisher<U, A> action) {
            return this.action(Objects.requireNonNull(action, "action").end());
        }

        @Override
        public View<T> end() {
            List<ElementEntry> elements = List.copyOf(this.state_.elements_);
            List<ViewAction<? extends T>> actions = List.copyOf(this.state_.actions_);
            return failedValidations -> new ViewBody<>(
                    elements.stream()
                            .filter(entry -> entry.failedValidation() == null
                                    || failedValidations.contains(entry.failedValidation()))
                            .map(ElementEntry::element)
                            .toList(),
                    actions
            );
        }

        @Override
        protected Ready<T> self_() {
            return this;
        }
    }

    private static final class ConditionalStage<T, S> implements Conditional<T, S> {
        private final State<T> state_;
        private final S next_;
        private final String validationId_;

        private ConditionalStage(State<T> state, S next, String validationId) {
            this.state_ = state;
            this.next_ = next;
            this.validationId_ = Objects.requireNonNull(validationId, "validationId");
        }

        @Override
        public S text(String text) {
            return this.text(FormTextTone.NORMAL, text);
        }

        @Override
        public S text(FormTextTone tone, String text) {
            return this.element(new FormText(this.state_.nextTextId(), text, tone));
        }

        @Override
        public S input(String id, String label) {
            return this.input(id, label, "", "", false, DEFAULT_INPUT_MAX_LENGTH);
        }

        @Override
        public S input(String id, String label, String placeholder, String defaultValue, boolean secret, int maxLength) {
            return this.element(new FormInput(id, label, placeholder, defaultValue, secret, maxLength));
        }

        @Override
        public S element(FormElement element) {
            this.state_.elements_.add(new ElementEntry(
                    this.validationId_,
                    Objects.requireNonNull(element, "element")
            ));
            return this.next_;
        }
    }
}
