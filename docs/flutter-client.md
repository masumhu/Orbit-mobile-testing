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

For native Flutter sign-in, obtain the provider token with the provider's Flutter SDK and send it to Orbit. The backend validates it; never put an OAuth client secret in the app.

Google uses an ID token:

```http
POST /api/v1/auth/login/google
```

```json
{ "providerToken": "<google-id-token>" }
```

GitHub uses an access token with `read:user` and `user:email` scopes:

```http
POST /api/v1/auth/login/github
```

```json
{ "providerToken": "<github-access-token>" }
```

For registration instead of login, call `POST /api/v1/auth/registrations/google/request-otp` or `POST /api/v1/auth/registrations/github/request-otp`. Send the same organization fields used for email registration, replacing `username`, `email`, and `password` with `providerToken`. Verify the returned OTP before logging in.

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
| Auth (public) | `POST /api/v1/auth/registrations/email/request-otp`, `POST /api/v1/auth/registrations/{google\|github}/request-otp`, `POST /api/v1/auth/registrations/verify-otp`, `POST /api/v1/auth/login`, `POST /api/v1/auth/login/{google\|github}`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` |
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
