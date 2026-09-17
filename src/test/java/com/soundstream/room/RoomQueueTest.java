package com.soundstream.room;

import com.soundstream.room.RoomDtos.QueueUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The queue is shared: everyone can add to it, only the host can take things out of it. */
class RoomQueueTest {

    private static final String ONE = "https://soundcloud.com/artist/one";
    private static final String TWO = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    private final RoomService service = new RoomService();

    @Test
    void anyoneCanAddATrackToTheEndOfTheQueue() {
        Room room = service.create("Room", "Host", "bun");

        assertThat(service.addToQueue(room.getId(), ONE)).isPresent();
        Optional<QueueState> second = service.addToQueue(room.getId(), TWO);

        assertThat(second).isPresent();
        assertThat(second.get().trackUrls()).containsExactly(ONE, TWO);
        // Adding does not change what is playing.
        assertThat(second.get().activeIndex()).isEqualTo(-1);
    }

    @Test
    void addingTheSameTrackTwiceChangesNothing() {
        Room room = service.create("Room", "Host", "bun");
        service.addToQueue(room.getId(), ONE);

        assertThat(service.addToQueue(room.getId(), ONE)).isEmpty();
        assertThat(room.getQueue().trackUrls()).containsExactly(ONE);
    }

    @Test
    void onlyLinksTheRoomCanPlayAreAccepted() {
        Room room = service.create("Room", "Host", "bun");

        assertThat(service.addToQueue(room.getId(), "https://example.com/song.mp3")).isEmpty();
        assertThat(service.addToQueue(room.getId(), "  ")).isEmpty();
        assertThat(service.addToQueue(room.getId(), null)).isEmpty();
        assertThat(room.getQueue().trackUrls()).isEmpty();
    }

    @Test
    void theQueueIsBounded() {
        Room room = service.create("Room", "Host", "bun");
        for (int i = 0; i < 100; i++) {
            assertThat(service.addToQueue(room.getId(), ONE + i)).isPresent();
        }

        assertThat(service.addToQueue(room.getId(), TWO)).isEmpty();
        assertThat(room.getQueue().trackUrls()).hasSize(100);
    }

    @Test
    void removingATrackNeedsTheHostToken() {
        Room room = service.create("Room", "Host", "bun");
        service.addToQueue(room.getId(), ONE);
        service.addToQueue(room.getId(), TWO);

        // A guest replaying the host's own message with a guessed token keeps both tracks.
        assertThat(service.updateQueue(room.getId(), new QueueUpdate("not-the-token", List.of(ONE), 0))).isEmpty();
        assertThat(room.getQueue().trackUrls()).containsExactly(ONE, TWO);

        assertThat(service.updateQueue(room.getId(), new QueueUpdate(room.getHostToken(), List.of(ONE), 0)))
                .isPresent();
        assertThat(room.getQueue().trackUrls()).containsExactly(ONE);
    }

    @Test
    void aChatRoomHasNoQueueAtAll() {
        Room room = service.create("Talk", RoomKind.CHAT, "Host", "bun", "hunter2");

        assertThat(service.addToQueue(room.getId(), ONE)).isEmpty();
        assertThat(service.updateQueue(room.getId(), new QueueUpdate(room.getHostToken(), List.of(ONE), 0)))
                .isEmpty();
    }
}
