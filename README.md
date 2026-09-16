# SoundStream backend

Spring Boot API and STOMP/WebSocket broker for synchronized listening rooms.

## Features

- Create, discover, and close listening rooms.
- Host-authorized play, pause, seek, and track-change broadcasts.
- Members with a name and a drawn avatar, tracked by STOMP session.
- A shared upcoming queue and room chat (text and stickers) over WebSockets.
- Recent chat history for people who join after the conversation starts.
- Link validation for SoundCloud, YouTube Music and YouTube.
- A scheduled sweep that closes rooms nobody is in.
- Docker image with a non-root runtime user and health check.

## Sources

The backend never touches audio; it relays room state and validates that a link points at a service
we can actually drive:

| Provider | Hosts |
|---|---|
| `SOUNDCLOUD` | soundcloud.com, on.soundcloud.com and friends |
| `YOUTUBE_MUSIC` | music.youtube.com, youtube.com, youtu.be |

Both expose real player APIs, so every room stays in true sync. Spotify (embeds only play a ~30s
preview) and Anghami (no player API) were removed rather than shipped as a worse experience.

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

No API credentials are required. Rooms are in memory and are
intentionally cleared on restart; use Redis or a database before scaling beyond one instance.

## Verify

```bash
./mvnw verify
docker build -t soundstream-backend .
```
