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

    public record CreateRoomRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 40) String hostName,
            @Size(max = 24) String hostAvatarId,
            @Size(min = 4, max = 100) String password) {
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
            String hostName,
            String hostAvatarId,
            int listeners,
            List<Member> members,
            PlaybackState playback,
            QueueState queue,
            List<ChatMessage> chat,
            Map<String, MemberReceipt> receipts,
            boolean privateRoom,
            boolean locked,
            long createdAt,
            long serverNow) {

        static RoomSummary of(Room room, List<Member> members) {
            return new RoomSummary(room.getId(), room.getName(), room.getHostName(), room.getHostAvatarId(),
                    members.size(), members, room.getPlayback(), room.getQueue(), room.getChatHistory(),
                    room.getReceipts(), room.isPrivate(), false, room.getCreatedAt(), System.currentTimeMillis());
        }

        /**
         * What a private room shows before the password: enough to render the door — its name and
         * who is hosting — and nothing that is going on inside it.
         */
        static RoomSummary locked(Room room) {
            return new RoomSummary(room.getId(), room.getName(), room.getHostName(), room.getHostAvatarId(),
                    0, List.of(), null, null, List.of(), Map.of(), true, true,
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

    /** Sent by the host whenever the upcoming room queue changes. */
    public record QueueUpdate(String hostToken, List<String> trackUrls, int activeIndex) {
    }

    /** A chat message is either typed text or one of the drawn stickers. */
    public record ChatRequest(String hostToken, String kind, String text, String stickerId) {
    }

    public record ChatMessage(
            String id,
            String memberId,
            String author,
            String avatarId,
            String kind,
            String text,
            String stickerId,
            boolean host,
            long serverTime) {
    }

    /** "I have received (or read) every message up to this moment." */
    public record ReceiptRequest(boolean read, long throughServerTime) {
    }

    /** How far one member has got: the two timestamps the ticks are computed from. */
    public record MemberReceipt(long deliveredAt, long readAt) {
    }
}
