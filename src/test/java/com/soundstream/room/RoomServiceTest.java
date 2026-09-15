package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;
import com.soundstream.room.RoomDtos.PlaybackUpdate;
import com.soundstream.room.RoomDtos.QueueUpdate;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomServiceTest {

    private static final String TRACK = "https://soundcloud.com/artist/some-track";

    private final RoomService service = new RoomService();

    @Test
    void createsRoomWithShortIdAndHostToken() {
        Room room = service.create("Friday vibes", "Ziad");

        assertThat(room.getId()).hasSize(6).matches("[A-Z2-9]+");
        assertThat(room.isHost(room.getHostToken())).isTrue();
        assertThat(service.find(room.getId())).contains(room);
    }

    @Test
    void acceptsPlaybackFromHost() {
        Room room = service.create("Room", "Host");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate(room.getHostToken(), TRACK, "Song", "Artist", "", true, 42_000));

        assertThat(state).isPresent();
        assertThat(state.get().positionMs()).isEqualTo(42_000);
        assertThat(state.get().serverTime()).isPositive();
        assertThat(room.getPlayback()).isEqualTo(state.get());
    }

    @Test
    void rejectsPlaybackFromNonHost() {
        Room room = service.create("Room", "Host");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate("not-the-token", TRACK, "Song", "Artist", "", true, 0));

        assertThat(state).isEmpty();
        assertThat(room.getPlayback()).isNull();
    }

    @Test
    void rejectsNonSoundCloudUrls() {
        Room room = service.create("Room", "Host");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate(room.getHostToken(), "https://evil.example/track", "", "", "", true, 0));

        assertThat(state).isEmpty();
    }

    @Test
    void acceptsSoundCloudShareLinks() {
        Room room = service.create("Room", "Host");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate(room.getHostToken(), "https://on.soundcloud.com/AbCdEf", "", "", "", true, 0));

        assertThat(state).isPresent();
    }

    @Test
    void rejectsLookalikeInsecureAndCredentialedUrls() {
        assertThat(RoomService.isSoundCloudUrl("http://soundcloud.com/artist/track")).isFalse();
        assertThat(RoomService.isSoundCloudUrl("https://soundcloud.com.evil.example/artist/track")).isFalse();
        assertThat(RoomService.isSoundCloudUrl("https://user@soundcloud.com/artist/track")).isFalse();
        assertThat(RoomService.isSoundCloudUrl("https://soundcloud.com:8443/artist/track")).isFalse();
        assertThat(RoomService.isSoundCloudUrl("https://soundcloud.com/")).isFalse();
    }

    @Test
    void acceptsQueueUpdatesFromTheHost() {
        Room room = service.create("Room", "Host");
        List<String> tracks = List.of(TRACK, "https://soundcloud.com/artist/next-track");

        var state = service.updateQueue(room.getId(), new QueueUpdate(room.getHostToken(), tracks, 0));

        assertThat(state).isPresent();
        assertThat(state.get().trackUrls()).containsExactlyElementsOf(tracks);
        assertThat(state.get().activeIndex()).isZero();
        assertThat(room.getQueue()).isEqualTo(state.get());
    }

    @Test
    void rejectsUnauthorizedInvalidOrOversizedQueues() {
        Room room = service.create("Room", "Host");

        assertThat(service.updateQueue(room.getId(), new QueueUpdate("wrong", List.of(TRACK), 0))).isEmpty();
        assertThat(service.updateQueue(room.getId(),
                new QueueUpdate(room.getHostToken(), List.of("https://evil.example/track"), 0))).isEmpty();
        assertThat(service.updateQueue(room.getId(),
                new QueueUpdate(room.getHostToken(), List.of(TRACK), 1))).isEmpty();
        assertThat(service.updateQueue(room.getId(),
                new QueueUpdate(room.getHostToken(), Collections.nCopies(101, TRACK), 0))).isEmpty();
    }

    @Test
    void retainsOnlyTheMostRecentFiftyChatMessages() {
        Room room = service.create("Room", "Host");
        for (int i = 0; i < 55; i++) {
            room.addChatMessage(new ChatMessage(Integer.toString(i), "Guest", "Message " + i, false, i));
        }

        assertThat(room.getChatHistory()).hasSize(50);
        assertThat(room.getChatHistory().getFirst().id()).isEqualTo("5");
        assertThat(room.getChatHistory().getLast().id()).isEqualTo("54");
    }
}
