# Finance Dashboard Backend

A backend system for a multi-role finance dashboard built as part of the Zorvyn FinTech Backend Developer Internship assignment.

The system manages financial records and users across three roles — Admin, Analyst, and Viewer — with JWT-based authentication, role-based access control enforced at the service layer, field-level restrictions, soft delete, and aggregated dashboard APIs.

---

## Live Demo

- **API Base URL:** https://finance-dashboard-backend-1-mh9y.onrender.com
- **Swagger UI:** https://finance-dashboard-backend-1-mh9y.onrender.com/swagger-ui.html

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.x |
| Security | Spring Security + JWT (JJWT 0.11.5) |
| Persistence | Spring Data JPA + Hibernate |
| Database (prod) | MySQL 8 |
| Database (dev) | H2 in-memory (MODE=MySQL) |
| Validation | Jakarta Bean Validation |
| API Docs | Springdoc OpenAPI / Swagger UI |
| Build | Maven |
| Utilities | Lombok |

---

## Running the Project

### Option 0 - Docker Compose (MySQL + app)

```bash
cp .env.example .env
docker compose up --build
```

Server starts at `http://localhost:8081`

### Option 1 — Local dev with H2 (recommended, no DB setup needed)

```bash
git clone <repo-url>
cd finance-dashboard-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Server starts at `http://localhost:8081`

H2 console available at `http://localhost:8081/h2-console`
- JDBC URL: `jdbc:h2:mem:financedb`
- Username: `sa`
- Password: *(leave blank)*

### Option 2 — MySQL (production config)

1. Create a MySQL database:
```sql
CREATE DATABASE fintech_db;
```

2. Update `application.properties` with your credentials:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/fintech_db
spring.datasource.username=<your_username>
spring.datasource.password=<your_password>
```

3. Run:
```bash
./mvnw spring-boot:run
```

Schema is auto-created on startup via `ddl-auto=update`.

### Prerequisites

- Java 17+
- Maven 3.6+ (or use the included `./mvnw` wrapper)
- MySQL 8 (only for Option 2)

---

## Swagger UI

Once the server is running:

- **Swagger UI:** `http://localhost:8081/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8081/v3/api-docs`

**To authenticate in Swagger:**
1. Call `POST /api/v1/auth/login` to get a token
2. Click the **Authorize** button (top right)
3. Enter your token (without the word Bearer — Swagger adds it)
4. All protected endpoints are now accessible

---

## First-Time Setup — Creating Users

Registration always creates a `VIEWER`. To test all roles:

1. **Register** via `POST /api/v1/auth/register` → creates a VIEWER
2. **Promote to ADMIN** via H2 console:
   ```sql
   UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';
   ```
3. **Login as ADMIN** → get admin token
4. **Create ANALYST** via `POST /api/v1/users` with admin token

---

## Data Model

Two tables. Schema is auto-created on startup.

### `users`

| Field | Type | Constraint | Notes |
|---|---|---|---|
| `id` | BIGINT | PK, auto-increment | |
| `name` | VARCHAR | NOT NULL | |
| `email` | VARCHAR | NOT NULL, UNIQUE | Uniqueness enforced at DB level |
| `password` | VARCHAR | NOT NULL | BCrypt encoded — never stored plain |
| `role` | VARCHAR | NOT NULL | Stored as string: `ADMIN`, `ANALYST`, `VIEWER` |
| `status` | VARCHAR | NOT NULL, default `ACTIVE` | `ACTIVE` or `INACTIVE` — controls all system access |
| `created_at` | TIMESTAMP | NOT NULL, immutable | Set by Hibernate on insert |
| `updated_at` | TIMESTAMP | | Updated by Hibernate on every save |

### `financial_records`

| Field | Type | Constraint | Notes |
|---|---|---|---|
| `id` | BIGINT | PK, auto-increment | |
| `amount` | DECIMAL(19,4) | NOT NULL | `BigDecimal` — never `float`/`double`. Precision 19, scale 4 for financial accuracy |
| `type` | VARCHAR | NOT NULL | `INCOME` or `EXPENSE` |
| `category` | VARCHAR(100) | NOT NULL | Max 100 characters |
| `date` | DATE | NOT NULL | Cannot be a future date — enforced at service layer |
| `notes` | TEXT | nullable | Max 500 characters |
| `user_id` | BIGINT | NOT NULL | Always set from JWT — never from request body |
| `is_deleted` | BOOLEAN | NOT NULL, default `false` | Soft delete flag — records are never hard deleted |
| `created_at` | TIMESTAMP | NOT NULL, immutable | Set by Hibernate on insert |
| `updated_at` | TIMESTAMP | | Updated by Hibernate on every save |
| `updated_by` | BIGINT | nullable | Lightweight audit — ID of the user who last modified the record |

---

## Project Structure

