# Flutter API integration

## Base URL and headers

Use the API base URL for the current environment, then append `/api/v1`. Send JSON requests with:

```http
Accept: application/json
Content-Type: application/json
```

For authenticated requests, add:

```http
Authorization: Bearer <accessToken>
```

All API responses are JSON except successful logout (`204 No Content`). Dates use ISO-8601; project and task deadlines use `YYYY-MM-DD`.

## Register with email

Request an OTP:

```http
POST /api/v1/auth/registrations/email/request-otp
```

Create an organization:

```json
{
  "organizationMode": "CREATE",
  "organizationName": "Orbit Demo",
  "username": "alex",
  "email": "alex@example.com",
  "password": "use-a-long-password"
}
```

To join an existing organization, use `organizationMode: "JOIN"` and provide `organizationId`, `role` (`PRODUCT_MANAGER`, `SENIOR_MANAGER`, or `DEVELOPER`), and `companyAccessCode`. The owner is assigned automatically when an organization is created.

The response contains `challengeId` and `expiresAt`. OTPs expire after 10 minutes and allow up to five attempts. Verify the email code:

```http
POST /api/v1/auth/registrations/verify-otp
```

```json
{
  "challengeId": "<challenge-id>",
  "otp": "123456"
}
```

Verification creates the account. Then call the password login endpoint to receive app tokens.

## Register or sign in with a provider

For native Flutter sign-in, obtain a Google ID token with the `google_sign_in` plugin and send it to Orbit over HTTPS. The backend verifies the token signature and its issuer, expiry, audience, and verified-email claims. Never send a Google profile/user ID in place of the ID token, and never put an OAuth client secret in the app.

Configure Android and iOS OAuth clients in Google Cloud Console, then create a Web application client ID for the backend audience. Set `GOOGLE_OAUTH_CLIENT_ID` to that Web client ID and set `GOOGLE_OAUTH_CLIENT_IDS` to the comma-separated ID-token audience allowlist. Usually the allowlist only needs the Web client ID when Flutter is initialized with it as `serverClientId`.

Google uses an ID token:

```http
POST /api/v1/auth/login/google
```

```json
{ "providerToken": "<google-id-token>" }
```

For example, with `google_sign_in` 7.x, initialize `GoogleSignIn.instance` with the Web client ID as `serverClientId`, call `authenticate()`, then read `account.authentication.idToken`. Send that token as `providerToken`; reject/cancel the client flow if the SDK returns no ID token.

For GitHub on Flutter, use an OAuth App authorization-code flow with PKCE. Create a cryptographically random `codeVerifier` (43 to 128 URL-safe characters), compute `codeChallenge = BASE64URL_NO_PADDING(SHA256(codeVerifier))`, and generate a random `state`. Open this authorization URL in the system browser, URL-encoding the values:

```text
https://github.com/login/oauth/authorize?client_id=<github-client-id>&redirect_uri=<configured-redirect-uri>&scope=read%3Auser%20user%3Aemail&state=<random-state>&code_challenge=<code-challenge>&code_challenge_method=S256
```

Handle the redirect in Flutter, verify the returned `state` matches the value created for this sign-in, then send the returned `code` and original `codeVerifier` to the backend. Configure `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET`, and `GITHUB_OAUTH_REDIRECT_URI` on the backend; the redirect URI must match the OAuth App callback URL and the URI used in the authorize request. The backend exchanges the code with GitHub, reads the user's GitHub identity and verified primary email, then returns Orbit tokens for login. Keep the client secret on the backend.

Login:

```http
POST /api/v1/auth/login/github/callback
```

```json
{
  "code": "<github-authorization-code>",
  "codeVerifier": "<original-pkce-code-verifier>"
}
```

For Google registration, call `POST /api/v1/auth/registrations/google/request-otp` with the ID token and organization fields. Example for creating an organization:

```json
{
  "organizationMode": "CREATE",
  "organizationName": "Orbit Demo",
  "providerToken": "<google-id-token>"
}
```

To join an organization, send `organizationMode: "JOIN"`, its `organizationId`, a non-owner `role`, and `companyAccessCode` instead. The backend emails an OTP to the verified Google account address; verify it at `POST /api/v1/auth/registrations/verify-otp`. Registration creates the account, then call `POST /api/v1/auth/login/google` again with a fresh ID token to receive Orbit access and refresh tokens. Existing Google accounts use login directly and do not need an email OTP.

For GitHub registration, send the organization fields together with `code` and `codeVerifier` to `POST /api/v1/auth/registrations/github/callback`. Example for creating an organization:

```json
{
  "organizationMode": "CREATE",
  "organizationName": "Orbit Demo",
  "code": "<github-authorization-code>",
  "codeVerifier": "<original-pkce-code-verifier>"
}
```

