# Module 1 REST API Contract

Base URL: `http://localhost:8080/api/v1`. JSON responses are wrapped in:

```json
{ "success": true, "data": {}, "error": null }
```

Errors are wrapped as:

```json
{ "success": false, "data": null, "error": { "code": "FORBIDDEN", "message": "..." } }
```

Authenticated endpoints require `Authorization: Bearer <accessToken>`. Organization roles are `ORG_ADMIN`, `ACCOUNTANT`, `STAFF`, and `VIEWER`; `SUPER_ADMIN` is the system role. `X-StepUp-Token` is required when stated.

## Authentication

| Method and path | Role | Request DTO and validation | Success response | Common errors |
|---|---|---|---|---|
| `POST /auth/register` | Public | `RegisterRequest`: fullName, email, password (12–72 chars; upper/lower/digit/symbol; no whitespace), organizationName | `201`; `data.status = VERIFICATION_EMAIL_SENT`; emails a 6-digit code valid for 10 minutes | `400 VALIDATION_ERROR`, `409 CONFLICT` |
| `POST /auth/verify-email` | Public; rate limited per IP and email | `{ "email": "alex@example.com", "otp": "123456" }` | `EMAIL_VERIFIED`; code is single-use | `400 INVALID_OTP`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`; `429 RATE_LIMITED` |
| `POST /auth/resend-verification` | Public; rate limited per IP and email | `{ "email": "alex@example.com" }` | Always `VERIFICATION_REQUEST_ACCEPTED`; if eligible, sends a new code. Enforces a 60-second cooldown and at most 5 sends per hour. | `400 VALIDATION_ERROR`, `429 RATE_LIMITED` |
| `POST /auth/login` | Public; rate limited | `LoginRequest`: email, password, optional 64-character hexadecimal SHA-256 deviceHash | Either tokens (`status=SUCCESS`) or a face challenge (`status=FACE_VERIFICATION_REQUIRED`, challengeId, challengeNonce, riskScore, riskReasons) | `400`, `401`, `403 EMAIL_NOT_VERIFIED` or face enrollment needed, `423` locked, `429` limited |
| `POST /auth/login/face-verify` | Public challenge; rate limited | challengeId, nonce, ISO timestamp, 128 finite floats | Rotated flow's initial access and refresh tokens | `400` expired challenge, `401` invalid face/nonce/timestamp, `429` limited |
| `POST /auth/refresh` | Public | `RefreshRequest`: refreshToken | New access and refresh tokens; submitted refresh token is revoked | `400` invalid/reused/expired token |
| `POST /auth/logout` | Authenticated | `RefreshRequest`: refreshToken owned by current user | `SIGNED_OUT` | `400` invalid token |
| `POST /auth/forgot-password` | Public | `ForgotPasswordBody`: email | Always `IF_ACCOUNT_EXISTS_EMAIL_SENT` | `400` invalid email format |
| `POST /auth/reset-password` | Public | `ResetPasswordRequest`: token, strong newPassword | `PASSWORD_RESET` | `400` invalid/expired/used token or password validation |
| `POST /auth/change-password` | Authenticated | `ChangePasswordRequest`: currentPassword, strong newPassword | `PASSWORD_CHANGED`; other refresh sessions revoked | `401` incorrect current password, `400` validation |

Successful login example:

```json
{
  "success": true,
  "data": {
    "status": "SUCCESS",
    "accessToken": "<jwt>",
    "refreshToken": "<opaque-token>",
    "tokenType": "Bearer",
    "expiresInSeconds": 900,
    "challengeId": null,
    "challengeNonce": null,
    "riskScore": null,
    "riskReasons": []
  },
  "error": null
}
```

Face challenge example:

```json
{
  "success": true,
  "data": {
    "status": "FACE_VERIFICATION_REQUIRED",
    "challengeId": "uuid",
    "challengeNonce": "<server nonce>",
    "riskScore": 55,
    "riskReasons": ["UNRECOGNIZED_DEVICE", "UNRECOGNIZED_IP"]
  },
  "error": null
}
```

Verify an email with the six-digit code:

```json
{ "email": "alex@example.com", "otp": "123456" }
```

Resend requests return the same neutral response whether or not an eligible account exists:

```json
{ "email": "alex@example.com" }
```

## Face and step-up

| Method and path | Role | Request DTO and validation | Success response | Common errors |
|---|---|---|---|---|
| `POST /face/enroll` | Authenticated | `{ "samples": [[128 floats], [128 floats], [128 floats]] }` | `FACE_ENROLLED` | `400` wrong count/descriptor, encryption configuration errors |
| `GET /face/status` | Authenticated | None | `{ "enrolled": true, "samplesStored": 3 }` | `401` |
| `DELETE /face/enrollment` | Authenticated | None | `FACE_ENROLLMENT_DELETED` | `401` |
| `POST /auth/step-up/initiate` | Authenticated | `{ "action": "CHANGE_BANK_DETAILS" }` | challengeId, nonce, action, expiresAt | `409` face not enrolled; `400` unknown action |
| `POST /auth/step-up/verify` | Authenticated | challengeId, action, nonce, ISO timestamp, 128 finite floats | `stepUpToken`, expiresAt | `400` invalid/expired/replayed challenge, `401` face mismatch |

The supported sensitive actions are `CHANGE_BANK_DETAILS`, `APPROVE_HIGH_VALUE_INVOICE`, `LARGE_REFUND`, `DELETE_INVOICE`, and `CHANGE_ORGANIZATION_ADMIN`. Tokens are valid for five minutes by default, bound to one action and user, and consumed once.

Step-up verification request:

```json
{
  "challengeId": "uuid",
  "action": "CHANGE_BANK_DETAILS",
  "nonce": "<challenge nonce>",
  "timestamp": "2026-10-26T16:30:00Z",
  "embedding": [0.01, 0.02, 0.03]
}
```

`embedding` must contain exactly 128 finite numbers; the abbreviated example above is not valid on its own.

## User, devices, and organization

| Method and path | Role | Request DTO and validation | Success response | Common errors |
|---|---|---|---|---|
| `GET /users/me` | Authenticated | None | Profile DTO (id, email, fullName, status, emailVerified, faceEnrolled) | `401`, `404` |
| `PUT /users/me` | Authenticated | `{ "fullName": "..." }`, non-empty, max 255 chars | Updated profile DTO | `400`, `404` |
| `GET /devices` | Authenticated | None | Current user's device DTO list (no raw fingerprint hashes) | `401` |
| `DELETE /devices/{id}` | Authenticated owner | None | `DEVICE_REVOKED` | `404` missing/not owned |
| `POST /devices/{id}/trust` | Authenticated owner | None | Device DTO | `404` missing/not owned |
| `GET /organizations/current` | Authenticated | None | Organization settings DTO; bank details are masked | `404` |
| `PUT /organizations/current` | `ORG_ADMIN` or `SUPER_ADMIN` | name, optional logoUrl, three-letter uppercase currency, optional taxSettings | Updated settings DTO | `400`, `403` |
| `PUT /organizations/bank-details` | `ORG_ADMIN` or `SUPER_ADMIN` + step-up | `{ "details": "<bank/payment JSON string>" }` | Updated DTO with masked bank details | `400`, `403` missing/invalid step-up |
| `GET /organizations/members` | `ORG_ADMIN` or `SUPER_ADMIN` | None | Member DTO list | `403` |
| `POST /organizations/members` | `ORG_ADMIN` or `SUPER_ADMIN` | email, fullName, optional OrgRole | Member DTO with `INVITED` status | `400`, `403`, `409` duplicate membership |
| `POST /organizations/invitations/accept` | Authenticated invited user | `{ "token": "<email invitation token>" }` | `INVITATION_ACCEPTED` | `400` invalid/expired; `409` already accepted |
| `PUT /organizations/members/{id}/role` | `ORG_ADMIN` or `SUPER_ADMIN` | `{ "role": "ACCOUNTANT" }` | Updated member DTO | `400`, `403`, `404`, `409` admin transfer required |
| `DELETE /organizations/members/{id}` | `ORG_ADMIN` or `SUPER_ADMIN` | None | `MEMBER_REMOVED` | `403`, `404`, `409` admin transfer required |
| `POST /organizations/transfer-admin` | `ORG_ADMIN` or `SUPER_ADMIN` + step-up | `{ "newAdminUserId": 42 }` | `ADMIN_TRANSFERRED` | `400`, `403`, `404` |

Invite request example:

```json
{ "email": "staff@example.com", "fullName": "Sam Staff", "role": "STAFF" }
```

## Invoices and security audit

| Method and path | Role | Request DTO and validation | Success response | Common errors |
|---|---|---|---|---|
| `POST /invoices` | `ORG_ADMIN`, `ACCOUNTANT`, `STAFF` | invoiceNumber, amount > 0, dueDate | Invoice response DTO | `400`, `403` |
| `GET /invoices` | `ORG_ADMIN`, `ACCOUNTANT`, `STAFF` | None | Current organization's invoice DTO list | `403` |
| `GET /invoices/{id}` | `ORG_ADMIN`, `ACCOUNTANT`, `STAFF` | None | Invoice DTO | `404` not found in current org |
| `POST /invoices/{id}/approve` | `ORG_ADMIN` or `ACCOUNTANT`; high value additionally requires step-up | Optional `X-StepUp-Token` for invoice amount >= configured threshold | Approved invoice DTO | `403` role/step-up |
| `DELETE /invoices/{id}` | `ORG_ADMIN` or `ACCOUNTANT` + step-up | `X-StepUp-Token` for `DELETE_INVOICE` | `INVOICE_DELETED` | `403`, `404` |
| `GET /audit-logs` | `ORG_ADMIN` or `SUPER_ADMIN` | Optional from/to ISO timestamps, userId, action, verificationResult, page, size (max 100) | Paginated audit DTOs | `400`, `403` |
| `GET /security/dashboard-summary` | `ORG_ADMIN` or `SUPER_ADMIN` | None | 7-day login, failed-login, face-success, high-risk, step-up, risky-user/device aggregates | `403` |
| `GET /security/risky-logins` | `ORG_ADMIN` or `SUPER_ADMIN` | Optional page and size (max 100) | Risky login DTO list | `403` |

High-value approval request:

```http
POST /api/v1/invoices/17/approve
Authorization: Bearer <accessToken>
X-StepUp-Token: <token bound to APPROVE_HIGH_VALUE_INVOICE>
```

## Frontend page handover

| Figma page | Endpoints | Fields / UI states |
|---|---|---|
| Login (face step) | `/auth/login`, `/auth/login/face-verify`, `/auth/refresh`, `/auth/logout` | Send email/password/deviceHash. On `SUCCESS`, persist tokens according to frontend security policy. On `FACE_VERIFICATION_REQUIRED`, capture a fresh face descriptor, challengeNonce, and current ISO timestamp; render face retry and rate-limit errors. |
| Register | `/auth/register`, `/auth/verify-email`, `/auth/resend-verification` | Collect fullName, email, strong password, organizationName. Show verification-email-sent state; enter the 6-digit emailed OTP; provide a resend action with cooldown feedback. Login errors include `EMAIL_NOT_VERIFIED`. |
| Face Enrolment | `/face/status`, `/face/enroll`, `/face/enrollment` | Show sample count and capture exactly three 128-float descriptors; never upload/store images in this backend contract. |
| Forgot/Reset Password | `/auth/forgot-password`, `/auth/reset-password` | Forgot form always shows neutral confirmation; reset page submits token and strong new password. |
| Profile & Trusted Devices | `/users/me`, `/users/me` PUT, `/devices`, `/devices/{id}`, `/devices/{id}/trust`, `/auth/change-password` | Render profile and device list; do not display device hashes. Handle revoked/trusted states and password reauthentication errors. |
| Organization Settings & Members | `/organizations/current`, `/organizations/current` PUT, `/organizations/bank-details`, `/organizations/members`, member role/delete/invite routes, invitation acceptance, `/organizations/transfer-admin`, `/auth/step-up/initiate`, `/auth/step-up/verify` | Non-admins get forbidden UI. Bank details/admin transfer first request an action-specific face step-up; pass resulting X-StepUp-Token once. Invitations move through `INVITED` then `ACTIVE`. |
| Security Audit Dashboard | `/audit-logs`, `/security/dashboard-summary`, `/security/risky-logins` | Admin-only filters, pagination, 7-day aggregates, top risky users/devices. Show loading, empty, forbidden, and API error states. |
