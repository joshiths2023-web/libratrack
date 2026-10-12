package com.libratrack.service;

import java.util.ArrayList;
import java.util.List;

/** Collects the reasons why an operation was rejected. */
public class ValidationResult {
    private final List<String> errors = new ArrayList<>();

    public void add(String error) {
        errors.add(error);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    public String summary() {
        return String.join("; ", errors);
    }
}