```
src/main/java/com/lakshaya/fintech/
├── access/                     # AccessControlService — central enforcement brain
├── auth/                       # Register, Login, JWT response
├── common/
│   ├── controller/             # HealthController — public keep-alive endpoint
│   ├── exception/              # 5 custom exceptions + GlobalExceptionHandler
│   └── response/               # ApiResponse<T> envelope
├── config/                     # SecurityConfig, SwaggerConfig
├── dashboard/                  # Summary, category breakdown, monthly trend APIs
├── record/                     # Financial record CRUD + filtering
├── security/
│   ├── auth/                   # AuthEntryPoint, CustomAccessDeniedHandler, SecurityUtils
│   └── jwt/                    # JwtUtil, JwtFilter
└── user/                       # User management (ADMIN only)
```

---

## API Overview

**Base URL:** `http://localhost:8081/api/v1`

All protected endpoints require: `Authorization: Bearer <token>`

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Register as VIEWER |
| POST | `/auth/login` | Public | Get JWT token |
| POST | `/users` | ADMIN | Create user with any role |
| GET | `/users` | ADMIN | List all users (paginated) |
| PATCH | `/users/{id}` | ADMIN | Update user role or status |
| POST | `/records` | ADMIN, ANALYST | Create financial record |
| GET | `/records` | ADMIN, ANALYST | List records with filters |
| GET | `/records/{id}` | ADMIN, ANALYST | Get single record |
| PATCH | `/records/{id}` | ADMIN, ANALYST | Partial update record |
| DELETE | `/records/{id}` | ADMIN | Soft delete record |
| GET | `/dashboard/summary` | All roles | Total income, expense, net balance |
| GET | `/dashboard/category-breakdown` | All roles | Totals grouped by category |
| GET | `/dashboard/monthly-trend` | All roles | Income and expense per month |
| GET | `/health` | Public | Uptime check / cron job keep-alive |

### Record Filtering

`GET /api/v1/records` supports the following query parameters — all optional, all combinable:

```
type=INCOME             filter by INCOME or EXPENSE
category=Salary         filter by category (exact match)
startDate=2026-01-01    filter from date (ISO format YYYY-MM-DD)
endDate=2026-04-01      filter to date (ISO format YYYY-MM-DD)
userId=2                ADMIN only — filter by specific user (silently ignored for ANALYST)
page=0                  page number (default: 0)
size=10                 page size (default: 10, max: 50)
sort=createdAt,desc     sort field and direction
```

Soft-deleted records are always excluded. Non-ADMIN users are always scoped to their own records regardless of any `userId` param passed.

Full request/response details: [`docs/api-documentation.md`](docs/api-documentation.md)

---

## Role System

| Action | ADMIN | ANALYST | VIEWER |
|---|---|---|---|
| Create record | ✅ | ✅ | ❌ 403 |
| Read records | ✅ All users | ✅ Own only | ❌ 403 |
| Update record (category, notes) | ✅ | ✅ Own only | ❌ 403 |
| Update record (amount, type, date) | ✅ | ❌ 403 | ❌ 403 |
| Delete record (soft delete) | ✅ | ❌ 403 | ❌ 403 |
| Dashboard | ✅ All / filter by user | ✅ Own only | ✅ Own only |
| User management | ✅ | ❌ 403 | ❌ 403 |

### User Status (ACTIVE / INACTIVE)

Every user has a status. An `INACTIVE` user:
- Cannot log in — token is never issued
- Cannot call any API endpoint even with a valid existing token
- Is blocked at the first line of every service method before any operation executes

An ADMIN can deactivate any user via `PATCH /api/v1/users/{id}`. An ADMIN cannot deactivate their own account — the system blocks this with `409 INVALID_OPERATION` to prevent permanent lockout.

---

## Key Design Decisions

### 1. Access control enforced at the service layer, not the controller

Controllers carry only a coarse `@PreAuthorize` role gate. All real enforcement — active-user check, ownership, field restrictions — lives in `AccessControlService`. This ensures the rules apply regardless of how a service method is called, and makes the enforcement logic testable in isolation without a Spring context.

### 2. VIEWER cannot access record endpoints

The assignment states VIEWER can "only view dashboard data." Giving VIEWER read access to record endpoints would be logically inconsistent — they can never create records, so they would always receive an empty list. VIEWER's use case is the dashboard, so all record endpoints return `403` for VIEWER.

### 3. ANALYST cannot modify amount, type, or date — only category and notes

These three fields are "financial truth" — the factual record of what the transaction was. Allowing an analyst to change the amount or type of a transaction would compromise the integrity of the financial data. Category and notes are metadata that can legitimately be corrected without affecting the financial record itself.

### 4. Soft delete only — no hard delete

Records are marked `isDeleted = true` rather than physically removed. This preserves the audit trail and prevents accidental permanent data loss. Soft-deleted records are invisible in all queries, list endpoints, and dashboard aggregations, but the data is retained in the database.

### 5. ADMIN creates records only for themselves

