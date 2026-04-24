package com.fenmo.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Per-category aggregation used in {@link ExpenseSummaryResponse}.
 *
 * Java record: immutable, equals/hashCode/toString auto-generated.
 * Used as the JPQL constructor expression target.
 */
@Schema(description = "Expense total for a single category")
public record CategoryTotal(

    @Schema(description = "Category name", example = "Food & Dining")
    String category,

    @Schema(description = "Sum of all expenses in this category", example = "4500.00")
    BigDecimal total,

    @Schema(description = "Number of expense entries in this category", example = "12")
    Long count

) {}
