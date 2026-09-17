package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;
import com.soundstream.room.RoomDtos.ChatRequest;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The sender's own id for a message is echoed back untouched, so their browser can show what they
 * sent immediately and then recognise it when the room confirms it.
 */
class ChatClientIdTest {

    private final RoomService rooms = new RoomService();
    private final StreamController controller = new StreamController(
            rooms, mock(PresenceTracker.class), mock(SimpMessagingTemplate.class), mock(AttachmentStore.class));

    @Test
    void anOrdinaryIdComesBackExactly() {
        assertThat(textMessage("abc123-XY_z").clientId()).isEqualTo("abc123-XY_z");
    }

    @Test
    void anythingOtherThanAPlainIdIsDropped() {
        // Echoed into every browser in the room, so only the shape we promised is echoed.
        assertThat(textMessage("<script>alert(1)</script>").clientId()).isEmpty();
        assertThat(textMessage("has spaces").clientId()).isEmpty();
        assertThat(textMessage("x".repeat(65)).clientId()).isEmpty();
        assertThat(textMessage(null).clientId()).isEmpty();
        assertThat(textMessage("").clientId()).isEmpty();
    }

    private ChatMessage textMessage(String clientId) {
        try {
            Method text = StreamController.class.getDeclaredMethod(
                    "text", ChatRequest.class, String.class, String.class, String.class, boolean.class);
            text.setAccessible(true);
            ChatRequest request = new ChatRequest(null, "TEXT", "hello", null, null, clientId);
            return (ChatMessage) text.invoke(controller, request, "member-1", "Someone", "bun", false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
