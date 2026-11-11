# Hostlify API

Spring Boot 3 (Java 21) backend for Hostlify, a hostel management platform with three portals: Hosteller, Staff (hostel owners and their team) and Super Admin. The web client lives in [hostlify-web](https://github.com/charan-kuramana/hostlify-web).

## Tech stack

Spring Boot 3.3 · Spring Security + JWT (JJWT) · Spring Data JPA · PostgreSQL 16 · Flyway · springdoc OpenAPI · Lombok · Razorpay · Azure Blob Storage

## Run locally

You need Docker Desktop. No local JDK is required: the API builds and runs in a JDK 21 container.

```bash
docker compose up -d            # PostgreSQL (host port 5433) + API (http://localhost:8090)
docker logs -f hostlify-api     # wait for "Started HostlifyApiApplication"
```

- Swagger UI: http://localhost:8090/swagger-ui/index.html
- Database: `localhost:5433`, database `hostlify-db`, user/password `postgres` / `postgres`
- After backend code changes: `docker compose restart api` (it recompiles on start)

To run the API from an IDE instead, start only the database (`docker compose up -d db`) and set `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/hostlify-db`.

### Demo data

The `dev` profile runs `DataSeeder`, which loads six real PG hostels in Vijayawada (names, areas, sharing types and rents from public listings; people, emails and phone numbers are fictional). Dates are relative to the day of seeding.

| Portal | Example login |
| --- | --- |
| Super Admin | superadmin@test.com |
| Staff (owner) | owner.bliss@test.com |
| Staff (manager / admin) | manager.bliss@test.com / admin.bliss@test.com |
| Hosteller | hosteller@test.com |
| Hosteller, pending | pranav.request@test.com, mounika.pending@test.com |

Every seeded account uses the password `Test@1234`.

### Useful commands

```bash
# Reset to fresh demo data
docker compose stop api
docker exec hostlify-db psql -U postgres -d hostlify-db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose start api

# Unlock accounts (5 wrong passwords lock an account for 30 minutes)
docker exec hostlify-db psql -U postgres -d hostlify-db -c "update auth_users set locked_until=null, failed_attempts=0;"

# Password-reset OTPs are logged, not emailed, in dev
docker logs hostlify-api 2>&1 | grep "OTP for"
```

## Security model

- JWT access + refresh tokens. Refresh tokens are stored hashed and revoked on logout; suspended or inactive accounts are rejected on every request.
- Staff endpoints are scoped to the hostels the staff member is assigned to (`shared/security/HostelAccess`), with sub-role rules: only Owner and Admin manage staff.
- Hostellers can only read and change their own records.
- Login locks after 5 failed attempts for 30 minutes.

## Not configured locally

Online payments need real Razorpay test keys (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`) and file uploads need Azure Blob credentials (`AZURE_BLOB_CONNECTION_STRING`). Without them the API returns a clear error instead of failing silently.
