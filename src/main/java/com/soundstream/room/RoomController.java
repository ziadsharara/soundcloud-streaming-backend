package com.soundstream.room;

import com.soundstream.room.RoomDtos.CreateRoomRequest;
import com.soundstream.room.RoomDtos.CreateRoomResponse;
import com.soundstream.room.RoomDtos.RoomSummary;
import com.soundstream.room.RoomDtos.UnlockRequest;
import com.soundstream.room.RoomDtos.UnlockResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    /** Presented by anyone who has already unlocked a private room. */
    static final String ROOM_KEY_HEADER = "X-Room-Key";

    private final RoomService rooms;
    private final PresenceTracker presence;
    private final SimpMessagingTemplate messaging;

    public RoomController(RoomService rooms, PresenceTracker presence, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.presence = presence;
        this.messaging = messaging;
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
        Room room = rooms.create(request.name().strip(), request.hostName().strip(),
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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable String id, @RequestHeader("X-Host-Token") String hostToken) {
        Room room = findOr404(id);
        if (!room.isHost(hostToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can end this stream");
        }
        rooms.remove(id);
        Object payload = Map.of("roomId", id, "reason", "host");
        messaging.convertAndSend(RoomTopics.closed(id), payload);
    }

    private Room findOr404(String id) {
        return rooms.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }
}