The request body for `POST /records` contains no `userId` field. The `userId` is always taken from the JWT token. This prevents cross-user record assignment and keeps ownership unambiguous — whoever's token is used owns the record.

### 6. Login returns the same error for wrong email and wrong password

Both cases return `400 INVALID_INPUT` with the identical message `"Invalid email or password."` This prevents user enumeration — an attacker cannot tell whether an email exists in the system based on which error they receive.

### 7. Dashboard aggregation at the database level

Dashboard queries use `SUM`, `GROUP BY`, and `COALESCE` at the SQL level. No records are loaded into application memory for calculation. This keeps dashboard performance consistent regardless of record volume.

### 8. userId scope resolved server-side — cannot be bypassed

Non-ADMIN users always see only their own data. Even if a non-ADMIN passes `?userId=1` on a list or dashboard endpoint, `AccessControlService.resolveUserIdScope()` silently replaces it with the authenticated user's own ID. The parameter is accepted but ignored — no 400 error, no data leakage.

---

## Error Response Format

All errors follow a consistent envelope:

```json
{
  "code": "ACCESS_DENIED",
  "message": "Analysts cannot modify financial truth fields: amount, type, or date."
}
```

| HTTP | Code | When |
|---|---|---|
| 400 | `INVALID_INPUT` | Validation failure, bad field value, future date, invalid enum value |
| 401 | `UNAUTHORIZED` | Missing, expired, or malformed token |
| 403 | `ACCESS_DENIED` | Wrong role or accessing another user's record |
| 403 | `USER_INACTIVE` | Account deactivated by admin |
| 404 | `NOT_FOUND` | Record or user does not exist (or is soft-deleted) |
| 409 | `INVALID_OPERATION` | Operating on deleted record, admin self-deactivation |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

Error messages are specific. For example, sending `"type": "TRANSFER"` returns:

```json
{
  "code": "INVALID_INPUT",
  "message": "Invalid value 'TRANSFER' for field 'type'. Allowed values are: INCOME, EXPENSE."
}
```

---

## Tests

```bash
./mvnw test
```

19 unit tests covering `AccessControlService` (the enforcement brain) and `AuthService`:

- `checkUserActive` — null user, active passes, inactive throws
- `checkOwnership` — ADMIN bypass, owner passes, non-owner throws
- `checkFieldAccess` — ANALYST blocked on amount/type/date, allowed on category/notes
- `checkRecordState` — active passes, deleted throws 409
- `resolveUserIdScope` — non-ADMIN always own ID, ADMIN passes through filter, invalid ID throws
- `register` — VIEWER role enforced, password encoded, duplicate email blocked
- `login` — valid credentials, wrong credentials (anti-enumeration), inactive user blocked before token issuance

`AccessControlService` has zero dependencies so tests run as pure Java with no Spring context — fast and isolated.

---

## Assumptions

- Every user who registers gets `VIEWER` role. Role elevation is done by an ADMIN after the fact.
- The system does not support assigning a record to a different user. Whoever's token is used to create the record owns it.
- Soft-deleted records are permanently invisible. There is no restore or undelete endpoint.
- Pagination defaults to page size 10, maximum 50, configurable via `page`, `size`, and `sort` query params.
- JWT tokens are valid for 24 hours (`86400000ms`). There is no refresh token endpoint.
- The `notes` field on a record is optional and capped at 500 characters.
- Dashboard returns zeros (not errors) when a user has no records.
- "Recent activity" (listed as a dashboard example in the assignment) was not implemented. The three dashboard endpoints — summary, category breakdown, and monthly trend — cover the core analytics use case.

---

## Tradeoffs

**Feature-based package structure over layered structure**
The project uses `auth/`, `record/`, `user/`, `dashboard/` packages instead of a flat `controller/`, `service/`, `repository/` layout. Feature-based packages keep related files together and make the codebase easier to navigate as it grows. The tradeoff is slightly more cross-package imports.

**`BigDecimal` over `double` for amount**
Financial amounts use `BigDecimal(precision=19, scale=4)` throughout — entity, DTO, and all aggregation queries. Using `double` would introduce floating-point precision errors on financial calculations, which is unacceptable in a finance system.

**No refresh token**
JWT tokens expire after 24 hours and there is no refresh mechanism. This simplifies the auth flow significantly. For a production system, a refresh token with a longer TTL would be needed.

**No rate limiting**
Rate limiting was not implemented as it was listed as an optional enhancement and would require additional infrastructure (Redis or an in-memory store). The foundation is clean enough to add it later.

**H2 with MODE=MySQL for dev**
The monthly trend query uses MySQL's `DATE_FORMAT` function. Rather than writing two separate queries, `application-dev.properties` runs H2 in `MODE=MySQL` so the same native query works in both environments without any code changes.

---

## Docs

- [`docs/api-documentation.md`](docs/api-documentation.md) — full API reference with request/response examples for every endpoint
- [`docs/testing-guide.md`](docs/testing-guide.md) — 26 manual test scenarios with exact expected responses for Postman