package com.soundstream.room;

import com.soundstream.room.RoomDtos.PlaybackUpdate;
import org.junit.jupiter.api.Test;

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
}
