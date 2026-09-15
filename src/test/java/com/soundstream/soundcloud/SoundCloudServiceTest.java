package com.soundstream.soundcloud;

import com.soundstream.soundcloud.SoundCloudDtos.Library;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SoundCloudServiceTest {

    @Test
    void loadsEverySoundCloudLibraryPage() throws Exception {
        HttpClient http = mock(HttpClient.class);
        AtomicBoolean signedOut = new AtomicBoolean();
        when(http.send(any(HttpRequest.class), anyStringBodyHandler())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            String url = request.uri().toString();
            if (url.equals("https://secure.soundcloud.com/oauth/token")) {
                return response(200, """
                        {"access_token":"access-token","refresh_token":"refresh-token","expires_in":3600}
                        """);
            }
            if (url.equals("https://secure.soundcloud.com/sign-out")) {
                assertEquals("POST", request.method());
                assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
                signedOut.set(true);
                return response(204, "");
            }
            assertEquals("OAuth access-token", request.headers().firstValue("Authorization").orElse(""));
            if (url.equals("https://api.soundcloud.com/me")) {
                return response(200, """
                        {"urn":"soundcloud:users:1","username":"DJ Test","permalink_url":"https://soundcloud.com/dj-test"}
                        """);
            }
            if (url.equals("https://api.soundcloud.com/me/recently-played/tracks?access=playable")) {
                return response(200, """
                        [{"urn":"soundcloud:tracks:4","title":"Just played","permalink_url":"https://soundcloud.com/artist/just-played"}]
                        """);
            }
            if (url.equals("https://api.soundcloud.com/me/playlists?cursor=next-page")) {
                return response(200, """
                        {"collection":[{"urn":"soundcloud:playlists:2","title":"Second set","permalink_url":"https://soundcloud.com/dj-test/sets/second"}]}
                        """);
            }
            if (url.startsWith("https://api.soundcloud.com/me/playlists?")) {
                return response(200, """
                        {
                          "collection":[{"urn":"soundcloud:playlists:1","title":"First set","permalink_url":"https://soundcloud.com/dj-test/sets/first"}],
                          "next_href":"https://api.soundcloud.com/me/playlists?cursor=next-page"
                        }
                        """);
            }
            if (url.startsWith("https://api.soundcloud.com/me/likes/tracks?")) {
                return response(200, """
                        {"collection":[{"track":{"urn":"soundcloud:tracks:3","title":"Liked track","permalink_url":"https://soundcloud.com/artist/liked-track"}}]}
                        """);
            }
            if (url.startsWith("https://api.soundcloud.com/me/likes/playlists?")) {
                return response(200, "{\"collection\":[]}");
            }
            throw new AssertionError("Unexpected SoundCloud request: " + url);
        });

        SoundCloudService service = new SoundCloudService(
                "client-id",
                "client-secret",
                "https://api.example.com/api/auth/soundcloud/callback",
                List.of("https://app.example.com"),
                new ObjectMapper(),
                http);

        URI authorization = service.beginAuthorization("https://app.example.com");
        String state = authorization.getRawQuery().lines()
                .flatMap(query -> List.of(query.split("&")).stream())
                .filter(parameter -> parameter.startsWith("state="))
                .map(parameter -> parameter.substring("state=".length()))
                .findFirst()
                .orElseThrow();
        SoundCloudService.LoginResult login = service.completeAuthorization(state, "authorization-code");

        Library library = service.library(login.sessionId());

        assertEquals("DJ Test", library.profile().username());
        assertEquals(List.of("Just played"),
                library.recentlyPlayed().stream().map(item -> item.title()).toList());
        assertEquals(List.of("First set", "Second set"),
                library.playlists().stream().map(item -> item.title()).toList());
        assertEquals(List.of("Liked track"),
                library.likedTracks().stream().map(item -> item.title()).toList());
        assertTrue(library.likedPlaylists().isEmpty());

        service.signOut(login.sessionId());

        assertTrue(signedOut.get());
        assertThrows(ResponseStatusException.class, () -> service.sessionProfile(login.sessionId()));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return any(HttpResponse.BodyHandler.class);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
