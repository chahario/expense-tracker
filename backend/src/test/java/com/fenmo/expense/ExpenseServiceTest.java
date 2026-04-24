package com.fenmo.expense;

import com.fenmo.expense.dto.CategoryTotal;
import com.fenmo.expense.dto.CreateExpenseRequest;
import com.fenmo.expense.dto.ExpenseResponse;
import com.fenmo.expense.dto.ExpenseSummaryResponse;
import com.fenmo.expense.model.Expense;
import com.fenmo.expense.model.IdempotencyRecord;
import com.fenmo.expense.repository.ExpenseRepository;
import com.fenmo.expense.repository.IdempotencyRepository;
import com.fenmo.expense.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock  private ExpenseRepository expenseRepository;
    @Mock  private IdempotencyRepository idempotencyRepository;
    @InjectMocks private ExpenseService expenseService;

    private Expense savedExpense;
    private CreateExpenseRequest validRequest;
    private final String idempotencyKey = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        savedExpense = new Expense();
        savedExpense.setId(UUID.randomUUID());
        savedExpense.setAmount(new BigDecimal("250.00"));
        savedExpense.setCategory("Food");
        savedExpense.setDescription("Lunch");
        savedExpense.setDate(LocalDate.now());
        savedExpense.setCreatedAt(Instant.now());

        validRequest = new CreateExpenseRequest();
        validRequest.setAmount(new BigDecimal("250.00"));
        validRequest.setCategory("food");  // lowercase — service should normalise
        validRequest.setDescription("Lunch");
        validRequest.setDate(LocalDate.now());
    }

    // ─── Create: first-time path ──────────────────────────────────────────────

    @Test
    @DisplayName("First-time create: inserts expense and idempotency record")
    void createExpense_firstTime_shouldPersistBoth() {
        when(idempotencyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        ExpenseResponse response = expenseService.createExpense(validRequest, idempotencyKey);

        assertThat(response.getAmount()).isEqualByComparingTo("250.00");
        verify(expenseRepository, times(1)).save(any(Expense.class));
        verify(idempotencyRepository, times(1)).save(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("Category is normalised to Title-Case on save")
    void createExpense_categoryNormalised() {
        when(idempotencyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            assertThat(e.getCategory()).isEqualTo("Food"); // was "food"
            return savedExpense;
        });

        expenseService.createExpense(validRequest, idempotencyKey);
    }

    @Test
    @DisplayName("Amount is rounded to 2dp with HALF_UP before save")
    void createExpense_amountScaled() {
        validRequest.setAmount(new BigDecimal("99.999")); // extra precision

        when(idempotencyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            assertThat(e.getAmount()).isEqualByComparingTo("100.00"); // HALF_UP
            return savedExpense;
        });

        expenseService.createExpense(validRequest, idempotencyKey);
    }

    // ─── Create: idempotent replay path ──────────────────────────────────────

    @Test
    @DisplayName("Duplicate key: returns original expense, no second insert")
    void createExpense_duplicateKey_returnsOriginalWithoutInsert() {
        IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, savedExpense.getId());
        when(idempotencyRepository.findById(idempotencyKey)).thenReturn(Optional.of(record));
        when(expenseRepository.findById(savedExpense.getId())).thenReturn(Optional.of(savedExpense));

        ExpenseResponse response = expenseService.createExpense(validRequest, idempotencyKey);

        assertThat(response.getId()).isEqualTo(savedExpense.getId());
        verify(expenseRepository, never()).save(any());       // NO insert
        verify(idempotencyRepository, never()).save(any());   // NO new record
    }

    @Test
    @DisplayName("Duplicate key but expense missing: throws IllegalStateException (data integrity)")
    void createExpense_duplicateKey_missingExpense_throwsISE() {
        IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, UUID.randomUUID());
        when(idempotencyRepository.findById(idempotencyKey)).thenReturn(Optional.of(record));
        when(expenseRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.createExpense(validRequest, idempotencyKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data integrity");
    }

    @Test
    @DisplayName("No idempotency key: creates expense without recording a key")
    void createExpense_noKey_createsWithoutIdempotencyRecord() {
        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        expenseService.createExpense(validRequest, null);

        verify(expenseRepository, times(1)).save(any());
        verify(idempotencyRepository, never()).save(any()); // no key → no record
    }

    @Test
    @DisplayName("Blank idempotency key: treated same as no key")
    void createExpense_blankKey_treatedAsNoKey() {
        when(expenseRepository.save(any(Expense.class))).thenReturn(savedExpense);

        expenseService.createExpense(validRequest, "   ");

        verify(idempotencyRepository, never()).findById(any());
        verify(idempotencyRepository, never()).save(any());
    }

    // ─── List ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listExpenses: passes category filter to repository")
    void listExpenses_withCategory_delegatesFilter() {
        when(expenseRepository.findFiltered("Food")).thenReturn(List.of(savedExpense));

        List<ExpenseResponse> result = expenseService.listExpenses("Food");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategory()).isEqualTo("Food");
        verify(expenseRepository).findFiltered("Food");
    }

    @Test
    @DisplayName("listExpenses: blank category becomes null (return all)")
    void listExpenses_blankCategory_passesNull() {
        when(expenseRepository.findFiltered(null)).thenReturn(List.of(savedExpense));

        expenseService.listExpenses("  ");

        verify(expenseRepository).findFiltered(null);
    }

    // ─── Summary ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSummary: aggregates category breakdown and computes grand total")
    void getSummary_computesGrandTotal() {
        List<CategoryTotal> breakdown = List.of(
                new CategoryTotal("Food", new BigDecimal("4500.00"), 12L),
                new CategoryTotal("Transport", new BigDecimal("1200.00"), 5L)
        );
        when(expenseRepository.getCategorySummary()).thenReturn(breakdown);

        ExpenseSummaryResponse summary = expenseService.getSummary();

        assertThat(summary.getGrandTotal()).isEqualByComparingTo("5700.00");
        assertThat(summary.getTotalCount()).isEqualTo(17L);
        assertThat(summary.getCategoryBreakdown()).hasSize(2);
    }

    @Test
    @DisplayName("getSummary: returns zero totals when no expenses exist")
    void getSummary_empty_returnsZeroes() {
        when(expenseRepository.getCategorySummary()).thenReturn(List.of());

        ExpenseSummaryResponse summary = expenseService.getSummary();

        assertThat(summary.getGrandTotal()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalCount()).isZero();
        assertThat(summary.getCategoryBreakdown()).isEmpty();
    }
}
