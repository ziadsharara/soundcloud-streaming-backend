package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;
import com.soundstream.room.RoomDtos.ChatRequest;
import com.soundstream.room.RoomDtos.JoinRequest;
import com.soundstream.room.RoomDtos.PlaybackUpdate;
import com.soundstream.room.RoomDtos.QueueUpdate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Optional;
import java.util.UUID;

/**
 * Real-time room traffic. Payloads are validated here, then fanned out on the room's topics.
 */
@Controller
public class StreamController {

    private static final String KIND_STICKER = "STICKER";
    private static final String KIND_TEXT = "TEXT";

    private final RoomService rooms;
    private final PresenceTracker presence;
    private final SimpMessagingTemplate messaging;

    public StreamController(RoomService rooms, PresenceTracker presence, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.presence = presence;
        this.messaging = messaging;
    }

    @MessageMapping("/rooms/{roomId}/join")
    public void join(@DestinationVariable String roomId, JoinRequest request,
                     @Header("simpSessionId") String sessionId) {
        if (request == null) {
            return;
        }
        rooms.find(roomId).ifPresent(room -> {
            boolean host = room.isHost(request.hostToken());
            String name = host ? room.getHostName() : RoomService.clip(request.name(), 40);
            String avatar = host ? room.getHostAvatarId() : RoomService.avatarOrDefault(request.avatarId());
            presence.join(roomId, sessionId, name.isEmpty() ? "Guest" : name, avatar, host);
        });
    }

    @MessageMapping("/rooms/{roomId}/playback")
    public void playback(@DestinationVariable String roomId, PlaybackUpdate update) {
        rooms.updatePlayback(roomId, update)
                .ifPresent(state -> messaging.convertAndSend(RoomTopics.playback(roomId), state));
    }

    @MessageMapping("/rooms/{roomId}/queue")
    public void queue(@DestinationVariable String roomId, QueueUpdate update) {
        rooms.updateQueue(roomId, update)
                .ifPresent(state -> messaging.convertAndSend(RoomTopics.queue(roomId), state));
    }

    @MessageMapping("/rooms/{roomId}/chat")
    public void chat(@DestinationVariable String roomId, ChatRequest request,
                     @Header("simpSessionId") String sessionId) {
        if (request == null) {
            return;
        }
        rooms.find(roomId).ifPresent(room -> {
            // Identity comes from the session that joined, never from the message body.
            Optional<Member> member = presence.member(sessionId);
            boolean host = member.map(Member::host).orElseGet(() -> room.isHost(request.hostToken()));
            String author = member.map(Member::name).orElse(host ? room.getHostName() : "Guest");
            String avatar = member.map(Member::avatarId)
                    .orElse(host ? room.getHostAvatarId() : RoomService.DEFAULT_AVATAR);

            ChatMessage message = KIND_STICKER.equalsIgnoreCase(request.kind())
                    ? sticker(request, sessionId, author, avatar, host)
                    : text(request, sessionId, author, avatar, host);
            if (message == null) {
                return;
            }
            room.addChatMessage(message);
            messaging.convertAndSend(RoomTopics.chat(roomId), message);
        });
    }

    private ChatMessage text(ChatRequest request, String sessionId, String author, String avatar, boolean host) {
        String body = RoomService.clip(request.text(), 500);
        return body.isEmpty() ? null
                : new ChatMessage(UUID.randomUUID().toString(), sessionId, author, avatar,
                        KIND_TEXT, body, "", host, System.currentTimeMillis());
    }

    private ChatMessage sticker(ChatRequest request, String sessionId, String author, String avatar, boolean host) {
        return RoomService.sticker(request.stickerId())
                .map(id -> new ChatMessage(UUID.randomUUID().toString(), sessionId, author, avatar,
                        KIND_STICKER, "", id, host, System.currentTimeMillis()))
                .orElse(null);
    }
}
