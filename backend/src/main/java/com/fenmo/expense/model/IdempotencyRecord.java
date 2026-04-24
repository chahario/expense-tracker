package com.fenmo.expense.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Tracks processed idempotency keys.
 *
 * Lifecycle:
 *   INSERT  → when a new expense is created (same transaction as the expense).
 *   SELECT  → on every POST /expenses to check for duplicates.
 *   DELETE  → by {@link com.fenmo.expense.scheduler.IdempotencyCleanupScheduler}
 *             when {@code expires_at < NOW()}.
 *
 * The 48-hour TTL matches common API gateway standards (Stripe uses 24h).
 * Retries beyond 48h are treated as new requests. This is documented in the README.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    private static final long TTL_HOURS = 48;

    @Id
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "processed_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant processedAt;

    /** Keys older than this are eligible for deletion by the cleanup scheduler. */
    @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant expiresAt;

    public IdempotencyRecord(String key, UUID expenseId) {
        this.idempotencyKey = key;
        this.expenseId = expenseId;
        this.processedAt = Instant.now();
        this.expiresAt = Instant.now().plus(TTL_HOURS, ChronoUnit.HOURS);
    }
}
