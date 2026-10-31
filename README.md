# Olgax Invoice — Module 1

Secure authentication, risk-based login, face enrollment, organization membership, step-up verification, and security auditing.

## Stack and package layout

- Java 17 bytecode, Spring Boot 3.2, Gradle, Spring Security, Spring Data JPA, Bean Validation
- MySQL 8 and Flyway; Hibernate schema mode is `validate`
- JWT access tokens, opaque refresh tokens, BCrypt passwords
- AES-GCM encryption for stored face embeddings and organization bank details
- springdoc Swagger UI at `/swagger-ui.html`

Package root: `com.olgav.invoicesecurity`.

```text
config/       security, async mail, and OpenAPI configuration
controller/   REST controllers
dto/          request, response, and error-envelope types
exception/    global REST exception handling
model/        JPA entities and role/action enums
repository/   Spring Data repositories
security/     JWT filter, AES-GCM converter, step-up aspect, rate limiter
service/      authentication, face, risk, organization, audit, and mail services
resources/db/migration/
              V1 core schema, V2 security schema, V3 demo seed, V4 email OTP
```

## Configuration and secrets

All operational credentials are environment variables. Do not commit `.env`; copy [.env.example](./.env.example) to `.env` for Docker and replace every placeholder.

Generate independent 32-byte keys on Windows PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

Use one value for `JWT_SECRET` and a different value for `AES_GCM_KEY`. A key must remain stable for the life of encrypted database values; rotating the AES key requires decrypting and re-encrypting existing values.

Set `OTP_HMAC_SECRET` to a separate, stable random secret of at least 32 UTF-8 bytes. It is used only to HMAC email-verification OTPs together with the user ID; never reuse the JWT or AES key. Generate a value with:

```powershell
$bytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

`OTP_HMAC_SECRET` is required by Docker Compose and must remain stable while outstanding verification codes exist. `DEMO_BCRYPT_PASSWORD_HASH` is a BCrypt hash supplied by the deployer and used for the five sample users seeded by V3. There is intentionally no known/default demo password in source. Supply a cost-12-or-higher BCrypt hash before the first migration. If omitted in a non-Docker local run, the seed users receive an unusable placeholder hash; update the seed credentials through a controlled provisioning process before enabling them.

Profiles:

- `dev` is the default profile. Database, mail, JWT, AES-GCM, and origin settings can be overridden with environment variables.
- `prod` requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and still requires the JWT/AES keys for token issuance and encrypted-value use.
- Security thresholds and durations are in [application.yml](./src/main/resources/application.yml): face sample count and similarity, face challenge TTL, login risk thresholds, lockout duration, step-up TTL, and high-value invoice amount.

## Run locally (Windows)

Prerequisites: JDK 17+, MySQL 8, a database named `olgax_invoice`, and valid environment variables. The project can be built using a newer JDK but emits Java 17 bytecode.

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/olgax_invoice?serverTimezone=UTC"
$env:DB_USERNAME = "invoice_app"
$env:DB_PASSWORD = "<your-local-db-password>"
$env:JWT_SECRET = "<base64-encoded-random-32-byte-key>"
$env:AES_GCM_KEY = "<different-base64-encoded-random-32-byte-key>"
$env:OTP_HMAC_SECRET = "<separate-random-secret-of-at-least-32-characters>"
$env:DEMO_BCRYPT_PASSWORD_HASH = "<your-bcrypt-hash>"
$env:SMTP_HOST = "localhost"
$env:SMTP_PORT = "1025"
.\gradlew.bat bootRun
```

Flyway automatically applies `V1__core_schema.sql`, `V2__security_schema.sql`, `V3__seed.sql`, and `V4__email_otp.sql` on first startup. The target schema must be empty. The earlier Hibernate `ddl-auto=update` schema is not silently baselined: back it up and provision a new database, or prepare and review a data-preserving migration before pointing this app at it. The server listens on port `8080`.

## Run with Docker Compose

```powershell
Copy-Item .env.example .env
# Edit .env and replace every REPLACE_WITH value.
docker compose up --build
```

The app waits for MySQL health before starting. It is available at `http://localhost:8080`; database state is in the `mysql_data` named volume. Configure a reachable SMTP server in `.env` if verification, recovery, invitation, and security-alert emails must be delivered.

## Quick API examples

Register and sign in using `curl.exe`:

