package com.soundstream.config;

import com.soundstream.room.Room;
import com.soundstream.room.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RoomAccessGuardTest {

    private final RoomService rooms = new RoomService();
    private final RoomAccessGuard guard = new RoomAccessGuard(rooms);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectingWithoutAKeyIsFine() {
        // Session attributes reject null values, so storing a missing key would break every
        // keyless connection — which is every visitor to a public room.
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        assertThatNoException().isThrownBy(() -> guard.preSend(connect(null, attributes), channel));
        assertThat(attributes).isEmpty();
    }

    @Test
    void connectingWithAKeyRemembersItForTheSession() {
        Map<String, Object> attributes = new ConcurrentHashMap<>();

        guard.preSend(connect("the-key", attributes), channel);

        assertThat(attributes).containsEntry("roomKey", "the-key");
    }

    @Test
    void aPublicRoomIsOpenToAConnectionWithNoKey() {
        Room room = rooms.create("Open", "Host", "bun", null);

        assertThatNoException().isThrownBy(
                () -> guard.preSend(subscribe(room.getId(), new ConcurrentHashMap<>()), channel));
    }

    @Test
    void aPrivateRoomRefusesASubscriptionWithoutTheKey() {
        Room room = rooms.create("Secret", "Host", "bun", "hunter2");

        assertThatThrownBy(() -> guard.preSend(subscribe(room.getId(), new ConcurrentHashMap<>()), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void aPrivateRoomRefusesTheWrongKeyAndAcceptsTheRightOne() {
        Room room = rooms.create("Secret", "Host", "bun", "hunter2");
        Map<String, Object> wrong = new ConcurrentHashMap<>(Map.of("roomKey", "not-the-key"));
        Map<String, Object> right = new ConcurrentHashMap<>(Map.of("roomKey", room.getAccessKey()));

        assertThatThrownBy(() -> guard.preSend(subscribe(room.getId(), wrong), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatNoException().isThrownBy(() -> guard.preSend(subscribe(room.getId(), right), channel));
    }

    @Test
    void sendingIntoAPrivateRoomNeedsTheKeyToo() {
        Room room = rooms.create("Secret", "Host", "bun", "hunter2");

        assertThatThrownBy(() -> guard.preSend(
                frame(StompCommand.SEND, "/app/rooms/" + room.getId() + "/chat", new ConcurrentHashMap<>()), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void anUnknownRoomIsLeftToTheHandlers() {
        assertThatNoException().isThrownBy(
                () -> guard.preSend(subscribe("NOPE00", new ConcurrentHashMap<>()), channel));
    }

    private Message<byte[]> connect(String key, Map<String, Object> attributes) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (key != null) {
            headers.addNativeHeader("roomKey", key);
        }
        headers.setSessionAttributes(attributes);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String roomId, Map<String, Object> attributes) {
        return frame(StompCommand.SUBSCRIBE, "/topic/rooms/" + roomId + "/playback", attributes);
    }

    private Message<byte[]> frame(StompCommand command, String destination, Map<String, Object> attributes) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        headers.setSessionAttributes(attributes);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
