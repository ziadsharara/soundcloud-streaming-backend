package com.soundstream.soundcloud;

import com.soundstream.soundcloud.SoundCloudDtos.Configuration;
import com.soundstream.soundcloud.SoundCloudDtos.Library;
import com.soundstream.soundcloud.SoundCloudDtos.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class SoundCloudController {

    private final SoundCloudService soundCloud;

    public SoundCloudController(SoundCloudService soundCloud) {
        this.soundCloud = soundCloud;
    }

    @GetMapping("/auth/soundcloud/config")
    public Configuration configuration() {
        return new Configuration(soundCloud.configured());
    }

    @GetMapping("/auth/soundcloud/start")
    public ResponseEntity<Void> start(@RequestParam String returnTo) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(soundCloud.beginAuthorization(returnTo))
                .build();
    }

    @GetMapping("/auth/soundcloud/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error) {
        if (error != null) {
            String origin = soundCloud.cancelAuthorization(state);
            return redirect(origin + "/#soundcloud_error=" + encode(error));
        }
        SoundCloudService.LoginResult result = soundCloud.completeAuthorization(state, code);
        return redirect(result.returnOrigin() + "/#soundcloud_session=" + encode(result.sessionId()));
    }

    @GetMapping("/auth/soundcloud/session")
    public Profile session(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return soundCloud.sessionProfile(bearer(authorization));
    }

    @DeleteMapping("/auth/soundcloud/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void signOut(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        soundCloud.signOut(bearer(authorization));
    }

    @GetMapping("/soundcloud/library")
    public Library library(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return soundCloud.library(bearer(authorization));
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    private static String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            return null;
        }
        return authorization.substring(7);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
