package com.soundstream.config;

import com.soundstream.room.RoomService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps private rooms private on the socket as well as the API.
 *
 * Hiding a room from the listing is not privacy: topics are guessable from a room code, so a
 * subscription to a private room's topics is refused unless the connection presented the key it
 * got by answering the password.
 */
final class RoomAccessGuard implements ChannelInterceptor {

    private static final Pattern ROOM_DESTINATION = Pattern.compile("^/(?:topic|app)/rooms/([^/]+)(?:/.*)?$");
    private static final String KEY_HEADER = "roomKey";

    private final RoomService rooms;

    RoomAccessGuard(RoomService rooms) {
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        // The key arrives once, on connect, and is remembered for the life of the session.
        // Only ever store a value: the attributes map rejects nulls, and a visitor to a public
        // room sends no key at all — storing one would break every keyless connection.
        if (command == StompCommand.CONNECT) {
            Map<String, Object> attributes = accessor.getSessionAttributes();
            String presented = accessor.getFirstNativeHeader(KEY_HEADER);
            if (attributes != null && presented != null) {
                attributes.put(KEY_HEADER, presented);
            }
            return message;
        }

        if (command != StompCommand.SUBSCRIBE && command != StompCommand.SEND) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        Matcher matcher = ROOM_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object key = attributes == null ? null : attributes.get(KEY_HEADER);
        boolean allowed = rooms.find(matcher.group(1))
                .map(room -> room.allows(key instanceof String presented ? presented : null))
                // An unknown room is not this guard's problem; the handlers ignore it anyway.
                .orElse(true);
        if (!allowed) {
            throw new MessageDeliveryException(message, "This room is private");
        }
        return message;
    }
}
