package com.soundstream.room;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentStoreTest {

    @TempDir
    Path directory;

    private AttachmentStore store() throws IOException {
        return new AttachmentStore(directory.toString());
    }

    private MockMultipartFile file(String name, String type, int bytes) {
        return new MockMultipartFile("file", name, type, new byte[bytes]);
    }

    @Test
    void keepsAVoiceNoteAndHandsItBack() throws IOException {
        AttachmentStore store = store();

        Attachment saved = store.save("ROOM01", "VOICE", "member-1", 4200, file("clip.webm", "audio/webm", 1024));

        assertThat(saved.kind()).isEqualTo(AttachmentKind.VOICE);
        assertThat(saved.durationMs()).isEqualTo(4200);
        assertThat(store.find("ROOM01", saved.id())).contains(saved);
        assertThat(store.open("ROOM01", saved.id())).isPresent();
    }

    @Test
    void believesTheBrowserOnlyWhenTheContentTypeAgrees() throws IOException {
        AttachmentStore store = store();

        // A PDF announced as a voice note is a file, whatever the browser called it.
        assertThat(store.save("ROOM01", "VOICE", "m", 0, file("notes.pdf", "application/pdf", 10)).kind())
                .isEqualTo(AttachmentKind.FILE);
        assertThat(store.save("ROOM01", "VIDEO_NOTE", "m", 0, file("clip.webm", "video/webm", 10)).kind())
                .isEqualTo(AttachmentKind.VIDEO_NOTE);
        assertThat(store.save("ROOM01", null, "m", 0, file("cat.png", "image/png", 10)).kind())
                .isEqualTo(AttachmentKind.IMAGE);
    }

    @Test
    void refusesAFileBiggerThanTheLimit() throws IOException {
        AttachmentStore store = store();

        assertThatThrownBy(() -> store.save("ROOM01", "FILE", "m", 0,
                file("huge.bin", "application/octet-stream", (int) AttachmentStore.MAX_FILE_BYTES + 1)))
                .isInstanceOf(AttachmentStore.RejectedException.class)
                .hasMessageContaining("25 MB");
        assertThatThrownBy(() -> store.save("ROOM01", "FILE", "m", 0,
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .isInstanceOf(AttachmentStore.RejectedException.class);
    }

    @Test
    void aNameCannotReachOutOfTheRoomsOwnFolder() throws IOException {
        AttachmentStore store = store();

        Attachment saved = store.save("ROOM01", "FILE", "m", 0,
                file("../../../etc/passwd", "application/octet-stream", 8));

        assertThat(saved.name()).isEqualTo("passwd");
        // Stored under a generated id, inside the room's own directory.
        assertThat(store.open("ROOM01", saved.id())).get().asString().contains("ROOM01");
        assertThat(Files.list(directory.resolve("ROOM01")).count()).isEqualTo(1);
    }

    @Test
    void aStrangeContentTypeIsNotEchoedBack() throws IOException {
        AttachmentStore store = store();

        Attachment saved = store.save("ROOM01", "FILE", "m", 0,
                file("odd", "text/html\r\nSet-Cookie: x=1", 8));

        assertThat(saved.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void oneRoomsFilesAreNotAnotherRooms() throws IOException {
        AttachmentStore store = store();
        Attachment saved = store.save("ROOM01", "FILE", "m", 0, file("a.txt", "text/plain", 8));

        assertThat(store.find("ROOM02", saved.id())).isEmpty();
        assertThat(store.open("ROOM02", saved.id())).isEmpty();
    }

    @Test
    void everythingGoesWhenTheRoomDoes() throws IOException {
        AttachmentStore store = store();
        Attachment saved = store.save("ROOM01", "VOICE", "m", 0, file("clip.webm", "audio/webm", 64));

        store.removeRoom("ROOM01");

        assertThat(store.find("ROOM01", saved.id())).isEmpty();
        assertThat(Files.exists(directory.resolve("ROOM01"))).isFalse();
    }

    @Test
    void aRoomCanOnlyHoldSoMuch() throws IOException {
        AttachmentStore store = store();
        for (int i = 0; i < AttachmentStore.MAX_ROOM_FILES; i++) {
            store.save("ROOM01", "FILE", "m", 0, file("f" + i, "application/octet-stream", 4));
        }

        assertThatThrownBy(() -> store.save("ROOM01", "FILE", "m", 0, file("one-more", "application/octet-stream", 4)))
                .isInstanceOf(AttachmentStore.RejectedException.class);
    }
}
