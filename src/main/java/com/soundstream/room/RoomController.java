package com.soundstream.room;

import com.soundstream.room.RoomDtos.AttachmentResponse;
import com.soundstream.room.RoomDtos.CreateRoomRequest;
import com.soundstream.room.RoomDtos.CreateRoomResponse;
import com.soundstream.room.RoomDtos.RoomSummary;
import com.soundstream.room.RoomDtos.UnlockRequest;
import com.soundstream.room.RoomDtos.UnlockResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    /** Presented by anyone who has already unlocked a private room. */
    static final String ROOM_KEY_HEADER = "X-Room-Key";

    /**
     * Types a browser may render in place. Everything else is sent as a download, so nothing
     * uploaded to a room can be opened as a page on the API's own origin.
     */
    private static final Set<String> INLINE_TYPES = Set.of("image/", "audio/", "video/");

    private final RoomService rooms;
    private final PresenceTracker presence;
    private final SimpMessagingTemplate messaging;
    private final AttachmentStore attachments;

    public RoomController(RoomService rooms, PresenceTracker presence, SimpMessagingTemplate messaging,
                          AttachmentStore attachments) {
        this.rooms = rooms;
        this.presence = presence;
        this.messaging = messaging;
        this.attachments = attachments;
    }

    /** Only public rooms are listed; a private one is found by code, then password. */
    @GetMapping
    public List<RoomSummary> list() {
        return rooms.listPublic().stream()
                .map(room -> RoomSummary.of(room, presence.members(room.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public RoomSummary get(@PathVariable String id,
                           @RequestHeader(value = ROOM_KEY_HEADER, required = false) String roomKey) {
        Room room = findOr404(id);
        // A locked room still answers, with just enough to draw the password door.
        return room.allows(roomKey) ? RoomSummary.of(room, presence.members(id)) : RoomSummary.locked(room);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRoomResponse create(@Valid @RequestBody CreateRoomRequest request) {
        RoomKind kind = RoomKind.parse(request.kind());
        // A chat room is a private place to talk by definition; it is not offered without a password.
        if (kind == RoomKind.CHAT && (request.password() == null || request.password().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A chat room needs a password");
        }
        Room room = rooms.create(request.name().strip(), kind, request.hostName().strip(),
                request.hostAvatarId(), request.password());
        return new CreateRoomResponse(RoomSummary.of(room, List.of()), room.getHostToken(), room.getAccessKey());
    }

    /** Trades the room password for the key that opens its REST data and its live topics. */
    @PostMapping("/{id}/unlock")
    public UnlockResponse unlock(@PathVariable String id, @RequestBody UnlockRequest request) {
        Room room = findOr404(id);
        if (!room.isPrivate()) {
            return new UnlockResponse(room.getAccessKey());
        }
        if (request == null || !room.matchesPassword(request.password())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That password does not open this room");
        }
        return new UnlockResponse(room.getAccessKey());
    }

    /**
     * Takes a voice note, a video note, a video or any other file into the room.
     *
     * It is stored against the room and reachable only by someone the room lets in, and it is
     * deleted with the room — a reload keeps it, ending the room does not.
     */
    @PostMapping("/{id}/attachments")
    public AttachmentResponse upload(@PathVariable String id,
                                     @RequestHeader(value = ROOM_KEY_HEADER, required = false) String roomKey,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "kind", required = false) String kind,
                                     @RequestParam(value = "memberId", required = false) String memberId,
                                     @RequestParam(value = "durationMs", required = false) Long durationMs) {
        Room room = allowedOr403(id, roomKey);
        try {
            room.markOccupied();
            return new AttachmentResponse(
                    attachments.save(room.getId(), kind, memberId, durationMs == null ? 0 : durationMs, file));
        } catch (AttachmentStore.RejectedException e) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, e.getMessage());
        }
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable String id, @PathVariable String attachmentId,
                                             @RequestHeader(value = ROOM_KEY_HEADER, required = false) String roomKey) {
        Room room = allowedOr403(id, roomKey);
        Attachment attachment = attachments.find(room.getId(), attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That file is no longer here"));
        Path path = attachments.open(room.getId(), attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That file is no longer here"));

        boolean inline = INLINE_TYPES.stream().anyMatch(prefix -> attachment.contentType().startsWith(prefix))
                && !attachment.contentType().startsWith("image/svg");
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(attachment.name(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        // Anything the room does not play or show is served as bytes, under its own type only if
        // that type is one a browser renders safely.
        MediaType type = inline
                ? MediaType.parseMediaType(attachment.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(type)
                .contentLength(attachment.size())
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable String id, @RequestHeader("X-Host-Token") String hostToken) {
        Room room = findOr404(id);
        if (!room.isHost(hostToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can end this stream");
        }
        rooms.remove(id);
        // Everything sent to the room goes with it.
        attachments.removeRoom(id);
        Object payload = Map.of("roomId", id, "reason", "host");
        messaging.convertAndSend(RoomTopics.closed(id), payload);
    }

    /** Spring's own message for an oversized upload says nothing about the limit. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> tooBig() {
        return Map.of("detail", "That file is larger than 25 MB.");
    }

    /** A private room's files need the same key its topics do. */
    private Room allowedOr403(String id, String roomKey) {
        Room room = findOr404(id);
        if (!room.allows(roomKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This room is private");
        }
        return room;
    }

    private Room findOr404(String id) {
        return rooms.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }
}
