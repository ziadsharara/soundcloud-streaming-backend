package com.soundstream.room;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A music source SoundStream accepts links from.
 *
 * <p>Both publish real player APIs, so every room stays in true sync. Services whose embeds cannot
 * be driven — Spotify, which only ever plays a preview, and Anghami, which exposes no player at
 * all — were removed rather than shipped as a worse experience wearing the same badge.
 *
 * <p>YOUTUBE_MUSIC covers music.youtube.com and ordinary youtube.com links alike: plenty of songs
 * live on YouTube proper, and both share the same video ids and the same player API.
 */
public enum Provider {

    SOUNDCLOUD(
            List.of("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com"),
            null),

    YOUTUBE_MUSIC(
            List.of("music.youtube.com", "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"),
            Pattern.compile("^/(watch|playlist)/?$|^/[A-Za-z0-9_-]{5,}/?$"));

    private final List<String> hosts;
    private final Pattern path;

    Provider(List<String> hosts, Pattern path) {
        this.hosts = hosts;
        this.path = path;
    }

    /** Identifies the provider of a share URL, or empty if it is not a link we accept. */
    public static Optional<Provider> detect(String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.strip());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        String host = uri.getHost();
        String path = uri.getRawPath();
        boolean safe = "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && uri.getRawUserInfo() == null
                && uri.getPort() == -1
                && path != null
                && !path.isBlank()
                && !"/".equals(path);
        if (!safe) {
            return Optional.empty();
        }
        String hostname = host.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(provider -> provider.hosts.contains(hostname))
                .filter(provider -> provider.path == null || provider.path.matcher(path).matches())
                .findFirst();
    }

    public static boolean isSupported(String value) {
        return detect(value).isPresent();
    }
}
