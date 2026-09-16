package com.soundstream.room;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PresenceTrackerTest {

    private final RoomService rooms = new RoomService();
    private final PresenceTracker presence = new PresenceTracker(rooms, mock(SimpMessagingTemplate.class));
    private final Room room = rooms.create("Room", "Host", "bun");

    @Test
    void listsEveryoneWhoJoined() {
        presence.join(room.getId(), "s1", "member-0001", "Ziad", "bun", true);
        presence.join(room.getId(), "s2", "member-0002", "Sara", "curls", false);

        assertThat(presence.members(room.getId()))
                .extracting(Member::name)
                .containsExactly("Ziad", "Sara");
        assertThat(presence.count(room.getId())).isEqualTo(2);
    }

    @Test
    void countsOnePersonOnceEvenWithTheRoomOpenTwice() {
        presence.join(room.getId(), "s1", "member-0001", "Sara", "curls", false);
        presence.join(room.getId(), "s2", "member-0001", "Sara", "curls", false);

        assertThat(presence.count(room.getId())).isEqualTo(1);
    }

    @Test
    void startsMembersAsPresentButNotYetListening() {
        presence.join(room.getId(), "s1", "member-0001", "Sara", "curls", false);

        assertThat(presence.members(room.getId())).singleElement()
                .extracting(Member::listening).isEqualTo(false);
    }

    @Test
    void reportsAPersonAsListeningWhenAnyOfTheirTabsIsPlaying() {
        presence.join(room.getId(), "s1", "member-0001", "Sara", "curls", false);
        presence.join(room.getId(), "s2", "member-0001", "Sara", "curls", false);

        presence.setListening("s2", true);

        assertThat(presence.members(room.getId())).singleElement()
                .extracting(Member::listening).isEqualTo(true);
    }

    @Test
    void forgetsAMemberWhoseSocketClosed() {
        presence.join(room.getId(), "s1", "member-0001", "Sara", "curls", false);

        presence.onDisconnect(new org.springframework.web.socket.messaging.SessionDisconnectEvent(
                this,
                org.springframework.messaging.support.MessageBuilder.withPayload(new byte[0]).build(),
                "s1",
                org.springframework.web.socket.CloseStatus.NORMAL));

        assertThat(presence.members(room.getId())).isEmpty();
    }
}
