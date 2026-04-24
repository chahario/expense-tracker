package com.fenmo.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Payload for creating a new expense entry")
public class CreateExpenseRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 17, fraction = 2,
            message = "Amount must have at most 17 integer digits and 2 decimal places")
    @Schema(description = "Expense amount in INR", example = "250.00", minimum = "0.01")
    private BigDecimal amount;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must not exceed 100 characters")
    @Schema(description = "Expense category", example = "Food & Dining")
    private String category;

    @NotBlank(message = "Description is required")
    @Size(min = 1, max = 500, message = "Description must be between 1 and 500 characters")
    @Schema(description = "Brief description of the expense", example = "Lunch at Sharma Dhaba")
    private String description;

    @NotNull(message = "Date is required")
    @Schema(description = "Transaction date (ISO-8601 format)", example = "2024-04-24")
    private LocalDate date;
    // Note: @PastOrPresent intentionally omitted.
    // Server is UTC; client may be in IST (+5:30). A date that's "tomorrow" in UTC
    // is "today" for an IST user submitting at 11 PM. Rejecting it would be wrong.
}
