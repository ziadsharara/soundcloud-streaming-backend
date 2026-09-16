package com.soundstream.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderTest {

    @Test
    void recognisesSoundCloudLinks() {
        assertThat(Provider.detect("https://soundcloud.com/forss/flickermood")).contains(Provider.SOUNDCLOUD);
        assertThat(Provider.detect("https://on.soundcloud.com/AbCdEf")).contains(Provider.SOUNDCLOUD);
        assertThat(Provider.detect("https://m.soundcloud.com/artist/track")).contains(Provider.SOUNDCLOUD);
    }

    @Test
    void recognisesYouTubeMusicAndYouTubeInEveryShareShape() {
        assertThat(Provider.detect("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
                .contains(Provider.YOUTUBE_MUSIC);
        assertThat(Provider.detect("https://music.youtube.com/playlist?list=PLabc123"))
                .contains(Provider.YOUTUBE_MUSIC);
        assertThat(Provider.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                .contains(Provider.YOUTUBE_MUSIC);
        assertThat(Provider.detect("https://youtu.be/dQw4w9WgXcQ")).contains(Provider.YOUTUBE_MUSIC);
    }

    @Test
    void noLongerAcceptsServicesThatCannotBeSynced() {
        assertThat(Provider.detect("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")).isEmpty();
        assertThat(Provider.detect("https://play.anghami.com/song/1234567")).isEmpty();
        assertThat(Provider.detect("https://music.apple.com/us/album/thriller/1440843616")).isEmpty();
    }

    @Test
    void rejectsLookalikeInsecureAndCredentialedUrls() {
        assertThat(Provider.isSupported("http://soundcloud.com/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://soundcloud.com.evil.example/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://user@soundcloud.com/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://soundcloud.com:8443/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://soundcloud.com/")).isFalse();
        assertThat(Provider.isSupported("https://www.youtube.com/feed/subscriptions")).isFalse();
        assertThat(Provider.isSupported("javascript:alert(1)")).isFalse();
        assertThat(Provider.isSupported(null)).isFalse();
    }
}
