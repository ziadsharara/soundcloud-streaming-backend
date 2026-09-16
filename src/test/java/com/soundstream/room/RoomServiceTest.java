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
        Room room = service.create("Friday vibes", "Ziad", "fox");

        assertThat(room.getId()).hasSize(6).matches("[A-Z2-9]+");
        assertThat(room.isHost(room.getHostToken())).isTrue();
        assertThat(room.getHostAvatarId()).isEqualTo("fox");
        assertThat(service.find(room.getId())).contains(room);
    }

    @Test
    void fallsBackToADefaultAvatarWhenTheIdIsMissingOrUnusable() {
        assertThat(service.create("Room", "Host", null).getHostAvatarId()).isEqualTo(RoomService.DEFAULT_AVATAR);
        assertThat(service.create("Room", "Host", "<script>").getHostAvatarId()).isEqualTo(RoomService.DEFAULT_AVATAR);
    }

    @Test
    void acceptsPlaybackFromHost() {
        Room room = service.create("Room", "Host", "fox");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate(room.getHostToken(), TRACK, "Song", "Artist", "", true, 42_000));

        assertThat(state).isPresent();
        assertThat(state.get().positionMs()).isEqualTo(42_000);
        assertThat(state.get().serverTime()).isPositive();
        assertThat(state.get().provider()).isEqualTo(Provider.SOUNDCLOUD);
        assertThat(state.get().sync()).isEqualTo(Provider.Sync.FULL);
        assertThat(room.getPlayback()).isEqualTo(state.get());
    }

    @Test
    void labelsSpotifyAsPreviewOnlyAndAnghamiAsUnsynced() {
        Room room = service.create("Room", "Host", "fox");

        var spotify = service.updatePlayback(room.getId(), new PlaybackUpdate(room.getHostToken(),
                "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", "Song", "Artist", "", true, 0));
        var anghami = service.updatePlayback(room.getId(), new PlaybackUpdate(room.getHostToken(),
                "https://play.anghami.com/song/1234567", "Song", "Artist", "", true, 0));

        assertThat(spotify).get().extracting(PlaybackState::provider, PlaybackState::sync)
                .containsExactly(Provider.SPOTIFY, Provider.Sync.PREVIEW);
        assertThat(anghami).get().extracting(PlaybackState::provider, PlaybackState::sync)
                .containsExactly(Provider.ANGHAMI, Provider.Sync.NONE);
    }

    @Test
    void rejectsPlaybackFromNonHost() {
        Room room = service.create("Room", "Host", "fox");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate("not-the-token", TRACK, "Song", "Artist", "", true, 0));

        assertThat(state).isEmpty();
        assertThat(room.getPlayback()).isNull();
    }

    @Test
    void rejectsUnsupportedProviders() {
        Room room = service.create("Room", "Host", "fox");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate(room.getHostToken(), "https://evil.example/track", "", "", "", true, 0));

        assertThat(state).isEmpty();
    }

    @Test
    void acceptsSoundCloudShareLinks() {
        Room room = service.create("Room", "Host", "fox");

        var state = service.updatePlayback(room.getId(),
                new PlaybackUpdate(room.getHostToken(), "https://on.soundcloud.com/AbCdEf", "", "", "", true, 0));

        assertThat(state).isPresent();
    }

    @Test
    void acceptsQueueUpdatesFromTheHostAcrossProviders() {
        Room room = service.create("Room", "Host", "fox");
        List<String> tracks = List.of(TRACK, "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M");

        var state = service.updateQueue(room.getId(), new QueueUpdate(room.getHostToken(), tracks, 0));

        assertThat(state).isPresent();
        assertThat(state.get().trackUrls()).containsExactlyElementsOf(tracks);
        assertThat(state.get().activeIndex()).isZero();
        assertThat(room.getQueue()).isEqualTo(state.get());
    }

    @Test
    void rejectsUnauthorizedInvalidOrOversizedQueues() {
        Room room = service.create("Room", "Host", "fox");

        assertThat(service.updateQueue(room.getId(), new QueueUpdate("wrong", List.of(TRACK), 0))).isEmpty();
        assertThat(service.updateQueue(room.getId(),
                new QueueUpdate(room.getHostToken(), List.of("https://evil.example/track"), 0))).isEmpty();
        assertThat(service.updateQueue(room.getId(),
                new QueueUpdate(room.getHostToken(), List.of(TRACK), 1))).isEmpty();
        assertThat(service.updateQueue(room.getId(),
                new QueueUpdate(room.getHostToken(), Collections.nCopies(101, TRACK), 0))).isEmpty();
    }

    @Test
    void acceptsOnlySlugStickerIds() {
        assertThat(RoomService.sticker("vinyl-spin")).contains("vinyl-spin");
        assertThat(RoomService.sticker("VINYL")).contains("vinyl");
        assertThat(RoomService.sticker("<img src=x>")).isEmpty();
        assertThat(RoomService.sticker("")).isEmpty();
    }

    @Test
    void retainsOnlyTheMostRecentFiftyChatMessages() {
        Room room = service.create("Room", "Host", "fox");
        for (int i = 0; i < 55; i++) {
            room.addChatMessage(new ChatMessage(Integer.toString(i), "session", "Guest", "fox",
                    "TEXT", "Message " + i, "", false, i));
        }

        assertThat(room.getChatHistory()).hasSize(50);
        assertThat(room.getChatHistory().getFirst().id()).isEqualTo("5");
        assertThat(room.getChatHistory().getLast().id()).isEqualTo("54");
    }
}
