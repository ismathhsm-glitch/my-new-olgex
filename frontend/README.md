# Olgax Invoice · Module 1 frontend

React 18, TypeScript (strict), Vite and Tailwind implementation of the secure authentication, risk and organization-management module. The UI follows the supplied design-system screenshots and calls only endpoints documented by the checked-in backend contract.

## Run locally

1. Use Node.js 20 or later.
2. From this folder, install dependencies and copy the environment example:

   ```sh
   npm install
   copy .env.example .env
   ```

3. Ensure the Spring Boot API is available at `http://localhost:8080`. `VITE_API_URL` is the origin only; the client appends `/api/v1`.
4. Start the frontend:

   ```sh
   npm run dev
   ```

   Open `http://localhost:5173`.

Run `npm run build` for the production type-check/build and `npm run lint` for ESLint.

## Environment

| Variable | Default | Description |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | Spring Boot API origin. The REST prefix `/api/v1` is added by the client. |

The backend must allow the frontend origin (`http://localhost:5173`) through CORS.

## Session storage tradeoff

The access token is kept in application memory and is not written to browser storage. The rotating refresh token is held in `sessionStorage` so it survives a page refresh in the current tab but is cleared when that tab session ends. This is more convenient than requiring a full sign-in after every reload, but browser JavaScript can read session storage; use a backend-managed `HttpOnly`, `Secure`, `SameSite` cookie if the backend later supports that model. A single shared refresh request queues concurrent failed requests; a failed refresh clears local session state.

The browser creates a random per-browser seed in `localStorage` and sends only its SHA-256 digest as the contract's `deviceHash`. This is a stable browser-install identifier, not a hardware fingerprint. The raw seed and digest are never rendered or logged.

## Face model files

Face descriptors are computed in the browser with `@vladmandic/face-api`; camera images are not sent or persisted by this frontend. To enable face enrolment, login verification and step-up:

1. Install dependencies with `npm install`.
2. Run `npm run models:copy`. This copies the browser runtime into `public/vendor/face-api.js` and the Tiny Face Detector, 68-point face landmark, and face recognition manifests/weights shipped with the installed `@vladmandic/face-api` package into `public/models/`.
3. The application loads those files locally on first use. Missing or inaccessible files are reported as a face-model error; no placeholder descriptors are sent.

Camera access requires HTTPS or localhost and explicit browser permission. Streams are stopped when the camera component unmounts. The app rejects zero/multiple faces and descriptors that are not exactly 128 finite floats.

## Project structure

- `src/api/` — typed envelope helpers, API client, refresh queue, friendly backend error map and DTO types.
- `src/auth/` — in-memory access-token context and browser device digest.
- `src/components/ui/` — reusable design-system components, including Radix Dialog.
- `src/components/` — application shell, camera and reusable face step-up dialog/hook.
- `src/face/` — model loading and descriptor extraction.
- `src/pages/` — authentication, workspace, audit and design-system routes.
- `src/styles/` — design tokens and responsive component styling.
- `design/` — original design-system reference screenshots.
- `public/models/` — add model manifests and shards locally; model binaries are not included.

## Screen-to-endpoint checklist

Paths below are relative to `/api/v1`. Authenticated requests use the in-memory access token.

| Screen / flow | Endpoints |
|---|---|
| Login | `POST /auth/login`; `POST /auth/login/face-verify` for risk challenges; `POST /auth/refresh` for session restoration/401 recovery; `POST /auth/logout`; browser `deviceHash` included on login. |
| Register | `POST /auth/register`; navigates to verification after accepted registration. |
| Email OTP verification | `POST /auth/verify-email`; `POST /auth/resend-verification` with a 60-second cooldown. |
| Forgot password | `POST /auth/forgot-password`; neutral success confirmation. |
| Reset password | `POST /auth/reset-password` using the URL `token`. |
| Accept invitation | `POST /organizations/invitations/accept` using the URL `token` and signed-in session. |
| Locked / rate-limited notices | Reached from `423` / `429` responses from authentication endpoints; no extra endpoint. |
| Forbidden / not found | Server `403` responses and unknown routes; no extra endpoint. |
| App shell | `GET /users/me`, `GET /organizations/current`; logout uses `POST /auth/logout`. |
| Home | `GET /users/me`, `GET /devices`; organization administrators additionally use `GET /security/dashboard-summary`. Last-login time is not exposed by the Module 1 API and is shown as unavailable. |
| Face security | `GET /face/status`, `POST /face/enroll` (exactly three browser-computed descriptors), `DELETE /face/enrollment`. |
| Profile and devices | `GET /users/me`, `PUT /users/me`, `POST /auth/change-password`, `GET /devices`, `POST /devices/{id}/trust`, `DELETE /devices/{id}`. |
| Organization settings and members | `GET /organizations/current`, `PUT /organizations/current`, `GET /organizations/members`, `POST /organizations/members`, `PUT /organizations/members/{id}/role`, `DELETE /organizations/members/{id}`, `PUT /organizations/bank-details`, `POST /organizations/transfer-admin`. |
| Sensitive action step-up | `POST /auth/step-up/initiate`, `POST /auth/step-up/verify`; resulting `X-StepUp-Token` is attached only to the corresponding protected bank-details or admin-transfer request. |
| Security audit | `GET /security/dashboard-summary`, `GET /security/risky-logins`, `GET /audit-logs` with API-supported date/user/action/result filters and pagination. |
| Design system showcase | `/design-system` (local presentation, no backend calls). |

The checked-in contract has no dedicated resend-invitation endpoint. Pending invitations expose the requested “Resend invite” control as unavailable (with an explanation) rather than inventing an endpoint or reusing the create-invite operation in a way that may create a duplicate.

## Backend/API verification status

The checked-in `invoice-security/API_CONTRACT.md` and its controllers/DTOs were reviewed while building this client. The live `http://localhost:8080/v3/api-docs` URL did not return within the request timeout in this environment, so live OpenAPI parity and authenticated end-to-end flows could not be verified here. See the completion report for the commands actually run.
