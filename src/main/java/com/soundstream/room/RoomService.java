package com.soundstream.room;

import com.soundstream.room.RoomDtos.PlaybackUpdate;
import com.soundstream.room.RoomDtos.QueueUpdate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * In-memory room registry. Rooms disappear when the server restarts, and empty ones are
 * swept away by {@link RoomCleanupService}.
 */
@Service
public class RoomService {

    private static final String ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ID_LENGTH = 6;
    private static final int MAX_QUEUE_SIZE = 100;
    /** Avatars and stickers are picked from drawn sets in the frontend; ids are slugs. */
    private static final Pattern SLUG = Pattern.compile("[a-z0-9-]{1,24}");
    static final String DEFAULT_AVATAR = "bun";

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public Room create(String name, String hostName, String hostAvatarId) {
        return create(name, hostName, hostAvatarId, null);
    }

    /** A blank password makes a public room; anything else locks it. */
    public Room create(String name, String hostName, String hostAvatarId, String password) {
        String token = UUID.randomUUID().toString();
        String passwordHash = password == null || password.isBlank() ? "" : RoomPassword.hash(password.strip());
        String accessKey = RoomPassword.newAccessKey();
        while (true) {
            Room room = new Room(newId(), name, hostName, avatarOrDefault(hostAvatarId), token,
                    passwordHash, accessKey, System.currentTimeMillis());
            if (rooms.putIfAbsent(room.getId(), room) == null) {
                return room;
            }
        }
    }

    public Optional<Room> find(String id) {
        return Optional.ofNullable(rooms.get(id));
    }

    /** Only public rooms are discoverable; a private one is reachable by code and password alone. */
    public List<Room> listPublic() {
        return list().stream().filter(room -> !room.isPrivate()).toList();
    }

    public List<Room> list() {
        return rooms.values().stream()
                .sorted(Comparator.comparingLong((Room r) -> r.getCreatedAt()).reversed())
                .toList();
    }

    public void remove(String id) {
        rooms.remove(id);
    }

    /**
     * Applies a playback update if it comes from the room's host.
     * Returns the new state, or empty if the update was rejected.
     */
    public Optional<PlaybackState> updatePlayback(String roomId, PlaybackUpdate update) {
        Room room = rooms.get(roomId);
        if (room == null || update == null || !room.isHost(update.hostToken())) {
            return Optional.empty();
        }
        Optional<Provider> provider = Provider.detect(update.trackUrl());
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        String artwork = update.artworkUrl() != null && update.artworkUrl().startsWith("https://")
                ? update.artworkUrl() : "";
        PlaybackState state = new PlaybackState(
                update.trackUrl().strip(),
                provider.get(),
                clip(update.title(), 200),
                clip(update.artist(), 100),
                artwork,
                update.playing(),
                Math.max(0, update.positionMs()),
                System.currentTimeMillis());
        room.setPlayback(state);
        return Optional.of(state);
    }

    /** Applies a bounded, host-authorized queue update. */
    public Optional<QueueState> updateQueue(String roomId, QueueUpdate update) {
        Room room = rooms.get(roomId);
        if (room == null || update == null || !room.isHost(update.hostToken())
                || update.trackUrls() == null || update.trackUrls().size() > MAX_QUEUE_SIZE) {
            return Optional.empty();
        }
        List<String> trackUrls = update.trackUrls().stream()
                .map(url -> url == null ? "" : url.strip())
                .toList();
        if (trackUrls.stream().anyMatch(url -> !Provider.isSupported(url))) {
            return Optional.empty();
        }
        int activeIndex = update.activeIndex();
        if (activeIndex < -1 || activeIndex >= trackUrls.size()) {
            return Optional.empty();
        }
        QueueState state = new QueueState(List.copyOf(trackUrls), activeIndex, System.currentTimeMillis());
        room.setQueue(state);
        return Optional.of(state);
    }

    static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() > max ? stripped.substring(0, max) : stripped;
    }

    /** Keeps unknown ids out of the UI, where they would render as a missing drawing. */
    static String avatarOrDefault(String avatarId) {
        String slug = clip(avatarId, 24).toLowerCase(java.util.Locale.ROOT);
        return SLUG.matcher(slug).matches() ? slug : DEFAULT_AVATAR;
    }

    static Optional<String> sticker(String stickerId) {
        String slug = clip(stickerId, 24).toLowerCase(java.util.Locale.ROOT);
        return SLUG.matcher(slug).matches() ? Optional.of(slug) : Optional.empty();
    }

    private String newId() {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }
}
