package com.fenmo.expense.service;

import com.fenmo.expense.dto.*;
import com.fenmo.expense.model.Expense;
import com.fenmo.expense.model.IdempotencyRecord;
import com.fenmo.expense.repository.ExpenseRepository;
import com.fenmo.expense.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final IdempotencyRepository idempotencyRepository;

    // ─── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates an expense with at-most-once semantics when an idempotency key is supplied.
     *
     * Idempotency contract:
     *   • Key present + found  → return original expense, skip insert.
     *   • Key present + absent → insert expense + insert key record (same TX).
     *   • Key absent           → insert expense, no idempotency protection.
     *
     * The insert + key record write are in a single @Transactional boundary.
     * If the app crashes between the two writes (race), the next retry won't find
     * the key and will retry the expense insert — at-least-once in the crash case,
     * bounded by the 48h TTL.
     *
     * @param request        validated request DTO
     * @param idempotencyKey nullable header value
     */
    @Transactional
    public ExpenseResponse createExpense(CreateExpenseRequest request, String idempotencyKey) {
        boolean keyProvided = idempotencyKey != null && !idempotencyKey.isBlank();

        if (keyProvided) {
            String key = idempotencyKey.trim();
            var existing = idempotencyRepository.findById(key);

            if (existing.isPresent()) {
                log.info("Idempotent replay for key={}", key);
                return expenseRepository
                        .findById(existing.get().getExpenseId())
                        .map(this::toResponse)
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record references a missing expense — data integrity issue"));
            }

            return createAndPersist(request, key);
        }

        return createAndPersist(request, null);
    }

    private ExpenseResponse createAndPersist(CreateExpenseRequest request, String idempotencyKey) {
        Expense expense = new Expense();
        // Enforce exactly 2dp with HALF_UP — matches standard accounting rounding
        expense.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        expense.setCategory(normalizeCategory(request.getCategory()));
        expense.setDescription(request.getDescription().trim());
        expense.setDate(request.getDate());
        // id and createdAt populated by @PrePersist

        expense = expenseRepository.save(expense);
        log.info("Created expense id={} category='{}' amount={} date={}",
                expense.getId(), expense.getCategory(), expense.getAmount(), expense.getDate());

        if (idempotencyKey != null) {
            idempotencyRepository.save(new IdempotencyRecord(idempotencyKey, expense.getId()));
            log.debug("Saved idempotency record key={} → expenseId={}", idempotencyKey, expense.getId());
        }

        return toResponse(expense);
    }

    // ─── List ─────────────────────────────────────────────────────────────────

    /**
     * Returns all expenses, always newest-first.
     * Category filter is case-insensitive; null/blank returns all.
     */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpenses(String category) {
        String filter = (category != null && !category.isBlank()) ? category : null;
        log.debug("Listing expenses — category filter: '{}'", filter);

        return expenseRepository.findFiltered(filter)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ─── Summary ──────────────────────────────────────────────────────────────

    /**
     * Returns aggregated totals per category plus a grand total.
     * Always covers ALL expenses (no filter) — this is the overview view.
     */
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse getSummary() {
        List<CategoryTotal> breakdown = expenseRepository.getCategorySummary();

        BigDecimal grandTotal = breakdown.stream()
                .map(CategoryTotal::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        long totalCount = breakdown.stream()
                .mapToLong(CategoryTotal::count)
                .sum();

        ExpenseSummaryResponse summary = new ExpenseSummaryResponse();
        summary.setCategoryBreakdown(breakdown);
        summary.setGrandTotal(grandTotal);
        summary.setTotalCount(totalCount);

        log.debug("Summary: {} categories, grandTotal={}", breakdown.size(), grandTotal);
        return summary;
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Normalises category: trim whitespace, Title-Case the first letter.
     * "  food " → "Food".  Prevents "food" and "Food" as separate categories.
     */
    private static String normalizeCategory(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return trimmed;
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private ExpenseResponse toResponse(Expense e) {
        ExpenseResponse r = new ExpenseResponse();
        r.setId(e.getId());
        r.setAmount(e.getAmount());
        r.setCategory(e.getCategory());
        r.setDescription(e.getDescription());
        r.setDate(e.getDate());
        r.setCreatedAt(e.getCreatedAt());
        return r;
    }
}
