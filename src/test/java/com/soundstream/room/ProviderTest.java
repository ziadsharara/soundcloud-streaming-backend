package com.soundstream.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderTest {

    @Test
    void recognisesEachSupportedService() {
        assertThat(Provider.detect("https://soundcloud.com/forss/flickermood")).contains(Provider.SOUNDCLOUD);
        assertThat(Provider.detect("https://on.soundcloud.com/AbCdEf")).contains(Provider.SOUNDCLOUD);
        assertThat(Provider.detect("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
                .contains(Provider.SPOTIFY);
        assertThat(Provider.detect("https://open.spotify.com/intl-de/album/1ATL5GLyefJaxhQzSPVrLX"))
                .contains(Provider.SPOTIFY);
        assertThat(Provider.detect("https://play.anghami.com/song/1234567")).contains(Provider.ANGHAMI);
    }

    @Test
    void reportsHowFarEachServiceCanBeSynced() {
        assertThat(Provider.SOUNDCLOUD.sync()).isEqualTo(Provider.Sync.FULL);
        assertThat(Provider.SPOTIFY.sync()).isEqualTo(Provider.Sync.PREVIEW);
        assertThat(Provider.ANGHAMI.sync()).isEqualTo(Provider.Sync.NONE);
    }

    @Test
    void rejectsLookalikeInsecureAndCredentialedUrls() {
        assertThat(Provider.isSupported("http://soundcloud.com/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://soundcloud.com.evil.example/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://user@soundcloud.com/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://soundcloud.com:8443/artist/track")).isFalse();
        assertThat(Provider.isSupported("https://soundcloud.com/")).isFalse();
        assertThat(Provider.isSupported("https://open.spotify.com/track/../../etc")).isFalse();
        assertThat(Provider.isSupported("javascript:alert(1)")).isFalse();
        assertThat(Provider.isSupported(null)).isFalse();
    }
}
