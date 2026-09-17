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
    private final AttachmentStore attachments = mock(AttachmentStore.class);

    @Test
    void removesRoomsNobodyHasTouchedForAVeryLongTime() {
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
    void keepsARoomEveryoneHasLeft() {
        // A room belongs to its host: listeners leaving, or the host closing the tab, must not
        // end it. Only the host ending it does, and the sweep is a day-long safety net.
        Room room = rooms.create("Just left", "Host", "fox");
        presence.join(room.getId(), "session-1", "member-0001", "Guest", "fox", false);
        presence.onDisconnect(new org.springframework.web.socket.messaging.SessionDisconnectEvent(
                this, org.springframework.messaging.support.MessageBuilder.withPayload(new byte[0]).build(),
                "session-1", org.springframework.web.socket.CloseStatus.NORMAL));

        cleanupWithGrace(Duration.ofHours(24)).removeEmptyRooms();

        assertThat(rooms.find(room.getId())).isPresent();
    }

    private RoomCleanupService cleanupWithGrace(Duration grace) {
        return new RoomCleanupService(rooms, presence, messaging, attachments, grace);
    }
}
