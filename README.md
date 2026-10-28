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

1. In Google Cloud Console, configure the OAuth consent screen and create Android and iOS OAuth clients for the Flutter app's package name/SHA-1 and bundle ID. Also create a **Web application** client to use as the Flutter SDK's server client ID.
2. Set `GOOGLE_OAUTH_CLIENT_ID` to the Web client ID. Set `GOOGLE_OAUTH_CLIENT_IDS` to a comma-separated allowlist of client IDs that may appear in Google ID tokens (usually the Web client ID; add Android/iOS IDs only if your Flutter setup actually returns tokens with those audiences). The verifier checks the Google signature, issuer, expiry, verified email, and audience using Google's rotating public keys.
3. With Flutter's `google_sign_in` plugin, initialize it with the Web ID as `serverClientId`, sign the user in, and send the returned **ID token** to `POST /api/v1/auth/login/google` as `{"providerToken":"<id-token>"}`. For a new account, send that token plus organization fields to `POST /api/v1/auth/registrations/google/request-otp`, verify the emailed code at `/api/v1/auth/registrations/verify-otp`, then call the Google login route to receive app tokens. Existing users sign in without an email OTP.
4. `GOOGLE_OAUTH_CLIENT_SECRET` and `GOOGLE_OAUTH_REDIRECT_URI` are only needed for the authorization-code callback routes. Keep the secret on the backend and out of Flutter; leave them blank when using the native ID-token flow.
5. For GitHub, create a GitHub OAuth App and configure its callback URL. Set `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET`, and `GITHUB_OAUTH_REDIRECT_URI` to the app's values; the redirect URI must match the Flutter OAuth redirect and GitHub callback URL. Keep the secret on the backend. Flutter should use authorization code + PKCE (`S256`) and request `read:user user:email`; send the returned code and original verifier to the GitHub callback endpoints described in `docs/flutter-client.md`.

Google Cloud's Android setup uses the app package name and SHA-1 signing certificate fingerprint; its iOS setup uses the app bundle ID. Register the correct debug and release signing certificates. For production deployments, configure Google client IDs and use HTTPS for all mobile-to-backend requests.

## Flutter integration

See [docs/flutter-client.md](docs/flutter-client.md) for the auth flows, request examples, token refresh behavior, API routes, and WebSocket details. Store refresh tokens with secure device storage; do not put secrets in the Flutter app.

## Deploy

The included `Dockerfile` builds and runs the API. Supply MongoDB and secret values as environment variables in the deployment platform. Keep `.env` local; it is ignored by Git.
