package com.portfoliomanager.web;

import com.portfoliomanager.application.ConflictException;
import com.portfoliomanager.application.NotFoundException;
import com.portfoliomanager.application.OversellException;
import com.portfoliomanager.application.RefreshInProgressException;
import com.portfoliomanager.application.ValidationException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** The one place exceptions become {@code application/problem+json} responses. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ProblemFactory.FieldError> errors =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                e ->
                                        new ProblemFactory.FieldError(
                                                e.getField(), e.getDefaultMessage()))
                        .toList();
        return ResponseEntity.badRequest()
                .body(ProblemFactory.validation("The request has invalid fields", errors));
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return ProblemFactory.of(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return ProblemFactory.of(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail invalid(ValidationException exception) {
        return ProblemFactory.validation(
                exception.getMessage(),
                List.of(
                        new ProblemFactory.FieldError(
                                exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(RefreshInProgressException.class)
    ProblemDetail refreshInProgress(RefreshInProgressException exception) {
        ProblemDetail problem = ProblemFactory.of(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("runId", exception.getRunId().toString());
        return problem;
    }

    @ExceptionHandler(OversellException.class)
    ProblemDetail oversell(OversellException exception) {
        ProblemDetail problem =
                ProblemFactory.of(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setProperty("transactionId", exception.getTransactionId().toString());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected error, correlation id {}", correlationId, exception);
        ProblemDetail problem =
                ProblemFactory.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Something went wrong. Quote the correlation id if you report this.");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }
}
