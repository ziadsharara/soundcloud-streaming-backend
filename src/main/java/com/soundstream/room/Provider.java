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
 * <p>How closely each one can be synced is decided by the service, not by us:
 * SoundCloud and YouTube expose real player APIs, Spotify's embed answers commands but plays a
 * preview, and Anghami publishes no player API at all.
 *
 * <p>YOUTUBE_MUSIC covers music.youtube.com and ordinary youtube.com links alike: plenty of songs
 * live on YouTube proper, and both share the same video ids and the same player API.
 */
public enum Provider {

    SOUNDCLOUD(
            List.of("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com"),
            null,
            Sync.FULL),

    YOUTUBE_MUSIC(
            List.of("music.youtube.com", "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be"),
            Pattern.compile("^/(watch|playlist)/?$|^/[A-Za-z0-9_-]{5,}/?$"),
            Sync.FULL),

    SPOTIFY(
            List.of("open.spotify.com", "play.spotify.com", "spotify.link"),
            Pattern.compile("^/(intl-[a-z-]+/)?(track|album|playlist|episode)/[A-Za-z0-9]+/?$|^/[A-Za-z0-9]+/?$"),
            Sync.PREVIEW),

    ANGHAMI(
            List.of("anghami.com", "www.anghami.com", "play.anghami.com", "open.anghami.com"),
            null,
            Sync.NONE);

    /** How closely listeners can be held to the host on this provider. */
    public enum Sync {
        /** Full tracks, position-accurate. */
        FULL,
        /** Controllable, but the embed plays a short preview for most listeners. */
        PREVIEW,
        /** No player API: everyone plays it themselves. */
        NONE
    }

    private final List<String> hosts;
    private final Pattern path;
    private final Sync sync;

    Provider(List<String> hosts, Pattern path, Sync sync) {
        this.hosts = hosts;
        this.path = path;
        this.sync = sync;
    }

    public Sync sync() {
        return sync;
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
