# Finance Dashboard Backend — API Documentation

**Base URL:** `http://localhost:8081`
**API Prefix:** `/api/v1`
**Auth:** JWT Bearer Token (stateless)

---

## 1) How the System Works

### 1.1 Request Lifecycle

```
Request
  → JwtFilter          (validates token, loads User from DB, sets SecurityContext)
  → @PreAuthorize      (coarse role gate — blocks wrong role before hitting service)
  → Service            (checkUserActive → load entity → checkOwnership → checkFieldAccess → execute)
  → GlobalExceptionHandler (maps every exception to correct HTTP code + JSON)
  → ApiResponse<T>     (wraps all responses — success and error)
```

### 1.2 What each error means in Postman

| HTTP | Code | Meaning | Why you see it |
|---|---|---|---|
| 400 | `INVALID_INPUT` | Bad request data | Missing field, wrong format, amount ≤ 0, future date, notes > 500 chars |
| 401 | `UNAUTHORIZED` | No token or bad/expired token | Token missing, malformed, or expired — login again to get a fresh token |
| 403 | `ACCESS_DENIED` | Wrong role or not your record | VIEWER hitting records, ANALYST hitting amount/type/date, accessing someone else's record |
| 403 | `USER_INACTIVE` | Account deactivated | Admin set your status to INACTIVE |
| 404 | `NOT_FOUND` | Resource does not exist | Wrong ID, or record was soft-deleted |
| 409 | `INVALID_OPERATION` | Invalid state | Operating on already-deleted record, admin self-deactivation |
| 500 | `INTERNAL_ERROR` | Unexpected server error | Bug — check server logs |

### 1.3 Role Permission Matrix

| Action | ADMIN | ANALYST | VIEWER |
|---|---|---|---|
| Register / Login | ✅ | ✅ | ✅ |
| Create record | ✅ | ✅ | ❌ 403 |
| List records | ✅ all users | ✅ own only | ❌ 403 |
| Get record by ID | ✅ any record | ✅ own only | ❌ 403 |
| Update record (category, notes) | ✅ | ✅ own only | ❌ 403 |
| Update record (amount, type, date) | ✅ | ❌ 403 | ❌ 403 |
| Delete record (soft delete) | ✅ | ❌ 403 | ❌ 403 |
| Dashboard summary | ✅ all / filter userId | ✅ own only | ✅ own only |
| Dashboard category breakdown | ✅ all / filter userId | ✅ own only | ✅ own only |
| Dashboard monthly trend | ✅ all / filter userId | ✅ own only | ✅ own only |
| Create / list / update users | ✅ | ❌ 403 | ❌ 403 |

### 1.4 Response Envelope

Every response — success or error — uses this envelope:

**Success:**
```json
{
  "data": { "..."},
  "message": "Success"
}
```

**Error:**
```json
{
  "code": "ACCESS_DENIED",
  "message": "You do not have permission to perform this action."
}
```

`data` is never present on error. `code` is never present on success.

---

## 2) Auth APIs

### POST `/api/v1/auth/register`

No token required.

