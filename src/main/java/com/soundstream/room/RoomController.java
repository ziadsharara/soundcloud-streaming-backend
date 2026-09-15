package com.soundstream.room;

import com.soundstream.room.RoomDtos.CreateRoomRequest;
import com.soundstream.room.RoomDtos.CreateRoomResponse;
import com.soundstream.room.RoomDtos.RoomSummary;
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

    private final RoomService rooms;
    private final PresenceTracker presence;
    private final SimpMessagingTemplate messaging;

    public RoomController(RoomService rooms, PresenceTracker presence, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.presence = presence;
        this.messaging = messaging;
    }

    @GetMapping
    public List<RoomSummary> list() {
        return rooms.list().stream()
                .map(room -> RoomSummary.of(room, presence.count(room.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public RoomSummary get(@PathVariable String id) {
        Room room = findOr404(id);
        return RoomSummary.of(room, presence.count(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRoomResponse create(@Valid @RequestBody CreateRoomRequest request) {
        Room room = rooms.create(request.name().strip(), request.hostName().strip());
        return new CreateRoomResponse(RoomSummary.of(room, 0), room.getHostToken());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable String id, @RequestHeader("X-Host-Token") String hostToken) {
        Room room = findOr404(id);
        if (!room.isHost(hostToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can end this stream");
        }
        rooms.remove(id);
        Object payload = Map.of("roomId", id);
        messaging.convertAndSend(RoomTopics.closed(id), payload);
    }

    private Room findOr404(String id) {
        return rooms.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }
}
