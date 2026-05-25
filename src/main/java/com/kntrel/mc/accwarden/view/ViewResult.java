package com.kntrel.mc.accwarden.view;

import java.util.Map;
import java.util.Optional;

public class ViewResult {

    //FIELDS
    private final Map<String, String> data_;


    //CONSTRUCTOR
    public ViewResult(Map<String, String> data) {
        this.data_ = Map.copyOf(data);
    }


    //API
    public String get(String key) {
        return this.data_.getOrDefault(key, "");
    }
    public Optional<String> getValue(String key) {
        return Optional.ofNullable(this.data_.get(key));
    }
    public boolean hasValue(String key) {
        return this.data_.containsKey(key);
    }
}
