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
 * A safety net for rooms nobody will ever come back to — not a tidy-up of quiet ones.
 *
 * A room belongs to its host: it stays open when the listeners leave, when the host closes the tab,
 * and overnight, and it ends when the host ends it. This only removes rooms that have had nobody in
 * them and no traffic for a very long time, so a server that is never restarted does not hold every
 * room ever made in memory.
 */
@Service
public class RoomCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupService.class);

    private final RoomService rooms;
    private final PresenceTracker presence;
    private final SimpMessagingTemplate messaging;
    private final AttachmentStore attachments;
    private final Duration emptyGrace;

    public RoomCleanupService(RoomService rooms, PresenceTracker presence, SimpMessagingTemplate messaging,
                              AttachmentStore attachments,
                              @Value("${app.rooms.empty-grace:PT24H}") Duration emptyGrace) {
        this.rooms = rooms;
        this.presence = presence;
        this.messaging = messaging;
        this.attachments = attachments;
        this.emptyGrace = emptyGrace;
    }

    @Scheduled(fixedDelayString = "${app.rooms.cleanup-interval:PT1M}")
    public void removeEmptyRooms() {
        long cutoff = System.currentTimeMillis() - emptyGrace.toMillis();
        List<Room> expired = rooms.list().stream()
                .filter(room -> presence.count(room.getId()) == 0)
                // <= so a zero grace period, which the tests use, means "as soon as it is empty".
                .filter(room -> room.getLastOccupiedAt() <= cutoff)
                .toList();

        for (Room room : expired) {
            rooms.remove(room.getId());
            attachments.removeRoom(room.getId());
            Object payload = Map.of("roomId", room.getId(), "reason", "abandoned");
            messaging.convertAndSend(RoomTopics.closed(room.getId()), payload);
            log.info("Removed abandoned room {} after {} with nobody in it", room.getId(), emptyGrace);
        }
    }
}