```powershell
curl.exe -X POST http://localhost:8080/api/v1/auth/register `
  -H "Content-Type: application/json" `
  -d '{"fullName":"Alex Example","email":"alex@example.com","password":"Strong-Pass123!","organizationName":"Example Org"}'

# Submit the 6-digit code from email:
curl.exe -X POST http://localhost:8080/api/v1/auth/verify-email `
  -H "Content-Type: application/json" `
  -d '{"email":"alex@example.com","otp":"123456"}'

# If needed, request a resend (60-second cooldown, maximum 5 sends per hour):
curl.exe -X POST http://localhost:8080/api/v1/auth/resend-verification `
  -H "Content-Type: application/json" `
  -d '{"email":"alex@example.com"}'

# After verification:
curl.exe -X POST http://localhost:8080/api/v1/auth/login `
  -H "Content-Type: application/json" `
  -d '{"email":"alex@example.com","password":"Strong-Pass123!","deviceHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}'
```

Authenticated bank-detail update:

```powershell
# First call /auth/step-up/initiate with {"action":"CHANGE_BANK_DETAILS"}.
# Submit the returned nonce and a current 128-float descriptor to /auth/step-up/verify.
curl.exe -X PUT http://localhost:8080/api/v1/organizations/bank-details `
  -H "Authorization: Bearer <access-token>" `
  -H "X-StepUp-Token: <one-time-step-up-token>" `
  -H "Content-Type: application/json" `
  -d '{"details":"{\"accountNumber\":\"example\"}"}'
```

## Test and build

```powershell
.\gradlew.bat test
.\gradlew.bat clean bootJar
```

The automated suite uses H2 for tests and exercises the application context, risk scoring, face-embedding comparison, step-up token consumption, and an HTTP login → step-up → protected bank-details flow. Production uses MySQL and Flyway.

## Security behavior and teammate integration

- Access JWTs expire after 15 minutes by default. Refresh tokens are random opaque values; only their SHA-256 hashes are stored. Refresh rotates and revokes the prior token.
- Password reset tokens remain one-time, expiring SHA-256 hashes. Email verification uses a six-digit OTP stored only as an HMAC-SHA256 hash keyed by `OTP_HMAC_SECRET` and the user ID; OTPs expire after 10 minutes, allow five incorrect attempts, and can be resent after a 60-second cooldown up to five times per hour.
- Face requests accept 128-value client-computed descriptors. Face samples are encrypted at rest; image data is not accepted or stored. Each verification requires a server challenge nonce and an ISO-8601 timestamp. Challenges expire and allow at most five attempts.
- A first login during the first 24 hours after registration is allowed to complete setup (including face enrollment). Later medium/high-risk logins require a face challenge. A very high score or repeated password failures causes temporary lockout.
- Login/face endpoints have an in-memory per-IP limit of 10 requests per minute per process. For horizontally scaled production, put a shared rate limiter at the gateway or replace the in-process limiter with a shared store.
- `UnknownGeoLocationService` is a replaceable provider that returns unknown location; implement `GeoLocationService` with a trusted GeoIP source to use country/city signals. Never trust arbitrary `X-Forwarded-For` values without configuring a trusted proxy.
- Mail sends asynchronously and retries three times; exhausted delivery failures are logged. The API does not reveal whether a password-reset email address exists.

To protect a teammate endpoint:

```java
@RequiresStepUp(action = SensitiveAction.CHANGE_BANK_DETAILS)
@PutMapping("/payment-settings")
public ApiResponse<?> updateSettings(...) { ... }
```

The caller first uses `POST /api/v1/auth/step-up/initiate`, completes the challenge at `/verify`, then sends the returned `stepUpToken` as `X-StepUp-Token`. The aspect checks the current user, action, expiry, and single-use state before the endpoint proceeds. Tokens are consumed in an independent transaction, so an endpoint failure does not make the same token reusable.

## API and frontend handover

See [API_CONTRACT.md](./API_CONTRACT.md) for roles, request and response shapes, error cases, examples, and the Figma-page mapping. Swagger UI is available at `/swagger-ui.html`; requests use the standard `{ "success": true, "data": ..., "error": null }` envelope (errors use `{ "success": false, "data": null, "error": { "code": "...", "message": "..." } }`).

## Assumptions and boundaries

- New registrations create one organization and make the registrant its `ORG_ADMIN`; invitees are accepted through one-time email links.
- `users.system_role` is separate from `organization_members.role`; the current-user organization is the account’s home organization. Membership rows support organization-specific roles.
- Sample seeds are only for demonstrations and must be provisioned with a private BCrypt hash. The external face provider is intentionally a profile-gated stub; only the client-embedding implementation is active by default.
- GeoIP lookup is deliberately mockable and returns `UNKNOWN` until a provider is configured. Impossible-travel detection depends on a real location provider and previously recorded locations.
- Invoice approval is protected by the configured high-value threshold; invoice records are scoped to the organization. Large-refund behavior is reserved for a payment module that owns refund processing.
