package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.List;

public class Room {

    private static final int MAX_CHAT_HISTORY = 50;

    private final String id;
    private final String name;
    private final String hostName;
    private final String hostToken;
    private final long createdAt;
    private final ArrayDeque<ChatMessage> chatHistory = new ArrayDeque<>();
    private volatile PlaybackState playback;
    private volatile QueueState queue;

    public Room(String id, String name, String hostName, String hostToken, long createdAt) {
        this.id = id;
        this.name = name;
        this.hostName = hostName;
        this.hostToken = hostToken;
        this.createdAt = createdAt;
        this.queue = new QueueState(List.of(), -1, createdAt);
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

    public QueueState getQueue() {
        return queue;
    }

    public void setQueue(QueueState queue) {
        this.queue = queue;
    }

    public synchronized void addChatMessage(ChatMessage message) {
        while (chatHistory.size() >= MAX_CHAT_HISTORY) {
            chatHistory.removeFirst();
        }
        chatHistory.addLast(message);
    }

    public synchronized List<ChatMessage> getChatHistory() {
        return List.copyOf(chatHistory);
    }
}
