package com.soundstream.room;

import com.soundstream.room.RoomDtos.PlaybackUpdate;
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
 * In-memory room registry. Rooms disappear when the server restarts.
 */
@Service
public class RoomService {

    private static final String ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ID_LENGTH = 6;
    private static final Pattern SOUNDCLOUD_URL = Pattern.compile("^https://(www\\.|m\\.)?soundcloud\\.com/.+");

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public Room create(String name, String hostName) {
        String token = UUID.randomUUID().toString();
        while (true) {
            Room room = new Room(newId(), name, hostName, token, System.currentTimeMillis());
            if (rooms.putIfAbsent(room.getId(), room) == null) {
                return room;
            }
        }
    }

    public Optional<Room> find(String id) {
        return Optional.ofNullable(rooms.get(id));
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
        if (update.trackUrl() == null || !SOUNDCLOUD_URL.matcher(update.trackUrl()).matches()) {
            return Optional.empty();
        }
        String artwork = update.artworkUrl() != null && update.artworkUrl().startsWith("https://")
                ? update.artworkUrl() : "";
        PlaybackState state = new PlaybackState(
                update.trackUrl(),
                clip(update.title(), 200),
                clip(update.artist(), 100),
                artwork,
                update.playing(),
                Math.max(0, update.positionMs()),
                System.currentTimeMillis());
        room.setPlayback(state);
        return Optional.of(state);
    }

    static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        return stripped.length() > max ? stripped.substring(0, max) : stripped;
    }

    private String newId() {
        StringBuilder id = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
        }
        return id.toString();
    }
}
