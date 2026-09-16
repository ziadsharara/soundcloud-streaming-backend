package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;
import com.soundstream.room.RoomDtos.ChatRequest;
import com.soundstream.room.RoomDtos.JoinRequest;
import com.soundstream.room.RoomDtos.PlaybackUpdate;
import com.soundstream.room.RoomDtos.QueueUpdate;
import com.soundstream.room.RoomDtos.ReceiptRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Real-time room traffic. Payloads are validated here, then fanned out on the room's topics.
 */
@Controller
public class StreamController {

    private static final String KIND_STICKER = "STICKER";
    private static final String KIND_TEXT = "TEXT";
    /** A browser-generated id; anything else falls back to the socket so a member still appears. */
    private static final Pattern MEMBER_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

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
            String memberId = request.memberId() != null && MEMBER_ID.matcher(request.memberId()).matches()
                    ? request.memberId() : sessionId;
            presence.join(roomId, sessionId, memberId, name.isEmpty() ? "Guest" : name, avatar, host);
        });
    }

    /** "My player is actually playing and in step" — the difference between present and listening. */
    @MessageMapping("/rooms/{roomId}/status")
    public void status(@DestinationVariable String roomId, ListeningStatus status,
                       @Header("simpSessionId") String sessionId) {
        if (status != null) {
            presence.setListening(sessionId, status.listening());
        }
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
            String memberId = member.map(Member::id).orElse(sessionId);

            ChatMessage message = KIND_STICKER.equalsIgnoreCase(request.kind())
                    ? sticker(request, memberId, author, avatar, host)
                    : text(request, memberId, author, avatar, host);
            if (message == null) {
                return;
            }
            room.addChatMessage(message);
            messaging.convertAndSend(RoomTopics.chat(roomId), message);
        });
    }

    /**
     * "Someone is typing", relayed rather than stored: the server keeps no timer, and clients
     * expire an indicator on their own, so a dropped "stopped typing" cannot leave one stuck.
     */
    @MessageMapping("/rooms/{roomId}/typing")
    public void typing(@DestinationVariable String roomId, TypingStatus status,
                       @Header("simpSessionId") String sessionId) {
        if (status == null) {
            return;
        }
        // The name comes from the session that joined, never from the payload.
        presence.member(sessionId).ifPresent(member -> messaging.convertAndSend(
                RoomTopics.typing(roomId),
                new TypingNotice(member.id(), member.name(), status.typing())));
    }

    /** Delivery and read acknowledgements, which become the ticks beside a sent message. */
    @MessageMapping("/rooms/{roomId}/receipt")
    public void receipt(@DestinationVariable String roomId, ReceiptRequest request,
                        @Header("simpSessionId") String sessionId) {
        if (request == null || request.throughServerTime() <= 0) {
            return;
        }
        Optional<Member> member = presence.member(sessionId);
        rooms.find(roomId).ifPresent(room -> member.ifPresent(present -> {
            room.markReceipt(present.id(), request.read(), request.throughServerTime());
            messaging.convertAndSend(RoomTopics.receipts(roomId), room.getReceipts());
        }));
    }

    private ChatMessage text(ChatRequest request, String memberId, String author, String avatar, boolean host) {
        String body = RoomService.clip(request.text(), 500);
        return body.isEmpty() ? null
                : new ChatMessage(UUID.randomUUID().toString(), memberId, author, avatar,
                        KIND_TEXT, body, "", host, System.currentTimeMillis());
    }

    private ChatMessage sticker(ChatRequest request, String memberId, String author, String avatar, boolean host) {
        return RoomService.sticker(request.stickerId())
                .map(id -> new ChatMessage(UUID.randomUUID().toString(), memberId, author, avatar,
                        KIND_STICKER, "", id, host, System.currentTimeMillis()))
                .orElse(null);
    }

    /** Bodies of the little status messages; tiny enough to live beside their handlers. */
    public record ListeningStatus(boolean listening) {
    }

    public record TypingStatus(boolean typing) {
    }

    public record TypingNotice(String memberId, String name, boolean typing) {
    }
}
