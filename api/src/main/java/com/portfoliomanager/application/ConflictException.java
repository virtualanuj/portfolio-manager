package com.portfoliomanager.application;

/** The request conflicts with current state, for example a duplicate name (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
