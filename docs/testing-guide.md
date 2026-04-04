# Finance Dashboard Backend — Testing Guide

**Base URL:** `http://localhost:8081`
**Swagger UI:** `http://localhost:8081/swagger-ui.html`

---

## 1) Setup Before Testing

### Step 1 — Start the server

Use `application-dev.properties` (H2 in-memory) for local testing:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Step 2 — Create your test users

You need 3 users to cover all roles. Register one VIEWER via the public endpoint, then use an ADMIN to create the other two.

**Register VIEWER (public endpoint):**
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "name": "Viewer One",
  "email": "viewer@test.com",
  "password": "password123"
}
```

**Login as VIEWER to get token, then create an ADMIN using that token — wait, you can't. VIEWER cannot create users.**

Correct order:
1. Register → creates VIEWER
2. Manually insert an ADMIN user in DB, OR use `data.sql` on startup
3. Login as ADMIN → get admin token
4. Use admin token to POST `/api/v1/users` to create ANALYST

For H2 dev testing, create an ADMIN directly via register and then update role via DB console (`http://localhost:8081/h2-console`), or seed on startup.

### Step 3 — Get tokens for each role

Login separately for each role and keep 3 tokens ready:
```http
POST /api/v1/auth/login
Content-Type: application/json

{ "email": "admin@test.com", "password": "password123" }
```

---

## 2) Headers Reference

**Public endpoints (register, login):**
```
Content-Type: application/json
```

**Protected GET / DELETE (no body):**
```
Authorization: Bearer <token>
```

**Protected POST / PATCH (with body):**
```
Authorization: Bearer <token>
Content-Type: application/json
```

---

## 3) Complete Test Matrix

Each test shows: what to send → what you must get back.

---

### AUTH

#### A1. Register — success
```http
POST /api/v1/auth/register
{ "name": "Test User", "email": "test@test.com", "password": "password123" }
```
**Expected: 201**
```json
{ "data": { "role": "VIEWER", "status": "ACTIVE" }, "message": "User registered successfully" }
```

#### A2. Register — duplicate email
Same request as A1 again.
**Expected: 400**
```json
{ "code": "INVALID_INPUT", "message": "Email is already registered." }
```

#### A3. Register — password too short
```json
{ "name": "Test", "email": "x@test.com", "password": "abc" }
```
**Expected: 400**
```json
{ "code": "INVALID_INPUT", "message": "password: Password must be at least 8 characters" }
```

#### A4. Login — success
```json
{ "email": "test@test.com", "password": "password123" }
```
**Expected: 200** — save the `token` from response.

#### A5. Login — wrong password
```json
{ "email": "test@test.com", "password": "wrongpass" }
```
**Expected: 400**
```json
{ "code": "INVALID_INPUT", "message": "Invalid email or password." }
```

#### A6. Login — wrong email
```json
{ "email": "nobody@test.com", "password": "password123" }
```
**Expected: 400**
```json
{ "code": "INVALID_INPUT", "message": "Invalid email or password." }
```
Note: Same message as A5. Intentional — prevents figuring out whether email exists.

#### A7. Login — inactive user
Set a user's status to INACTIVE via `PATCH /api/v1/users/{id}`, then try to login.
**Expected: 403**
```json
{ "code": "USER_INACTIVE", "message": "User account is inactive. Access denied." }
```

---

### USERS (ADMIN token required for all)

#### U1. Create user — success
```http
POST /api/v1/users
Authorization: Bearer <admin_token>
{ "name": "Analyst One", "email": "analyst@test.com", "password": "password123", "role": "ANALYST" }
```
**Expected: 201** — user created with role ANALYST.

#### U2. Create user — non-ADMIN token
Use ANALYST or VIEWER token.
**Expected: 403**
```json
{ "code": "ACCESS_DENIED", "message": "You do not have permission to perform this action." }
```

#### U3. Create user — no token
**Expected: 401**
```json
{ "code": "UNAUTHORIZED", "message": "Authentication required." }
```

#### U4. Get all users
```http
GET /api/v1/users?page=0&size=10&sort=createdAt,desc
Authorization: Bearer <admin_token>
```
**Expected: 200** — paginated list of all users.

#### U5. Update user role
```http
PATCH /api/v1/users/{id}
Authorization: Bearer <admin_token>
{ "role": "VIEWER" }
```
**Expected: 200** — user role updated.

