package com.soundstream.room;

/**
 * What the host is playing. {@code positionMs} was the playhead at {@code serverTime};
 * listeners extrapolate the current position from that while {@code playing} is true.
 *
 * <p>{@code provider} tells listeners which player to open the link in, and {@code sync}
 * how closely they can expect to follow along.
 */
public record PlaybackState(
        String trackUrl,
        Provider provider,
        Provider.Sync sync,
        String title,
        String artist,
        String artworkUrl,
        boolean playing,
        long positionMs,
        long serverTime) {
}