**Request:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123"
}
```

**Validation rules:**
- `name` — required, not blank
- `email` — required, valid email format
- `password` — required, minimum 8 characters

**Responses:**

| Status | Code | When |
|---|---|---|
| 201 | — | User created. Role is always `VIEWER`. Status is always `ACTIVE`. |
| 400 | `INVALID_INPUT` | Missing field / invalid email format / password < 8 chars / email already registered |

**201 response:**
```json
{
  "data": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "role": "VIEWER",
    "status": "ACTIVE",
    "createdAt": "2026-04-03T10:00:00",
    "updatedAt": "2026-04-03T10:00:00"
  },
  "message": "User registered successfully"
}
```

---

### POST `/api/v1/auth/login`

No token required.

**Request:**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | Login successful. Copy `token` for all protected requests. |
| 400 | `INVALID_INPUT` | Wrong email OR wrong password. Message is identical for both — intentional, prevents guessing which failed. |
| 403 | `USER_INACTIVE` | Account has been deactivated by admin. |

**200 response:**
```json
{
  "data": {
    "token": "<jwt_token>",
    "role": "VIEWER"
  },
  "message": "Login successful"
}
```

---

## 3) User Management APIs (ADMIN only)

All endpoints require `ADMIN` token. Any other role gets `403 ACCESS_DENIED`.

### POST `/api/v1/users`

Creates a user with any role. Only ADMIN can do this.

**Request:**
```json
{
  "name": "Analyst One",
  "email": "analyst1@example.com",
  "password": "password123",
  "role": "ANALYST"
}
```

Valid `role` values: `ADMIN`, `ANALYST`, `VIEWER`

**Responses:**

| Status | Code | When |
|---|---|---|
| 201 | — | User created |
| 400 | `INVALID_INPUT` | Missing field / email already registered / password < 8 chars / invalid role value |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | Token is not ADMIN |

---

### GET `/api/v1/users`

Returns paginated list of all users.

**Query params (all optional):**
```
page=0
size=10       (default: 10, max: 50)
sort=createdAt,desc
```

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | Paginated user list |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | Not ADMIN |

---

### PATCH `/api/v1/users/{id}`

Updates role and/or status. Send only the fields you want to change.

**Request (at least one field required):**
```json
{
  "role": "ANALYST",
  "status": "INACTIVE"
}
```

Valid `status` values: `ACTIVE`, `INACTIVE`

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | User updated |
| 400 | `INVALID_INPUT` | Neither `role` nor `status` provided |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | Not ADMIN |
| 404 | `NOT_FOUND` | User ID does not exist |
| 409 | `INVALID_OPERATION` | Admin trying to deactivate their own account |

---

## 4) Record APIs

### POST `/api/v1/records`

Creates a financial record. `userId` is always taken from the token — cannot be assigned to another user.

**Requires:** `ADMIN` or `ANALYST`. VIEWER gets `403 ACCESS_DENIED`.

**Request:**
```json
{
  "amount": 1200.50,
  "type": "INCOME",
  "category": "Salary",
  "date": "2026-04-01",
  "notes": "April salary"
}
```

**Validation rules:**
- `amount` — required, must be ≥ 0.01
- `type` — required, must be exactly `INCOME` or `EXPENSE`
- `category` — required, not blank, max 100 characters
- `date` — required, cannot be a future date
- `notes` — optional, max 500 characters

**Responses:**

| Status | Code | When |
|---|---|---|
| 201 | — | Record created |
| 400 | `INVALID_INPUT` | Validation failure — missing field / amount ≤ 0 / future date / notes > 500 chars / wrong type value |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | VIEWER token |
| 403 | `USER_INACTIVE` | Your account is inactive |

---

### GET `/api/v1/records`

Lists records with optional filters. Soft-deleted records are never returned.

**Requires:** `ADMIN` or `ANALYST`. VIEWER gets `403 ACCESS_DENIED`.

**Query params (all optional):**
```
userId=1              (ADMIN only — filter by user. Silently ignored for ANALYST.)
type=INCOME           (INCOME or EXPENSE)
category=Salary
startDate=2026-01-01  (ISO: YYYY-MM-DD)
endDate=2026-04-01
page=0
size=10
sort=createdAt,desc
```

**Scoping (enforced server-side, cannot be bypassed):**
- `ADMIN` → all records, optionally filtered by `userId`
- `ANALYST` → own records only, always. `userId` param is silently ignored.

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | Paginated list. `content: []` if no records match filters. |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | VIEWER token |
| 403 | `USER_INACTIVE` | Your account is inactive |

---

### GET `/api/v1/records/{id}`

Fetches a single record by ID.

**Requires:** `ADMIN` or `ANALYST`. VIEWER gets `403 ACCESS_DENIED`.

**Ownership:**
- `ADMIN` → can fetch any record
- `ANALYST` → own records only. Fetching another user's record returns `403`, not `404`.

Soft-deleted records return `404`.

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | Record returned |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | VIEWER token / ANALYST accessing another user's record |
| 403 | `USER_INACTIVE` | Your account is inactive |
| 404 | `NOT_FOUND` | ID does not exist or record is soft-deleted |

---

### PATCH `/api/v1/records/{id}`

Partial update. Send only the fields you want to change.

**Requires:** `ADMIN` or `ANALYST`. VIEWER gets `403 ACCESS_DENIED`.

**ANALYST field restrictions:**
- ✅ Can update: `category`, `notes`
- ❌ Cannot update: `amount`, `type`, `date` → `403 ACCESS_DENIED` with message `"Analysts cannot modify financial truth fields: amount, type, or date."`

**Request — ADMIN (all fields allowed):**
```json
{
  "amount": 1500.00,
  "type": "INCOME",
  "category": "Bonus",
  "date": "2026-03-31",
  "notes": "Q1 bonus"
}
```

**Request — ANALYST (restricted fields only):**
```json
{
  "category": "Food",
  "notes": "Team lunch"
}
```

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | Record updated |
| 400 | `INVALID_INPUT` | Future date provided / amount ≤ 0 |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | VIEWER token / ANALYST sending amount, type, or date / ANALYST accessing another user's record |
| 403 | `USER_INACTIVE` | Your account is inactive |
| 404 | `NOT_FOUND` | Record ID does not exist |
| 409 | `INVALID_OPERATION` | Record has already been soft-deleted |

---

### DELETE `/api/v1/records/{id}`

Soft-deletes a record (`isDeleted = true`). Record becomes invisible everywhere. Cannot be undone.

**Requires:** `ADMIN` only. ANALYST and VIEWER get `403 ACCESS_DENIED`.

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | Record soft-deleted |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `ACCESS_DENIED` | Not ADMIN |
| 403 | `USER_INACTIVE` | Your account is inactive |
| 404 | `NOT_FOUND` | Record ID does not exist |
| 409 | `INVALID_OPERATION` | Record was already deleted |

---

## 5) Dashboard APIs

All three endpoints require any valid token (ADMIN, ANALYST, or VIEWER).

**Scoping (enforced server-side, cannot be bypassed):**
- `ADMIN` → all users' data, optionally filtered by `?userId=`
- `ANALYST` → own data only. `userId` param silently ignored.
- `VIEWER` → own data only. `userId` param silently ignored.

Soft-deleted records are excluded from all aggregations.
Empty data always returns zeros or empty list — never an error.

---

### GET `/api/v1/dashboard/summary`

**Query params:**
```
userId=1    (ADMIN only — optional)
```

**200 response:**
```json
{
  "data": {
    "totalIncome": 10000.00,
    "totalExpense": 2500.00,
    "netBalance": 7500.00
  },
  "message": "Success"
}
```

No records → returns zeros, not an error:
```json
{
  "data": { "totalIncome": 0, "totalExpense": 0, "netBalance": 0 },
  "message": "Success"
}
```

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | Summary (zeros if no data) |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `USER_INACTIVE` | Your account is inactive |

---

### GET `/api/v1/dashboard/category-breakdown`

**Query params:**
```
userId=1    (ADMIN only — optional)
```

**200 response:**
```json
{
  "data": [
    { "category": "Salary", "total": 10000.00 },
    { "category": "Food", "total": 1200.00 }
  ],
  "message": "Success"
}
```

No records → `"data": []`

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | List (empty array if no data) |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `USER_INACTIVE` | Your account is inactive |

---

### GET `/api/v1/dashboard/monthly-trend`

**Query params:**
```
userId=1    (ADMIN only — optional)
```

**200 response:**
```json
{
  "data": [
    { "month": "2026-01", "income": 5000.00, "expense": 1200.00 },
    { "month": "2026-02", "income": 5000.00, "expense": 900.00 }
  ],
  "message": "Success"
}
```

Ordered by month ascending. No records → `"data": []`

**Responses:**

| Status | Code | When |
|---|---|---|
| 200 | — | List (empty array if no data) |
| 401 | `UNAUTHORIZED` | No token or token expired |
| 403 | `USER_INACTIVE` | Your account is inactive |

---

## 6) Swagger

- UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI spec: `http://localhost:8081/v3/api-docs`

Click **Authorize** → paste your JWT token → test any endpoint directly in the browser.