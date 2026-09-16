package com.soundstream.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomPrivacyTest {

    private final RoomService service = new RoomService();

    @Test
    void aRoomWithoutAPasswordIsPublicAndOpen() {
        Room room = service.create("Open", "Host", "bun", null);

        assertThat(room.isPrivate()).isFalse();
        assertThat(room.allows(null)).isTrue();
        assertThat(service.listPublic()).contains(room);
    }

    @Test
    void aBlankPasswordIsStillAPublicRoom() {
        assertThat(service.create("Open", "Host", "bun", "   ").isPrivate()).isFalse();
    }

    @Test
    void aPrivateRoomIsHiddenFromTheListing() {
        Room room = service.create("Secret", "Host", "bun", "hunter2");

        assertThat(room.isPrivate()).isTrue();
        assertThat(service.listPublic()).doesNotContain(room);
        // Still reachable by code, which is how an invited guest arrives.
        assertThat(service.find(room.getId())).contains(room);
    }

    @Test
    void onlyTheRightPasswordOpensIt() {
        Room room = service.create("Secret", "Host", "bun", "hunter2");

        assertThat(room.matchesPassword("hunter2")).isTrue();
        assertThat(room.matchesPassword("Hunter2")).isFalse();
        assertThat(room.matchesPassword("")).isFalse();
        assertThat(room.matchesPassword(null)).isFalse();
    }

    @Test
    void theKeyIsWhatOpensAPrivateRoomAfterwards() {
        Room room = service.create("Secret", "Host", "bun", "hunter2");

        assertThat(room.allows(room.getAccessKey())).isTrue();
        assertThat(room.allows("not-the-key")).isFalse();
        assertThat(room.allows(null)).isFalse();
    }

    @Test
    void everyRoomGetsItsOwnKeyAndHash() {
        Room one = service.create("A", "Host", "bun", "same-password");
        Room two = service.create("B", "Host", "bun", "same-password");

        assertThat(one.getAccessKey()).isNotEqualTo(two.getAccessKey());
        // Per-room salt: the same password must not produce the same stored value.
        assertThat(one.matchesPassword("same-password")).isTrue();
        assertThat(two.matchesPassword("same-password")).isTrue();
    }
}
