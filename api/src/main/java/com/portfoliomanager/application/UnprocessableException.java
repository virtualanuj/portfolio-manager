package com.portfoliomanager.application;

/** The request is well formed but breaks a rule, for example committing rows with errors (422). */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
