package com.soundstream.soundcloud;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import com.soundstream.soundcloud.SoundCloudDtos.Library;
import com.soundstream.soundcloud.SoundCloudDtos.LibraryItem;
import com.soundstream.soundcloud.SoundCloudDtos.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SoundCloudService {

    private static final URI AUTHORIZE_URI = URI.create("https://secure.soundcloud.com/authorize");
    private static final URI TOKEN_URI = URI.create("https://secure.soundcloud.com/oauth/token");
    private static final URI SIGN_OUT_URI = URI.create("https://secure.soundcloud.com/sign-out");
    private static final String API_ROOT = "https://api.soundcloud.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final long STATE_TTL_MS = Duration.ofMinutes(10).toMillis();
    private static final long SESSION_TTL_MS = Duration.ofDays(30).toMillis();
    private static final int MAX_LIBRARY_PAGES = 1_000;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final List<String> allowedReturnOrigins;
    private final HttpClient http;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    public SoundCloudService(
            @Value("${app.soundcloud.client-id:}") String clientId,
            @Value("${app.soundcloud.client-secret:}") String clientSecret,
            @Value("${app.soundcloud.redirect-uri}") String redirectUri,
            @Value("${app.soundcloud.allowed-return-origins}") String[] allowedReturnOrigins,
            ObjectMapper json) {
        this(clientId, clientSecret, redirectUri, List.of(allowedReturnOrigins), json,
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
    }

    SoundCloudService(String clientId, String clientSecret, String redirectUri, List<String> allowedReturnOrigins,
                      ObjectMapper json, HttpClient http) {
        this.clientId = clientId.strip();
        this.clientSecret = clientSecret.strip();
        this.redirectUri = redirectUri.strip();
        this.allowedReturnOrigins = allowedReturnOrigins.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        this.json = json;
        this.http = http;
    }

    public boolean configured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && !redirectUri.isEmpty();
    }

    public URI beginAuthorization(String requestedReturnOrigin) {
        requireConfigured();
        pruneExpired();
        String returnOrigin = validateReturnOrigin(requestedReturnOrigin);
        String state = randomToken(32);
        String verifier = randomToken(64);
        String challenge = sha256Base64Url(verifier);
        pending.put(state, new PendingAuthorization(verifier, returnOrigin, System.currentTimeMillis() + STATE_TTL_MS));

        return URI.create(AUTHORIZE_URI + "?" + form(Map.of(
                "client_id", clientId,
                "redirect_uri", redirectUri,
                "response_type", "code",
                "code_challenge", challenge,
                "code_challenge_method", "S256",
                "state", state)));
    }

    public LoginResult completeAuthorization(String state, String code) {
        requireConfigured();
        PendingAuthorization authorization = state == null ? null : pending.remove(state);
        if (authorization == null || authorization.expiresAt() < System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This SoundCloud sign-in link has expired");
        }
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SoundCloud did not return an authorization code");
        }

        JsonNode tokens = tokenRequest(Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", redirectUri,
                "code_verifier", authorization.verifier(),
                "code", code));
        UserSession session = newSession(tokens);
        session.profile = profile(apiGet("/me", session, false));
        String sessionId = randomToken(32);
        sessions.put(sessionId, session);
        return new LoginResult(sessionId, authorization.returnOrigin());
    }

    public Profile sessionProfile(String sessionId) {
        return requireSession(sessionId).profile;
    }

    public Library library(String sessionId) {
        UserSession session = requireSession(sessionId);
        return new Library(
                session.profile,
                items(apiGet("/me/recently-played/tracks?access=playable", session, true), "track"),
                allItems("/me/playlists?show_tracks=false&linked_partitioning=true&limit=50", "playlist", session),
                allItems("/me/likes/tracks?linked_partitioning=true&limit=50", "track", session),
                allItems("/me/likes/playlists?show_tracks=false&linked_partitioning=true&limit=50", "playlist", session));
    }

    public void signOut(String sessionId) {
        if (sessionId == null) {
            return;
        }
        UserSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(SIGN_OUT_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json; charset=utf-8")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json(Map.of("access_token", session.accessToken))))
                .build();
        HttpResponse<String> response = send(request);
        if ((response.statusCode() < 200 || response.statusCode() >= 300) && response.statusCode() != 401) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "SoundCloud sign-out could not be completed");
        }
    }

    public String cancelAuthorization(String state) {
        PendingAuthorization authorization = state == null ? null : pending.remove(state);
        return authorization == null ? safeReturnOrigin(null) : authorization.returnOrigin();
    }

    public String safeReturnOrigin(String requested) {
        try {
            return validateReturnOrigin(requested);
        } catch (ResponseStatusException ignored) {
            return allowedReturnOrigins.isEmpty() ? "http://localhost:5173" : allowedReturnOrigins.getFirst();
        }
    }

    private UserSession newSession(JsonNode tokenResponse) {
        String accessToken = requiredText(tokenResponse, "access_token");
        String refreshToken = requiredText(tokenResponse, "refresh_token");
        long expiresIn = Math.max(60, tokenResponse.path("expires_in").asLong(3600));
        return new UserSession(accessToken, refreshToken,
                System.currentTimeMillis() + Duration.ofSeconds(expiresIn).toMillis(),
                System.currentTimeMillis() + SESSION_TTL_MS);
    }

    private UserSession requireSession(String sessionId) {
        pruneExpired();
        UserSession session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || session.sessionExpiresAt < System.currentTimeMillis()) {
            if (sessionId != null) {
                sessions.remove(sessionId);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connect your SoundCloud account again");
        }
        return session;
    }

    private JsonNode apiGet(String path, UserSession session, boolean allowRefresh) {
        return apiGet(URI.create(API_ROOT + path), session, allowRefresh);
    }

    private JsonNode apiGet(URI uri, UserSession session, boolean allowRefresh) {
        String accessToken = validAccessToken(session);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json; charset=utf-8")
                .header("Authorization", "OAuth " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 401 && allowRefresh) {
            refreshAfterUnauthorized(session, accessToken);
            return apiGet(uri, session, false);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    response.statusCode() == 429 ? "SoundCloud rate limit reached; try again shortly"
                            : "SoundCloud could not load your library");
        }
        return parse(response.body());
    }

    private List<LibraryItem> allItems(String initialPath, String expectedKind, UserSession session) {
        List<LibraryItem> result = new ArrayList<>();
        Set<URI> visited = new HashSet<>();
        URI pageUri = URI.create(API_ROOT + initialPath);

        while (pageUri != null) {
            if (!visited.add(pageUri) || visited.size() > MAX_LIBRARY_PAGES) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "SoundCloud returned invalid library pagination");
            }
            JsonNode page = apiGet(pageUri, session, true);
            result.addAll(items(page, expectedKind));
            String nextHref = page.path("next_href").asText("");
            pageUri = nextHref.isBlank() ? null : trustedApiUri(nextHref);
        }
        return List.copyOf(result);
    }

    private static URI trustedApiUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"api.soundcloud.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "SoundCloud returned an invalid library page URL");
        }
    }

    private String validAccessToken(UserSession session) {
        synchronized (session) {
            if (session.accessExpiresAt <= System.currentTimeMillis() + Duration.ofSeconds(30).toMillis()) {
                refreshLocked(session);
            }
            return session.accessToken;
        }
    }

    private void refreshAfterUnauthorized(UserSession session, String rejectedAccessToken) {
        synchronized (session) {
            if (rejectedAccessToken.equals(session.accessToken)) {
                refreshLocked(session);
            }
        }
    }

    private void refreshLocked(UserSession session) {
        JsonNode tokens = tokenRequest(Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", session.refreshToken));
        session.accessToken = requiredText(tokens, "access_token");
        session.refreshToken = requiredText(tokens, "refresh_token");
        long expiresIn = Math.max(60, tokens.path("expires_in").asLong(3600));
        session.accessExpiresAt = System.currentTimeMillis() + Duration.ofSeconds(expiresIn).toMillis();
    }

    private JsonNode tokenRequest(Map<String, String> fields) {
        HttpRequest request = HttpRequest.newBuilder(TOKEN_URI)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json; charset=utf-8")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SoundCloud sign-in could not be completed");
        }
        return parse(response.body());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SoundCloud request was interrupted", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SoundCloud is temporarily unavailable", e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SoundCloud returned an invalid response", e);
        }
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not encode SoundCloud request", e);
        }
    }

    private Profile profile(JsonNode node) {
        return new Profile(
                node.path("urn").asText(node.path("id").asText()),
                node.path("username").asText("SoundCloud listener"),
                node.path("avatar_url").asText(""),
                node.path("permalink_url").asText(""));
    }

    private List<LibraryItem> items(JsonNode response, String expectedKind) {
        JsonNode collection = response.isArray() ? response : response.path("collection");
        List<LibraryItem> items = new ArrayList<>();
        if (!collection.isArray()) {
            return items;
        }
        for (JsonNode wrapper : collection) {
            JsonNode item = unwrap(wrapper, expectedKind);
            String permalink = item.path("permalink_url").asText("");
            if (permalink.isBlank()) {
                continue;
            }
            JsonNode user = item.path("user");
            String artist = item.path("metadata_artist").asText("");
            if (artist.isBlank()) {
                artist = user.path("username").asText("");
            }
            String artwork = item.path("artwork_url").asText("");
            if (artwork.isBlank()) {
                artwork = user.path("avatar_url").asText("");
            }
            String access = item.path("access").asText("playable");
            items.add(new LibraryItem(
                    item.path("urn").asText(item.path("id").asText()),
                    expectedKind,
                    item.path("title").asText("Untitled"),
                    artist,
                    artwork,
                    permalink,
                    item.path("track_count").asInt(0),
                    item.path("duration").asLong(0),
                    !"blocked".equalsIgnoreCase(access) && item.path("streamable").asBoolean(true)));
        }
        return items;
    }

    private static JsonNode unwrap(JsonNode node, String kind) {
        JsonNode wrapped = node.path(kind);
        return wrapped.isObject() ? wrapped : node;
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "SoundCloud login is not configured yet");
        }
    }

    private String validateReturnOrigin(String value) {
        String candidate;
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            if (uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            candidate = uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid return URL");
        }
        if (!allowedReturnOrigins.contains(candidate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return URL is not allowed");
        }
        return candidate;
    }

    private void pruneExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
        sessions.entrySet().removeIf(entry -> entry.getValue().sessionExpiresAt < now);
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "SoundCloud did not return a required token");
        }
        return value;
    }

    public record LoginResult(String sessionId, String returnOrigin) {
    }

    private record PendingAuthorization(String verifier, String returnOrigin, long expiresAt) {
    }

    private static final class UserSession {
        private String accessToken;
        private String refreshToken;
        private long accessExpiresAt;
        private final long sessionExpiresAt;
        private Profile profile;

        private UserSession(String accessToken, String refreshToken, long accessExpiresAt, long sessionExpiresAt) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.accessExpiresAt = accessExpiresAt;
            this.sessionExpiresAt = sessionExpiresAt;
        }
    }
}
