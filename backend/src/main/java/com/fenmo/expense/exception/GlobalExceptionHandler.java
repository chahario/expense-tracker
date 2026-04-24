package com.fenmo.expense.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response translation.
 *
 * Rules enforced here:
 *  1. Never expose stack traces to clients.
 *  2. Client errors (4xx) logged at WARN; server errors (5xx) at ERROR.
 *  3. Every response uses the {@link ApiError} envelope.
 *  4. requestId is set by {@link com.fenmo.expense.filter.RequestLoggingFilter}
 *     via MDC, so all log lines for a request share the same ID.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Bean Validation failures from @Valid on request body */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .sorted()
                .collect(Collectors.toList());

        log.warn("Validation failed with {} error(s): {}", fieldErrors.size(), fieldErrors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, "Validation failed", fieldErrors));
    }

    /** Malformed JSON body or wrong field types */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, "Malformed or unreadable request body", List.of()));
    }

    /** Path/query variable type mismatch (e.g., non-UUID where UUID expected) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("Parameter '%s' has invalid value: '%s'", ex.getName(), ex.getValue());
        log.warn(msg);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, msg, List.of()));
    }

    /** Missing required headers */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getHeaderName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, "Missing required header: " + ex.getHeaderName(), List.of()));
    }

    /** Business-rule violations thrown explicitly in service layer */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArg(IllegalArgumentException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(400, ex.getMessage(), List.of()));
    }

    /** Catch-all: unexpected errors — never expose internals */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Unexpected error — please check server logs", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, "An unexpected error occurred. Please try again.", List.of()));
    }
}
