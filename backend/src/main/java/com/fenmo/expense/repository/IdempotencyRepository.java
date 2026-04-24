package com.fenmo.expense.repository;

import com.fenmo.expense.model.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Bulk-deletes expired idempotency records.
     *
     * Called hourly by {@link com.fenmo.expense.scheduler.IdempotencyCleanupScheduler}.
     * Returns the count of deleted rows for logging purposes.
     *
     * @Modifying + @Transactional (on the scheduler) ensures this is a write operation.
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    int deleteExpiredBefore(Instant now);
}
