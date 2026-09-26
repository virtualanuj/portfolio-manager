package com.portfoliomanager.application;

/** A rule on a single request field was broken (HTTP 400 with the field named). */
public class ValidationException extends RuntimeException {

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
