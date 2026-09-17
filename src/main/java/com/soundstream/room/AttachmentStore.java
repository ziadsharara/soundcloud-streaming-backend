package com.soundstream.room;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * The voice notes, videos and files people send to a room.
 *
 * They belong to the room, not to the people in it: they survive a reload, because they are here
 * rather than in a browser, and they are deleted the moment the room is — by its host, or by the
 * sweep. The bytes are kept on disk, never in the heap, so a long video does not cost the server
 * the memory it would take to hold it.
 */
@Service
public class AttachmentStore {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStore.class);
    /** Anything bigger is refused; it is also the multipart limit, so this is the polite message. */
    static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    static final long MAX_ROOM_BYTES = 200L * 1024 * 1024;
    static final int MAX_ROOM_FILES = 200;
    static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 120;

    private final Path root;
    private final Map<String, Map<String, Attachment>> byRoom = new ConcurrentHashMap<>();
    private final AtomicLong totalBytes = new AtomicLong();

    public AttachmentStore(@Value("${app.attachments.dir:}") String directory) throws IOException {
        this.root = directory == null || directory.isBlank()
                ? Files.createTempDirectory("soundstream-attachments")
                : Files.createDirectories(Path.of(directory));
    }

    /** Reasons an upload was refused, in words a person can act on. */
    public static final class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }
    }

    public Attachment save(String roomId, String requestedKind, String memberId, long durationMs, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RejectedException("There was nothing in that file");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new RejectedException("That file is larger than 25 MB");
        }
        Map<String, Attachment> room = byRoom.computeIfAbsent(roomId, key -> new ConcurrentHashMap<>());
        if (room.size() >= MAX_ROOM_FILES) {
            throw new RejectedException("This room is holding as many files as it can");
        }
        long roomBytes = room.values().stream().mapToLong(Attachment::size).sum();
        if (roomBytes + file.getSize() > MAX_ROOM_BYTES) {
            throw new RejectedException("This room has no room left for more files");
        }
        if (totalBytes.get() + file.getSize() > MAX_TOTAL_BYTES) {
            throw new RejectedException("SoundStream is out of space for attachments right now");
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        Attachment attachment = new Attachment(
                id,
                safeName(file.getOriginalFilename(), requestedKind),
                contentType(file.getContentType()),
                file.getSize(),
                AttachmentKind.of(requestedKind, file.getContentType()),
                Math.max(0, durationMs),
                memberId,
                System.currentTimeMillis());
        try {
            Path directory = Files.createDirectories(root.resolve(roomId));
            file.transferTo(directory.resolve(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        room.put(id, attachment);
        totalBytes.addAndGet(file.getSize());
        return attachment;
    }

    public Optional<Attachment> find(String roomId, String attachmentId) {
        return Optional.ofNullable(byRoom.get(roomId)).map(room -> room.get(attachmentId));
    }

    /** Where one attachment's bytes are, if they are still here. */
    public Optional<Path> open(String roomId, String attachmentId) {
        return find(roomId, attachmentId)
                .map(attachment -> root.resolve(roomId).resolve(attachment.id()))
                .filter(Files::isReadable);
    }

    /** Everything a room is holding goes when the room does. */
    public void removeRoom(String roomId) {
        Map<String, Attachment> room = byRoom.remove(roomId);
        if (room == null) {
            return;
        }
        totalBytes.addAndGet(-room.values().stream().mapToLong(Attachment::size).sum());
        deleteTree(root.resolve(roomId));
    }

    @PreDestroy
    void removeEverything() {
        byRoom.clear();
        totalBytes.set(0);
        deleteTree(root);
    }

    private void deleteTree(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> entries = Files.walk(path)) {
            entries.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    log.warn("Could not delete {}", entry, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clear attachments at {}", path, e);
        }
    }

    /**
     * A display name only — the bytes are stored under a generated id, so nothing a browser sends
     * can reach outside the room's own directory.
     */
    private static String safeName(String original, String kind) {
        String name = original == null ? "" : original.strip();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = slash >= 0 ? name.substring(slash + 1) : name;
        name = name.replace("\n", "").replace("\r", "").replace("\"", "");
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            name = defaultName(kind);
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static String defaultName(String kind) {
        AttachmentKind parsed = AttachmentKind.of(kind, "");
        return switch (parsed) {
            case VOICE -> "Voice note";
            case VIDEO_NOTE -> "Video note";
            default -> "Attachment";
        };
    }

    private static String contentType(String declared) {
        String type = declared == null ? "" : declared.strip().toLowerCase(Locale.ROOT);
        // One line, no parameters we did not choose: this string is echoed in a response header.
        if (type.isEmpty() || !type.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+(;\\s*[a-z0-9=;\\s.+-]*)?")) {
            return "application/octet-stream";
        }
        return type;
    }
}
