package com.soundstream.room;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RoomCleanupServiceTest {

    private final RoomService rooms = new RoomService();
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final PresenceTracker presence = new PresenceTracker(rooms, messaging);

    @Test
    void removesRoomsThatHaveBeenEmptyPastTheGracePeriod() {
        Room room = rooms.create("Abandoned", "Host", "fox");

        cleanupWithGrace(Duration.ZERO).removeEmptyRooms();

        assertThat(rooms.find(room.getId())).isEmpty();
    }

    @Test
    void keepsRoomsThatStillHaveSomebodyInThem() {
        Room room = rooms.create("Busy", "Host", "fox");
        presence.join(room.getId(), "session-1", "member-0001", "Guest", "fox", false);

        cleanupWithGrace(Duration.ZERO).removeEmptyRooms();

        assertThat(rooms.find(room.getId())).isPresent();
    }

    @Test
    void keepsAnEmptyRoomUntilItsGracePeriodExpires() {
        Room room = rooms.create("Just left", "Host", "fox");

        cleanupWithGrace(Duration.ofMinutes(10)).removeEmptyRooms();

        assertThat(rooms.find(room.getId())).isPresent();
    }

    private RoomCleanupService cleanupWithGrace(Duration grace) {
        return new RoomCleanupService(rooms, presence, messaging, grace);
    }
}
