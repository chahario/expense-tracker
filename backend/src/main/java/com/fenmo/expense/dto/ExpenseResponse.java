package com.fenmo.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Expense entry as returned by the API")
public class ExpenseResponse {

    @Schema(description = "Unique expense identifier (UUID)")
    private UUID id;

    @Schema(description = "Expense amount, exactly 2 decimal places", example = "250.00")
    private BigDecimal amount;

    @Schema(description = "Expense category", example = "Food & Dining")
    private String category;

    @Schema(description = "Expense description", example = "Lunch at Sharma Dhaba")
    private String description;

    @Schema(description = "Transaction date", example = "2024-04-24")
    private LocalDate date;

    @Schema(description = "Server-assigned creation timestamp (UTC)")
    private Instant createdAt;
}
