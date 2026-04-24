package com.fenmo.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "Aggregated expense summary across all categories")
public class ExpenseSummaryResponse {

    @Schema(description = "Per-category breakdown, sorted by total descending")
    private List<CategoryTotal> categoryBreakdown;

    @Schema(description = "Sum of all expenses across all categories", example = "18500.00")
    private BigDecimal grandTotal;

    @Schema(description = "Total number of expense entries", example = "47")
    private long totalCount;
}
