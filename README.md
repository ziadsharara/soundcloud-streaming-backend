# SoundStream backend

Spring Boot API and STOMP/WebSocket broker for synchronized SoundCloud listening rooms.

## Features

- Create, discover, and close listening rooms.
- Host-authorized play, pause, seek, and track-change broadcasts.
- Listener presence and room chat over WebSockets.
- Public SoundCloud song, playlist, and album URL playback through the official embedded player.
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
| `FRONTEND_ORIGINS` | Comma-separated exact frontend origins allowed by CORS |
| `PUBLIC_FRONTEND_ORIGIN` | Canonical invite-link origin, defaults to `https://soundstreaming.vercel.app` |

No SoundCloud API credentials or Artist Pro account are required. The backend relays room state only; audio plays directly from SoundCloud in each listener's browser. Rooms are currently in memory and are intentionally cleared on restart; use Redis or a database before scaling beyond one instance.

## Verify

```bash
./mvnw verify
docker build -t soundcloud-streaming-backend .
```
