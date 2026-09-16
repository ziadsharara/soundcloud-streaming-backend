# SoundStream backend

Spring Boot API and STOMP/WebSocket broker for synchronized listening rooms.

## Features

- Create, discover, and close listening rooms.
- Host-authorized play, pause, seek, and track-change broadcasts.
- Members with a name and a drawn avatar, tracked by STOMP session.
- A shared upcoming queue and room chat (text and stickers) over WebSockets.
- Recent chat history for people who join after the conversation starts.
- Link validation for SoundCloud, Spotify and Anghami, each tagged with how far it can be synced.
- A scheduled sweep that closes rooms nobody is in.
- Docker image with a non-root runtime user and health check.

## Sources and sync

The backend never touches audio; it relays room state, and tells clients what to expect from each
service:

| Provider | `sync` | Meaning |
|---|---|---|
| `SOUNDCLOUD` | `FULL` | Full tracks, position-accurate |
| `SPOTIFY` | `PREVIEW` | Controllable, but the embed plays a ~30s preview |
| `ANGHAMI` | `NONE` | No player API; the link is shared, not synced |

Chat is attributed from the sender's STOMP session, never from the message body, so a message
cannot claim someone else's name or avatar.

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
| `ROOM_EMPTY_GRACE` | How long an empty room is kept, ISO-8601, defaults to `PT10M` |
| `ROOM_CLEANUP_INTERVAL` | How often the sweep runs, ISO-8601, defaults to `PT1M` |

The grace period exists so a host who reloads, loses Wi-Fi, or switches network comes back to the
same room and the same code.

No SoundCloud, Spotify or Anghami credentials are required. Rooms are in memory and are
intentionally cleared on restart; use Redis or a database before scaling beyond one instance.

## Verify

```bash
./mvnw verify
docker build -t soundstream-backend .
```
