package com.fenmo.expense.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Core domain entity — maps to the {@code expenses} table created by Flyway.
 *
 * Money: BigDecimal with NUMERIC(19,2). Never float/double.
 *   - HALF_UP rounding applied in the service layer before persist.
 *
 * UUID: PostgreSQL native UUID type. Hibernate 6 generates UUIDs in Java
 *   via GenerationType.UUID before the INSERT (avoids a DB round-trip).
 *
 * Timestamps: Instant → TIMESTAMPTZ. Always UTC internally.
 *   The client receives ISO-8601 strings; the frontend formats for display.
 */
@Entity
@Table(
    name = "expenses",
    indexes = {
        // These mirror the Flyway migration for documentation; actual indexes are DB-managed.
        @Index(name = "idx_expenses_date_desc",        columnList = "date DESC, created_at DESC"),
        @Index(name = "idx_expenses_category_lower",   columnList = "category"),
        @Index(name = "idx_expenses_category_date",    columnList = "category, date DESC")
    }
)
@Getter
@Setter
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * NUMERIC(19,2) in PostgreSQL. BigDecimal provides exact decimal arithmetic.
     * @Column precision/scale are documentation here; Flyway sets the real constraint.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 500)
    private String description;

    /** User-supplied transaction date (e.g., when they bought coffee). Not a server timestamp. */
    @Column(nullable = false)
    private LocalDate date;

    /** Server-assigned UTC timestamp. Immutable after insert. */
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
