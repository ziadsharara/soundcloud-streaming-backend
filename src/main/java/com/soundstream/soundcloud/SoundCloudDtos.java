package com.soundstream.soundcloud;

import java.util.List;

public final class SoundCloudDtos {

    private SoundCloudDtos() {
    }

    public record Configuration(boolean configured) {
    }

    public record Profile(
            String id,
            String username,
            String avatarUrl,
            String permalinkUrl) {
    }

    public record LibraryItem(
            String urn,
            String kind,
            String title,
            String artist,
            String artworkUrl,
            String permalinkUrl,
            int trackCount,
            long durationMs,
            boolean streamable) {
    }

    public record Library(
            Profile profile,
            List<LibraryItem> playlists,
            List<LibraryItem> likedTracks,
            List<LibraryItem> likedPlaylists) {
    }
}
