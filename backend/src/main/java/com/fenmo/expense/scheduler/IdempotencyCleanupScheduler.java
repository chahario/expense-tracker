package com.fenmo.expense.scheduler;

import com.fenmo.expense.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Background job that deletes expired idempotency records.
 *
 * Without this, the {@code idempotency_records} table would grow unboundedly.
 * Records older than 48h (the TTL set in IdempotencyRecord) are safe to delete
 * because no legitimate retry would arrive that late.
 *
 * Schedule: runs at the top of every hour (cron = "0 0 * * * *").
 * At-most-one execution per node — acceptable since the table has a Postgres index
 * on expires_at and the delete is cheap even if re-run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupScheduler {

    private final IdempotencyRepository idempotencyRepository;

    @Scheduled(cron = "0 0 * * * *")  // Every hour, at :00
    @Transactional
    public void deleteExpiredKeys() {
        Instant now = Instant.now();
        log.debug("Running idempotency cleanup at {}", now);

        int deleted = idempotencyRepository.deleteExpiredBefore(now);

        if (deleted > 0) {
            log.info("Idempotency cleanup: deleted {} expired record(s)", deleted);
        } else {
            log.debug("Idempotency cleanup: nothing to delete");
        }
    }
}