The backend emails an OTP to the verified primary GitHub email. Verify it at `POST /api/v1/auth/registrations/verify-otp`, then start a new GitHub authorization and call the login callback to receive Orbit tokens. To join an organization, send `organizationMode: "JOIN"`, `organizationId`, a non-owner `role`, and `companyAccessCode` instead.

The `/callback` endpoints are available for clients using provider authorization codes. They require the matching backend redirect URI configuration and should only be used with the client-secret exchange kept on the server.

## Password login and token lifecycle

```http
POST /api/v1/auth/login
```

```json
{
  "identifier": "alex@example.com",
  "password": "use-a-long-password"
}
```

Successful login returns:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "<opaque-refresh-token>",
  "refreshTokenExpiresAt": "<timestamp>",
  "user": {
    "id": "<user-id>",
    "username": "alex",
    "email": "alex@example.com",
    "role": "DEVELOPER",
    "organizationId": "<organization-id>"
  }
}
```

Access tokens last 15 minutes. Refresh tokens last 30 days and rotate on refresh:

```http
POST /api/v1/auth/refresh
```

```json
{ "refreshToken": "<current-refresh-token>" }
```

Replace both stored tokens with the response values. To sign out, call `POST /api/v1/auth/logout` with the refresh token; it returns `204` and revokes that session. Use `GET /api/v1/auth/me` to load the current user.

Store refresh tokens in secure device storage such as `flutter_secure_storage`. Keep access tokens in memory where practical, never log either token, and retry a failed request only after a successful refresh.

## Main API routes

All routes below require a bearer access token unless marked public.

| Area | Routes |
| --- | --- |
| Auth (public) | `POST /api/v1/auth/registrations/email/request-otp`, `POST /api/v1/auth/registrations/{google\|github}/request-otp`, `POST /api/v1/auth/registrations/{google\|github}/callback`, `POST /api/v1/auth/registrations/verify-otp`, `POST /api/v1/auth/login`, `POST /api/v1/auth/login/{google\|github}`, `POST /api/v1/auth/login/github/callback`, `POST /api/v1/auth/login/google/callback`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
| Current user | `GET /api/v1/auth/me` |
| Organizations | `GET /api/v1/organizations/{organizationId}`, `GET /api/v1/organizations/{organizationId}/members`, `GET /api/v1/organizations/{organizationId}/developers`, `GET /api/v1/organizations/{organizationId}/senior-managers`, `PUT /api/v1/organizations/{organizationId}/access-codes`, `POST /api/v1/organizations/{organizationId}/leave` |
| Projects | `GET /api/v1/projects`, `POST /api/v1/projects`, `GET /api/v1/projects/{projectId}`, `PATCH /api/v1/projects/{projectId}`, `POST /api/v1/projects/{projectId}/leave` |
| Tasks | `GET|POST /api/v1/projects/{projectId}/tasks`, `GET /api/v1/tasks/{taskId}`, `PUT /api/v1/tasks/{taskId}/assignee`, `PATCH /api/v1/tasks/{taskId}/status` |
| Submissions | `POST /api/v1/tasks/{taskId}/submissions`, `GET /api/v1/projects/{projectId}/submissions`, `POST /api/v1/submissions/{submissionId}/forward`, `POST /api/v1/submissions/{submissionId}/request-changes`, `POST /api/v1/submissions/{submissionId}/review` |
| Tickets | `POST /api/v1/projects/{projectId}/tickets`, `GET /api/v1/organizations/tickets`, `GET /api/v1/projects/{projectId}/tickets`, `PATCH /api/v1/tickets/{ticketId}/priority`, `PATCH /api/v1/tickets/{ticketId}/status`, `PUT /api/v1/tickets/{ticketId}/assignee` |
| Notifications | `GET /api/v1/notifications`, `GET /api/v1/notifications/unread-count`, `PATCH /api/v1/notifications/{notificationId}/read`, `PATCH /api/v1/notifications/read-all` |
| Chat | `GET|POST /api/v1/channels/{channelId}/messages`, `PATCH /api/v1/messages/{messageId}`, `DELETE /api/v1/messages/{messageId}` |

Swagger UI at `/swagger-ui.html` lists request schemas and the full route definitions.

## Chat over WebSocket

Connect a STOMP client to `/ws` with `Authorization: Bearer <accessToken>`. Subscribe to `/topic/channels/{channelId}` and publish chat messages to `/app/channels/{channelId}/messages`. The user must already have access to the channel. REST endpoints are also available for message history and sending.

## Errors

Errors use the API's JSON error shape:

```json
{
  "timestamp": "<timestamp>",
  "status": 400,
  "error": "Bad Request",
  "message": "<explanation>",
  "path": "/api/v1/..."
}
```

Use the HTTP status as the primary signal. A `401` means the access token is missing, expired, or invalid; refresh once, then require sign-in if refresh fails.
