package com.fenmo.expense.repository;

import com.fenmo.expense.dto.CategoryTotal;
import com.fenmo.expense.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    /**
     * Returns expenses filtered by category (optional, case-insensitive) and
     * sorted newest-first. Secondary sort on created_at breaks ties.
     *
     * When :category is NULL the WHERE clause is vacuously true — all rows return.
     * This avoids two separate query methods and keeps the call site simple.
     *
     * Note: JPQL uses entity field names (camelCase), not column names.
     */
    @Query("""
            SELECT e FROM Expense e
            WHERE (:category IS NULL OR LOWER(e.category) = LOWER(:category))
            ORDER BY e.date DESC, e.createdAt DESC
            """)
    List<Expense> findFiltered(@Param("category") String category);

    /**
     * Returns per-category totals, sorted by total amount descending.
     *
     * Uses a JPQL constructor expression to map directly to {@link CategoryTotal}.
     * No N+1 risk — this is a single aggregation query.
     */
    @Query("""
            SELECT new com.fenmo.expense.dto.CategoryTotal(
                e.category,
                SUM(e.amount),
                COUNT(e)
            )
            FROM Expense e
            GROUP BY e.category
            ORDER BY SUM(e.amount) DESC
            """)
    List<CategoryTotal> getCategorySummary();
}
