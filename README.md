# Expense Tracker

Full-stack personal expense tracker. Spring Boot backend + React frontend.
Built for correctness under real-world conditions (retries, duplicate submissions, page refreshes).

## Live Links

| | URL |
|---|---|
| Frontend | `https://expense-tracker-dinesh.vercel.app` |
| Backend | `https://expense-tracker.railway.app` |
| Swagger UI | `https://expense-tracker.railway.app/swagger-ui.html` |

---

## Quick Start (Local)

### Option A — Docker Compose (recommended)

```bash
cp .env.example .env          # fill in passwords if desired
docker-compose up --build     # starts postgres + backend
cd frontend && npm install && npm run dev   # starts frontend at :5173
```

### Option B — Manual

**Backend** (requires Java 17, Maven 3.8+, local PostgreSQL)
```bash
cd backend
# Create DB: createdb expenses
# Edit application-dev.properties or set env vars
mvn spring-boot:run -Dspring.profiles.active=dev
```

**Frontend**
```bash
cd frontend
npm install
npm run dev      # Vite proxy handles /expenses → localhost:8080
```

---

## API Reference

Full interactive docs at `/swagger-ui.html` when running.

### `POST /expenses`

Create an expense. Supply `Idempotency-Key` header for safe retry semantics.

```
POST /expenses
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "amount": 250.00,
  "category": "Food & Dining",
  "description": "Lunch at Punjab Grill",
  "date": "2024-04-24"
}
```

Response: `201 Created` with expense body (same on idempotent replay).

### `GET /expenses`

```
GET /expenses?category=Food&sort=date_desc
```

Returns expense array, always sorted newest-first.

### `GET /expenses/summary`

Returns per-category totals and grand total across all expenses.

### `GET /actuator/health`

Spring Boot health check. Used by Railway, docker-compose, and load balancers.

---

## Design Decisions

### 1. Idempotency via `Idempotency-Key` header

The assignment explicitly asks for correct behaviour under retries and duplicate submissions.

**Flow:**
1. Frontend generates `crypto.randomUUID()` when form initialises.
2. UUID sent as `Idempotency-Key` header on every `POST /expenses`.
3. Backend checks `idempotency_records` table.
4. Found → return original expense, skip insert (idempotent replay).
5. Not found → insert expense + insert key record **in the same transaction**.

**Frontend key lifecycle:**
- Network failure / timeout → same key reused → retry safely deduplicated by server.
- Success → new key generated for next submission.
- Double-click → `submitting` state flag + disabled button block the second request at UI level.

**TTL:** Keys expire after 48 hours (matches Stripe's policy). An hourly scheduled job
(`IdempotencyCleanupScheduler`) bulk-deletes expired records using the `idx_idempotency_expires` index.

### 2. `BigDecimal` with `NUMERIC(19,2)` for money

IEEE-754 floating-point cannot represent all decimals exactly: `0.1 + 0.2 = 0.30000000000000004`.
`BigDecimal` with `NUMERIC(19,2)` in PostgreSQL guarantees exact representation.
`RoundingMode.HALF_UP` applied before every persist (standard accounting convention).

### 3. PostgreSQL over SQLite

For a deployed, production application (not embedded/mobile):
- PostgreSQL supports concurrent writes without serialisation bottlenecks.
- `NUMERIC(19,2)` and `TIMESTAMPTZ` are first-class types.
- `gen_random_uuid()` built-in (PG 13+); no extension needed.
- Connection pooling (HikariCP) is meaningful with PG; wasted on SQLite.

### 4. Flyway for schema management

`ddl-auto=update` is dangerous in production — it can silently alter columns.
Flyway migrations (`V1__init_schema.sql`) are:
- Reviewed before deploy.
- Version-controlled.
- Applied exactly once, in order.
- Validated by Hibernate (`ddl-auto=validate`) on startup.

### 5. Category normalisation

`"food"` → `"Food"` (title-case first character). Prevents `"food"` and `"Food"` as separate categories in the summary. Simple, deterministic, no special-casing needed.

### 6. Client-side filter vs server-side

The API supports `?category=` (server-side filter, used by API clients).
The frontend fetches all records and filters client-side:
- Filter response is **instant** (no loading state on dropdown change).
- Category dropdown always shows **all** categories even when one is selected.
- Personal expense data is hundreds of records — trivial to filter in JS.

### 7. Request logging with MDC correlation ID

`RequestLoggingFilter` attaches a short UUID (`requestId`) to every request via `MDC`.
- Every log line for a request shares the same ID.
- `X-Request-Id` header on every response lets support ask users for this ID.
- Actuator `/health` polls are filtered from access logs to reduce noise.

### 8. Multi-stage Docker build

- Stage 1 (Maven): downloads deps (layer cached), builds JAR.
- Stage 2 (JRE alpine): copies only the JAR — no build tools in production image.
- Non-root user (`appuser`) for security.
- `UseContainerSupport` + `MaxRAMPercentage=75` for correct JVM memory sizing in containers.

---

## Trade-offs Made Due to Timebox

| Area | Decision | Reason |
|---|---|---|
| Auth | None | Out of scope for single-user assessment |
| Pagination | Not implemented | Personal tracker; small data set |
| Testcontainers | H2 in test profile | Avoids Docker dependency in CI |
| Category CRUD | Free-text input | Simpler; flexibility > consistency |
| Idempotency crash window | Documented | At-least-once in crash case; bounded by TTL |

## Intentionally Not Done

- User authentication and per-user data isolation
- Pagination (Pageable + cursor)
- Expense editing / deletion
- CSV export
- Idempotency key deduplication within concurrent in-flight requests (lock not needed at this scale)
- End-to-end tests (Playwright / Cypress)
