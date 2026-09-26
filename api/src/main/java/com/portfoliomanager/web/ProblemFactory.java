package com.portfoliomanager.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 problem bodies so controllers and handlers never assemble errors by hand. */
final class ProblemFactory {

    record FieldError(String field, String message) {}

    private ProblemFactory() {}

    static ProblemDetail of(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    static ProblemDetail validation(String detail, List<FieldError> errors) {
        ProblemDetail problem = of(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("errors", errors);
        return problem;
    }
}
