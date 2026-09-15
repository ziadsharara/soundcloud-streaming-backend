package com.soundstream.room;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class Room {

    private final String id;
    private final String name;
    private final String hostName;
    private final String hostToken;
    private final long createdAt;
    private volatile PlaybackState playback;

    public Room(String id, String name, String hostName, String hostToken, long createdAt) {
        this.id = id;
        this.name = name;
        this.hostName = hostName;
        this.hostToken = hostToken;
        this.createdAt = createdAt;
    }

    /** Constant-time comparison so the token can't be guessed by timing. */
    public boolean isHost(String token) {
        return token != null && MessageDigest.isEqual(
                hostToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostToken() {
        return hostToken;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public PlaybackState getPlayback() {
        return playback;
    }

    public void setPlayback(PlaybackState playback) {
        this.playback = playback;
    }
}
