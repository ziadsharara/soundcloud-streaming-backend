package com.soundstream.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Closes rooms nobody is in. A room is kept for a grace period after the last member leaves so a
 * host who reloads, loses Wi-Fi, or switches networks comes back to the same room and the same code.
 */
@Service
public class RoomCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupService.class);

    private final RoomService rooms;
    private final PresenceTracker presence;
    private final SimpMessagingTemplate messaging;
    private final Duration emptyGrace;

    public RoomCleanupService(RoomService rooms, PresenceTracker presence, SimpMessagingTemplate messaging,
                              @Value("${app.rooms.empty-grace:PT10M}") Duration emptyGrace) {
        this.rooms = rooms;
        this.presence = presence;
        this.messaging = messaging;
        this.emptyGrace = emptyGrace;
    }

    @Scheduled(fixedDelayString = "${app.rooms.cleanup-interval:PT1M}")
    public void removeEmptyRooms() {
        long cutoff = System.currentTimeMillis() - emptyGrace.toMillis();
        List<Room> expired = rooms.list().stream()
                .filter(room -> presence.count(room.getId()) == 0)
                // <= so a zero grace period means "close as soon as the room is empty".
                .filter(room -> room.getLastOccupiedAt() <= cutoff)
                .toList();

        for (Room room : expired) {
            rooms.remove(room.getId());
            Object payload = Map.of("roomId", room.getId(), "reason", "empty");
            messaging.convertAndSend(RoomTopics.closed(room.getId()), payload);
            log.info("Closed empty room {} after {} without members", room.getId(), emptyGrace);
        }
    }
}
