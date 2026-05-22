package com.kntrel.mc.accwarden.form;

public sealed interface FormElement permits FormText, FormInput {

    String id();
}
