package com.fenmo.expense.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Schema(description = "Structured error response returned for all error status codes")
public class ApiError {

    @Schema(description = "HTTP status code", example = "400")
    private final int status;

    @Schema(description = "Short error summary", example = "Validation failed")
    private final String message;

    @Schema(description = "Field-level validation errors (may be empty)", example = "[\"amount: must be > 0\"]")
    private final List<String> errors;

    @Schema(description = "Server timestamp when error occurred")
    private final Instant timestamp;

    public ApiError(int status, String message, List<String> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors != null ? errors : List.of();
        this.timestamp = Instant.now();
    }
}