#### U6. Update user status to INACTIVE
```http
PATCH /api/v1/users/{id}
{ "status": "INACTIVE" }
```
**Expected: 200** — user deactivated. They can no longer login or use any API.

#### U7. Admin self-deactivation attempt
Use your own admin user's ID:
```http
PATCH /api/v1/users/{your_own_admin_id}
{ "status": "INACTIVE" }
```
**Expected: 409**
```json
{ "code": "INVALID_OPERATION", "message": "Admins cannot deactivate their own account." }
```

#### U8. Update user — no fields sent
```http
PATCH /api/v1/users/{id}
{}
```
**Expected: 400**
```json
{ "code": "INVALID_INPUT", "message": "At least one field (role or status) must be provided." }
```

---

### RECORDS

#### R1. Create record — ADMIN token, success
```http
POST /api/v1/records
Authorization: Bearer <admin_token>
{
  "amount": 1200.50,
  "type": "INCOME",
  "category": "Salary",
  "date": "2026-04-01",
  "notes": "April salary"
}
```
**Expected: 201** — record created with `userId` = admin's ID.

#### R2. Create record — ANALYST token, success
Same body with ANALYST token.
**Expected: 201** — record created with `userId` = analyst's ID.

#### R3. Create record — VIEWER token
Same body with VIEWER token.
**Expected: 403**
```json
{ "code": "ACCESS_DENIED", "message": "You do not have permission to perform this action." }
```

#### R4. Create record — no token
**Expected: 401**
```json
{ "code": "UNAUTHORIZED", "message": "Authentication required." }
```

#### R5. Create record — future date
```json
{ "amount": 100, "type": "INCOME", "category": "Test", "date": "2027-01-01" }
```
**Expected: 400**
```json
{ "code": "INVALID_INPUT", "message": "Date cannot be in the future." }
```

#### R6. Create record — amount zero
```json
{ "amount": 0, "type": "INCOME", "category": "Test", "date": "2026-04-01" }
```
**Expected: 400**
```json
{ "code": "INVALID_INPUT", "message": "amount: Amount must be greater than zero" }
```

#### R7. Create record — invalid type
```json
{ "amount": 100, "type": "TRANSFER", "category": "Test", "date": "2026-04-01" }
```
**Expected: 400** — `INVALID_INPUT` (TRANSFER is not a valid type).

#### R8. List records — ADMIN token
```http
GET /api/v1/records
Authorization: Bearer <admin_token>
```
**Expected: 200** — all records from all users.

#### R9. List records — ADMIN filtered by userId
```http
GET /api/v1/records?userId=2
Authorization: Bearer <admin_token>
```
**Expected: 200** — only records belonging to user ID 2.

#### R10. List records — ANALYST token
```http
GET /api/v1/records
Authorization: Bearer <analyst_token>
```
**Expected: 200** — only analyst's own records. Even if `?userId=1` is passed, it returns own records only.

#### R11. List records — VIEWER token
```http
GET /api/v1/records
Authorization: Bearer <viewer_token>
```
**Expected: 403**
```json
{ "code": "ACCESS_DENIED", "message": "You do not have permission to perform this action." }
```

#### R12. Get record by ID — owner
```http
GET /api/v1/records/{id}
Authorization: Bearer <analyst_token>   (analyst who owns this record)
```
**Expected: 200** — record returned.

#### R13. Get record by ID — non-owner (ANALYST accessing another user's record)
```http
GET /api/v1/records/{id_of_admin_record}
Authorization: Bearer <analyst_token>
```
**Expected: 403**
```json
{ "code": "ACCESS_DENIED", "message": "Access denied. You can only access your own records." }
```

#### R14. Get record by ID — ADMIN accessing any record
**Expected: 200** — ADMIN bypasses ownership check.

#### R15. Get record by ID — VIEWER token
**Expected: 403** — `ACCESS_DENIED`

#### R16. Get record by ID — soft-deleted record
First delete a record with ADMIN, then try to GET it.
**Expected: 404**
```json
{ "code": "NOT_FOUND", "message": "Record not found." }
```

#### R17. Update record — ADMIN, all fields
```http
PATCH /api/v1/records/{id}
Authorization: Bearer <admin_token>
{ "amount": 1500.00, "type": "EXPENSE", "category": "Food", "date": "2026-03-31", "notes": "Updated" }
```
**Expected: 200** — all fields updated.

