package com.soundstream.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Wire formats shared by the REST API and the STOMP endpoints.
 */
public final class RoomDtos {

    private RoomDtos() {
    }

    /** {@code kind} is "CHAT" for a chat-only room; anything else makes the usual music room. */
    public record CreateRoomRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 40) String hostName,
            @Size(max = 24) String hostAvatarId,
            @Size(min = 4, max = 100) String password,
            @Size(max = 16) String kind) {
    }

    /** The host gets both secrets at once: one to control the room, one to let people in. */
    public record CreateRoomResponse(RoomSummary room, String hostToken, String accessKey) {
    }

    public record UnlockRequest(String password) {
    }

    public record UnlockResponse(String accessKey) {
    }

    /** Public view of a room — never includes the host token. */
    public record RoomSummary(
            String id,
            String name,
            RoomKind kind,
            String hostName,
            String hostAvatarId,
            int listeners,
            List<Member> members,
            PlaybackState playback,
            QueueState queue,
            List<ChatMessage> chat,
            Map<String, MemberReceipt> receipts,
            Map<String, Map<String, List<String>>> reactions,
            boolean privateRoom,
            boolean locked,
            long createdAt,
            long serverNow) {

        static RoomSummary of(Room room, List<Member> members) {
            return new RoomSummary(room.getId(), room.getName(), room.getKind(), room.getHostName(),
                    room.getHostAvatarId(), members.size(), members, room.getPlayback(), room.getQueue(),
                    room.getChatHistory(), room.getReceipts(), room.getReactions(), room.isPrivate(), false,
                    room.getCreatedAt(), System.currentTimeMillis());
        }

        /**
         * What a private room shows before the password: enough to render the door — its name and
         * who is hosting — and nothing that is going on inside it.
         */
        static RoomSummary locked(Room room) {
            return new RoomSummary(room.getId(), room.getName(), room.getKind(), room.getHostName(),
                    room.getHostAvatarId(), 0, List.of(), null, null, List.of(), Map.of(), Map.of(), true, true,
                    room.getCreatedAt(), System.currentTimeMillis());
        }
    }

    /**
     * Sent once per connection, after subscribing, to put a name and face in the room.
     * {@code memberId} is the browser's own stable id, so a person keeps one identity across
     * reconnects and tabs — which is what makes read receipts add up to a person rather than a socket.
     */
    public record JoinRequest(String hostToken, String memberId, String name, String avatarId) {
    }

    /** Sent by the host's browser whenever its player changes state. */
    public record PlaybackUpdate(
            String hostToken,
            String trackUrl,
            String title,
            String artist,
            String artworkUrl,
            boolean playing,
            long positionMs) {
    }

    /** Sent by the host whenever the upcoming room queue changes: reorder, removal, what is playing. */
    public record QueueUpdate(String hostToken, List<String> trackUrls, int activeIndex) {
    }

    /** "Put this on too" — anyone in the room may add to the end of the queue. */
    public record QueueAddRequest(String trackUrl) {
    }

    /** Tapping an emoji under a message; tapping the same one again takes it back. */
    public record ReactionRequest(String messageId, String emoji) {
    }

    /** One message's reactions after a change: emoji to the members who chose it. */
    public record ReactionUpdate(String messageId, Map<String, List<String>> reactions) {
    }

    /**
     * A chat message is typed text, one of the drawn stickers, or something somebody sent.
     *
     * {@code clientId} is the sender's own id for this message. It is echoed back untouched so a
     * browser can show what you sent the instant you send it and then recognise its own message
     * when it comes back, rather than leaving you watching a round trip.
     */
    public record ChatRequest(String hostToken, String kind, String text, String stickerId,
                              String attachmentId, String clientId) {
    }

    public record ChatMessage(
            String id,
            String clientId,
            String memberId,
            String author,
            String avatarId,
            String kind,
            String text,
            String stickerId,
            Attachment attachment,
            boolean host,
            long serverTime) {

        static ChatMessage text(String id, String memberId, String author, String avatarId, String body,
                                boolean host, long serverTime) {
            return new ChatMessage(id, "", memberId, author, avatarId, "TEXT", body, "", null, host, serverTime);
        }
    }

    /** What the browser gets back after sending a file, before it posts the message itself. */
    public record AttachmentResponse(Attachment attachment) {
    }

    /** "I have received (or read) every message up to this moment." */
    public record ReceiptRequest(boolean read, long throughServerTime) {
    }

    /** How far one member has got: the two timestamps the ticks are computed from. */
    public record MemberReceipt(long deliveredAt, long readAt) {
    }
}
