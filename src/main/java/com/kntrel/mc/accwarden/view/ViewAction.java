package com.kntrel.mc.accwarden.view;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.StreamSupport;

sealed public abstract class ViewAction<T> {

    public static <T> ViewActionBuilder.IdGetter<ViewActionBuilder.ValueGetter<T>> returnValue() {
        return new ViewActionBuilder.Return<>();
    }
    public static <T> ViewActionBuilder.IdGetter<ViewActionBuilder.ViewGetter<T>> link() {
        return new ViewActionBuilder.Link<>();
    }


    //FIELDS
    private final String id_, label_;
    private final List<Validation> validations_;


    //CONSTRUCTOR
    protected ViewAction(String id, String label, @Nullable Validation validation) {
        this(id, label, validation == null ? List.of() : List.of(validation));
    }
    protected ViewAction(String id, String label, Iterable<? extends Validation> validations) {
        this.id_ = Objects.requireNonNull(id, "id");
        this.label_ = Objects.requireNonNull(label, "label");
        this.validations_ = StreamSupport.stream(Objects.requireNonNull(validations, "validations").spliterator(), false)
                .<Validation>map(validation -> validation)
                .toList();
    }


    //GETTERS
    public final String id() {
        return this.id_;
    }

    public final String label() {
        return this.label_;
    }

    public final List<Validation> validations() {
        return this.validations_;
    }


    //SUBTYPES
    public final static class Link<T> extends ViewAction<T> {

        private Function<ViewResult, View<T>> viewFunction_;

        public Link(String id, String label, Function<ViewResult, View<T>> viewFunction, @Nullable Validation validation) {
            super(id, label, validation);
            this.viewFunction_ = Objects.requireNonNull(viewFunction, "viewFunction");
        }
        public Link(String id, String label, Function<ViewResult, View<T>> viewFunction, Iterable<? extends Validation> validations) {
            super(id, label, validations);
            this.viewFunction_ = Objects.requireNonNull(viewFunction, "viewFunction");
        }
        public Link(String id, String label, Function<ViewResult, View<T>> viewFunction) {
            this(id, label, viewFunction, (Validation) null);
        }

        public View<T> getSubView(ViewResult viewResult) { return this.viewFunction_.apply(viewResult); }
    }

    public final static class Return<T> extends ViewAction<T> {

        private Function<ViewResult, T> valueFunction_;

        public Return(String id, String label, Function<ViewResult, T> valueFunction, @Nullable Validation validation) {
            super(id, label, validation);
            this.valueFunction_ = Objects.requireNonNull(valueFunction, "valueFunction");
        }
        public Return(String id, String label, Function<ViewResult, T> valueFunction, Iterable<? extends Validation> validations) {
            super(id, label, validations);
            this.valueFunction_ = Objects.requireNonNull(valueFunction, "valueFunction");
        }
        public Return(String id, String label, Function<ViewResult, T> valueFunction) {
            this(id, label, valueFunction, (Validation) null);
        }

        public T getValue(ViewResult viewResult) { return this.valueFunction_.apply(viewResult); }
    }
}