#### R18. Update record — ANALYST, allowed fields
```http
PATCH /api/v1/records/{id}
Authorization: Bearer <analyst_token>
{ "category": "Food", "notes": "Team lunch" }
```
**Expected: 200** — category and notes updated.

#### R19. Update record — ANALYST, restricted field (amount)
```http
PATCH /api/v1/records/{id}
Authorization: Bearer <analyst_token>
{ "amount": 9999.00 }
```
**Expected: 403**
```json
{ "code": "ACCESS_DENIED", "message": "Analysts cannot modify financial truth fields: amount, type, or date." }
```

#### R20. Update record — ANALYST, restricted field (type)
```json
{ "type": "EXPENSE" }
```
**Expected: 403** — same message as R19.

#### R21. Update record — ANALYST, restricted field (date)
```json
{ "date": "2026-03-01" }
```
**Expected: 403** — same message as R19.

#### R22. Update record — already soft-deleted
Delete a record first, then PATCH it.
**Expected: 409**
```json
{ "code": "INVALID_OPERATION", "message": "Operation not allowed. This record has already been deleted." }
```

#### R23. Delete record — ADMIN
```http
DELETE /api/v1/records/{id}
Authorization: Bearer <admin_token>
```
**Expected: 200**
```json
{ "message": "Record deleted successfully" }
```

#### R24. Delete same record again
**Expected: 409**
```json
{ "code": "INVALID_OPERATION", "message": "Operation not allowed. This record has already been deleted." }
```

#### R25. Delete record — ANALYST token
**Expected: 403** — `ACCESS_DENIED`

#### R26. Delete record — VIEWER token
**Expected: 403** — `ACCESS_DENIED`

---

### DASHBOARD

#### D1. Summary — VIEWER (own data, no records yet)
```http
GET /api/v1/dashboard/summary
Authorization: Bearer <viewer_token>
```
**Expected: 200**
```json
{ "data": { "totalIncome": 0, "totalExpense": 0, "netBalance": 0 }, "message": "Success" }
```

#### D2. Summary — ANALYST (own records)
**Expected: 200** — totals for analyst's own records only.

#### D3. Summary — ADMIN (all users)
**Expected: 200** — totals across all users.

#### D4. Summary — ADMIN filtered by userId
```http
GET /api/v1/dashboard/summary?userId=2
Authorization: Bearer <admin_token>
```
**Expected: 200** — totals for user ID 2 only.

#### D5. Summary — ANALYST passing userId param (should be ignored)
```http
GET /api/v1/dashboard/summary?userId=1
Authorization: Bearer <analyst_token>
```
**Expected: 200** — returns analyst's own data, NOT user 1's data. The `userId` param is silently ignored.

#### D6. Summary — after soft-deleting a record
Delete a record, then check summary. Deleted record's amount must NOT be included.
**Expected: 200** — totals exclude deleted records.

#### D7. Category breakdown — no records
**Expected: 200** — `"data": []`

#### D8. Monthly trend — no records
**Expected: 200** — `"data": []`

#### D9. No token on dashboard
**Expected: 401** — `UNAUTHORIZED`

---

## 4) Key Things to Verify

These are the most important behaviours to confirm work correctly:

1. **401 vs 403 are distinct** — no token = 401, valid token + wrong role = 403
2. **VIEWER cannot access any record endpoint** — all 5 record endpoints return 403 for VIEWER
3. **ANALYST is scoped to own records** — cannot see, update, or access other users' records
4. **ANALYST field restriction** — amount, type, date all return 403
5. **Soft delete is invisible** — deleted record returns 404 on GET, 409 on DELETE again, excluded from dashboard
6. **Dashboard userId param ignored for non-ADMIN** — passing `?userId=1` as ANALYST returns own data
7. **Login anti-enumeration** — wrong email and wrong password return identical message
8. **Admin self-deactivation blocked** — 409, not 403

---

## 5) Quick cURL Reference

```bash
# Register
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"test@test.com","password":"password123"}'

# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password123"}'

# Create record
curl -X POST http://localhost:8081/api/v1/records \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"amount":1000,"type":"INCOME","category":"Salary","date":"2026-04-01"}'

# List records with filter
curl -X GET "http://localhost:8081/api/v1/records?type=INCOME&page=0&size=10" \
  -H "Authorization: Bearer <token>"

# Dashboard summary
curl -X GET http://localhost:8081/api/v1/dashboard/summary \
  -H "Authorization: Bearer <token>"
```