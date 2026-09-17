package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;
import com.soundstream.room.RoomDtos.MemberReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class Room {

    private static final int MAX_CHAT_HISTORY = 50;
    /** Defensive bound: a long-lived room that many people pass through should not grow forever. */
    private static final int MAX_TRACKED_RECEIPTS = 500;
    private static final int MAX_QUEUE_SIZE = 100;
    /** How many different emoji one message can collect, so a single message can't grow forever. */
    private static final int MAX_EMOJI_PER_MESSAGE = 12;

    private final String id;
    private final String name;
    private final RoomKind kind;
    private final String hostName;
    private final String hostAvatarId;
    private final String hostToken;
    /** Empty for a public room; a PBKDF2 hash when the host set a password. */
    private final String passwordHash;
    /** Handed out once someone proves they know the password, and required to join or subscribe. */
    private final String accessKey;
    private final long createdAt;
    private final ArrayDeque<ChatMessage> chatHistory = new ArrayDeque<>();
    /**
     * How far each member has got through the chat, as timestamps rather than per-message sets:
     * reading a message implies reading everything before it, so two numbers per member is enough
     * to render ticks for the whole history.
     */
    private final Map<String, MemberReceipt> receipts = new ConcurrentHashMap<>();
    /** message id -> emoji -> the members who put it there. Guarded by the chat lock. */
    private final Map<String, Map<String, LinkedHashSet<String>>> reactions = new LinkedHashMap<>();
    private volatile PlaybackState playback;
    private volatile QueueState queue;
    /** When the room was last used; only a room nobody has touched in a long while is swept. */
    private volatile long lastOccupiedAt;

    public Room(String id, String name, RoomKind kind, String hostName, String hostAvatarId, String hostToken,
                String passwordHash, String accessKey, long createdAt) {
        this.id = id;
        this.name = name;
        this.kind = kind == null ? RoomKind.MUSIC : kind;
        this.hostName = hostName;
        this.hostAvatarId = hostAvatarId;
        this.hostToken = hostToken;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.accessKey = accessKey;
        this.createdAt = createdAt;
        this.lastOccupiedAt = createdAt;
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

    public RoomKind getKind() {
        return kind;
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostAvatarId() {
        return hostAvatarId;
    }

    public String getHostToken() {
        return hostToken;
    }

    public boolean isPrivate() {
        return !passwordHash.isEmpty();
    }

    public boolean matchesPassword(String password) {
        return isPrivate() && RoomPassword.matches(password, passwordHash);
    }

    public String getAccessKey() {
        return accessKey;
    }

    /** A public room is open to anyone with the code; a private one needs the key. */
    public boolean allows(String key) {
        return !isPrivate() || (key != null && MessageDigest.isEqual(
                accessKey.getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8)));
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastOccupiedAt() {
        return lastOccupiedAt;
    }

    public void markOccupied() {
        this.lastOccupiedAt = System.currentTimeMillis();
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

    /**
     * Adds one track to the end of the queue. Anyone in the room may do this — the queue is a
     * shared wishlist — which is why it is the server that appends rather than the host's browser
     * pushing a whole list and overwriting what a guest just added.
     *
     * Returns empty when the track is already queued or the queue is full, so nothing is broadcast.
     */
    public synchronized Optional<QueueState> addTrack(String trackUrl) {
        QueueState current = queue;
        if (current.trackUrls().size() >= MAX_QUEUE_SIZE || current.trackUrls().contains(trackUrl)) {
            return Optional.empty();
        }
        List<String> next = new ArrayList<>(current.trackUrls());
        next.add(trackUrl);
        QueueState state = new QueueState(List.copyOf(next), current.activeIndex(), System.currentTimeMillis());
        queue = state;
        return Optional.of(state);
    }

    public synchronized void addChatMessage(ChatMessage message) {
        while (chatHistory.size() >= MAX_CHAT_HISTORY) {
            ChatMessage dropped = chatHistory.removeFirst();
            // A message that has scrolled out of history takes its reactions with it.
            reactions.remove(dropped.id());
        }
        chatHistory.addLast(message);
    }

    public synchronized List<ChatMessage> getChatHistory() {
        return List.copyOf(chatHistory);
    }

    /**
     * Adds or removes one member's reaction to one message, the way a messaging app does: tapping
     * the same emoji again takes it back.
     *
     * Only messages still in history can be reacted to, so this cannot be used to grow the map with
     * invented ids. Returns empty when nothing changed.
     */
    public synchronized Optional<Map<String, List<String>>> toggleReaction(
            String messageId, String emoji, String memberId) {
        if (messageId == null || emoji == null || memberId == null || memberId.isBlank()
                || chatHistory.stream().noneMatch(message -> messageId.equals(message.id()))) {
            return Optional.empty();
        }

        Map<String, LinkedHashSet<String>> byEmoji = reactions.computeIfAbsent(messageId, key -> new LinkedHashMap<>());
        LinkedHashSet<String> members = byEmoji.get(emoji);
        if (members != null && members.remove(memberId)) {
            if (members.isEmpty()) {
                byEmoji.remove(emoji);
            }
        } else {
            if (members == null && byEmoji.size() >= MAX_EMOJI_PER_MESSAGE) {
                return Optional.empty();
            }
            byEmoji.computeIfAbsent(emoji, key -> new LinkedHashSet<>()).add(memberId);
        }
        if (byEmoji.isEmpty()) {
            reactions.remove(messageId);
        }
        return Optional.of(reactionsFor(messageId));
    }

    /** One message's reactions, as emoji to the members who chose it. */
    public synchronized Map<String, List<String>> reactionsFor(String messageId) {
        Map<String, LinkedHashSet<String>> byEmoji = reactions.get(messageId);
        if (byEmoji == null) {
            return Map.of();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>();
        byEmoji.forEach((emoji, members) -> copy.put(emoji, List.copyOf(members)));
        return copy;
    }

    /** Every message's reactions, for the snapshot a browser loads the room with. */
    public synchronized Map<String, Map<String, List<String>>> getReactions() {
        Map<String, Map<String, List<String>>> copy = new LinkedHashMap<>();
        reactions.keySet().forEach(messageId -> copy.put(messageId, reactionsFor(messageId)));
        return copy;
    }

    /**
     * Records that a member has received, or read, everything up to {@code through}.
     * Only ever moves forward, so a late or out-of-order acknowledgement cannot un-read a message.
     */
    public void markReceipt(String memberId, boolean read, long through) {
        if (memberId == null || memberId.isBlank()
                || (receipts.size() >= MAX_TRACKED_RECEIPTS && !receipts.containsKey(memberId))) {
            return;
        }
        receipts.merge(
                memberId,
                new MemberReceipt(through, read ? through : 0),
                (current, incoming) -> new MemberReceipt(
                        Math.max(current.deliveredAt(), incoming.deliveredAt()),
                        Math.max(current.readAt(), incoming.readAt())));
    }

    public Map<String, MemberReceipt> getReceipts() {
        return Map.copyOf(receipts);
    }
}
