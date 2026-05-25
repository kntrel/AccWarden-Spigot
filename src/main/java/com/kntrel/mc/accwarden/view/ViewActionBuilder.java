package com.kntrel.mc.accwarden.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ViewActionBuilder {

    private ViewActionBuilder() {}

    public interface IdGetter<I> {
        LabelGetter<I> id(String id);
    }

    public interface LabelGetter<I> {
        I label(String label);
    }

    public interface ValueGetter<T> {
        Finisher<T, ViewAction.Return<T>> value(Function<ViewResult, T> valueFunction);
        default Finisher<T, ViewAction.Return<T>> value(T value) { return this.value(_ -> value); }
    }

    public interface ViewGetter<T> {
        Finisher<T, ViewAction.Link<T>> view(Function<ViewResult, View<T>> viewFunction);
        default Finisher<T, ViewAction.Link<T>> view(View<T> subView) {
            return this.view((Function<ViewResult, View<T>>) _ -> subView);
        }
    }

    public interface ValidationGetter<T, A extends ViewAction<T>> {
        Finisher<T, A> validates(Validation validation);
        default Finisher<T, A> validates(String id, Predicate<ViewResult> predicate) {
            return this.validates(Validation.of(id, predicate));
        }
    }

    public interface Finisher<T, A extends ViewAction<T>> extends ValidationGetter<T, A> {
        A end();
        default A get() { return this.end(); }
    }

    public static final class Link<T>
    implements IdGetter<ViewGetter<T>>,
               LabelGetter<ViewGetter<T>>,
               ViewGetter<T>,
               Finisher<T, ViewAction.Link<T>>
    {
        private String id_, label_;
        private Function<ViewResult, View<T>> viewFunction_;
        private final List<Validation> validations_ = new ArrayList<>();

        @Override
        public ViewAction.Link<T> end() {
            return new ViewAction.Link<>(
                    this.id_,
                    this.label_,
                    this.viewFunction_,
                    this.validations_
            );
        }

        @Override
        public LabelGetter<ViewGetter<T>> id(String id) {
            this.id_ = Objects.requireNonNull(id, "id");
            return this;
        }

        @Override
        public ViewGetter<T> label(String label) {
            this.label_ = Objects.requireNonNull(label, "label");
            return this;
        }

        @Override
        public Finisher<T, ViewAction.Link<T>> validates(Validation validation) {
            this.validations_.add(Objects.requireNonNull(validation, "validation"));
            return this;
        }

        @Override
        public Finisher<T, ViewAction.Link<T>> view(Function<ViewResult, View<T>> viewFunction) {
            this.viewFunction_ = Objects.requireNonNull(viewFunction, "viewFunction");
            return this;
        }
    }

    public static final class Return<T>
    implements IdGetter<ValueGetter<T>>,
               LabelGetter<ValueGetter<T>>,
               ValueGetter<T>,
               Finisher<T, ViewAction.Return<T>>
    {
        private String id_, label_;
        private Function<ViewResult, T> valueFunction_;
        private final List<Validation> validations_ = new ArrayList<>();


        @Override
        public ViewAction.Return<T> end() {
            return new ViewAction.Return<>(
                    this.id_,
                    this.label_,
                    this.valueFunction_,
                    this.validations_
            );
        }

        @Override
        public LabelGetter<ValueGetter<T>> id(String id) {
            this.id_ = Objects.requireNonNull(id, "id");
            return this;
        }

        @Override
        public ValueGetter<T> label(String label) {
            this.label_ = Objects.requireNonNull(label, "label");
            return this;
        }

        @Override
        public Finisher<T, ViewAction.Return<T>> validates(Validation validation) {
            this.validations_.add(Objects.requireNonNull(validation, "validation"));
            return this;
        }

        @Override
        public Finisher<T, ViewAction.Return<T>> value(Function<ViewResult, T> valueFunction) {
            this.valueFunction_ = Objects.requireNonNull(valueFunction, "valueFunction");
            return this;
        }
    }
}
