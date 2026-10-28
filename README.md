# Orbit Mobile Backend

An API-only version of the Orbit backend for Flutter clients. It keeps Orbit's organization, project, task, submission, ticket, chat, and notification features, with JSON REST endpoints and authenticated WebSocket chat. No website or Flutter UI is included.

## Stack

- Java 25 and Spring Boot 3.5
- MongoDB
- JWT access tokens and rotating refresh tokens
- Email OTP registration through Brevo
- Optional Google and GitHub sign-in
- OpenAPI/Swagger documentation

## Run locally

1. Install Java 25 and Maven, and start MongoDB.
2. Copy `.env.example` to `.env` and set `MONGODB_URI`, `BREVO_API_KEY`, `MAIL_FROM`, `OTP_HMAC_SECRET`, and `JWT_SECRET`. Use random secrets of at least 32 characters for `OTP_HMAC_SECRET` and `JWT_SECRET`.
3. Google and GitHub sign-in are optional. Leave their variables blank when using email/password and OTP. Configure provider client IDs and any callback secrets only if you enable social sign-in later.
4. Start the API:

   ```powershell
   mvn spring-boot:run
   ```

The API listens on port `8080` by default. Health is at `/actuator/health`; Swagger UI is at `/swagger-ui.html`.

For a local Flutter development build, use `http://10.0.2.2:8080` from the Android emulator, `http://127.0.0.1:8080` from the iOS simulator, or your computer's LAN address from a physical device. Android may require cleartext HTTP to be enabled for debug builds; use an HTTPS deployment URL in release builds.

## Configure email OTP and optional social login

### Email OTP through Brevo

1. Create a Brevo API key and configure a sender address (and authenticate its sending domain for production delivery).
2. Verify the sender address in Brevo. Set `BREVO_API_KEY` to the Brevo API key and `MAIL_FROM` to that exact verified sender email. `MAIL_FROM_NAME` controls the display name. This backend calls Brevo's transactional email API; SMTP credentials are not used.
3. Set `OTP_HMAC_SECRET` and `JWT_SECRET` to separate random secrets of at least 32 characters. In PowerShell, generate a value with:

   ```powershell
   $bytes = New-Object byte[] 32
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   [BitConverter]::ToString($bytes).Replace('-', '').ToLower()
   ```

   Run it twice and use one value for each secret. Keep both out of source control.

4. Set `MONGODB_URI`, copy `.env.example` to `.env`, and fill in the values above. Start MongoDB and then run `mvn spring-boot:run`.

Email/password registration uses `POST /api/v1/auth/registrations/email/request-otp`, followed by `POST /api/v1/auth/registrations/verify-otp`; successful registration then signs in at `POST /api/v1/auth/login`. OTPs are six digits, expire after 10 minutes, and allow up to five attempts. Email/password login does not send an OTP.

### Optional: Google or GitHub sign-in

1. In Google Cloud Console, configure the OAuth consent screen and create OAuth clients for the Android package name/SHA-1 and iOS bundle ID used by the Flutter app.
2. Create a **Web application** OAuth client in the same Google Cloud project for backend ID-token verification. Put that client ID in `GOOGLE_OAUTH_CLIENT_ID`, and configure the Flutter Google sign-in SDK to request an ID token for this same server client ID. The backend checks that the token's `aud` claim equals this value.
3. For the native Flutter flow, send the Google **ID token** to `POST /api/v1/auth/login/google` as `{"providerToken":"<id-token>"}`. For Google registration, use `POST /api/v1/auth/registrations/google/request-otp` with the same token plus the organization fields, then verify the emailed code. A normal login does not send an OTP; OTP is required when registering a new account.
4. `GOOGLE_OAUTH_CLIENT_SECRET` and `GOOGLE_OAUTH_REDIRECT_URI` are only needed if you choose the authorization-code callback routes. Keep the secret on the backend and out of Flutter. They can stay blank for the native ID-token route.

Google Cloud's Android setup uses the app package name and SHA-1 signing certificate fingerprint; its iOS setup uses the app bundle ID. Register the correct debug and release signing certificates. The backend currently checks Google ID tokens through Google's `tokeninfo` endpoint; Google documents that endpoint for debugging, not production validation. Replace this with local signature/JWK validation before using this verifier for production traffic.

## Flutter integration

See [docs/flutter-client.md](docs/flutter-client.md) for the auth flows, request examples, token refresh behavior, API routes, and WebSocket details. Store refresh tokens with secure device storage; do not put secrets in the Flutter app.

## Deploy

The included `Dockerfile` builds and runs the API. Supply MongoDB and secret values as environment variables in the deployment platform. Keep `.env` local; it is ignored by Git.
