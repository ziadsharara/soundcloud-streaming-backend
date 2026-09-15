# SoundStream backend

Spring Boot API and STOMP/WebSocket broker for synchronized SoundCloud listening rooms.

## Features

- Create, discover, and close listening rooms.
- Host-authorized play, pause, seek, and track-change broadcasts.
- Listener presence and room chat over WebSockets.
- SoundCloud OAuth 2.1 Authorization Code flow with PKCE.
- Server-side access/refresh token handling; the browser receives only an opaque app session.
- SoundCloud playlists, liked tracks, and liked playlists exposed to the connected host.
- Docker image with a non-root runtime user and health check.

## Run locally

Requires Java 21+.

```bash
cp .env.example .env
./mvnw spring-boot:run
```

Or with Docker:

```bash
docker compose up --build
```

## Configuration

| Variable | Purpose |
|---|---|
| `PORT` | HTTP port, defaults to `8080` |
| `FRONTEND_ORIGINS` | Comma-separated exact frontend origins allowed by CORS and OAuth return validation |
| `SOUNDCLOUD_CLIENT_ID` | Registered SoundCloud application ID |
| `SOUNDCLOUD_CLIENT_SECRET` | Registered SoundCloud application secret |
| `SOUNDCLOUD_REDIRECT_URI` | Exact registered callback, ending in `/api/auth/soundcloud/callback` |

SoundCloud requires OAuth 2.1 with PKCE. Access tokens expire and refresh tokens are single-use, so this service serializes refresh and replaces both tokens. Sessions and rooms are currently in memory and are intentionally cleared on restart; use Redis or a database before scaling beyond one instance.

## Verify

```bash
./mvnw verify
docker build -t soundcloud-streaming-backend .
```
