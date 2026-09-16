package com.soundstream.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Wire formats shared by the REST API and the STOMP endpoints.
 */
public final class RoomDtos {

    private RoomDtos() {
    }

    public record CreateRoomRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 40) String hostName,
            @Size(max = 24) String hostAvatarId) {
    }

    public record CreateRoomResponse(RoomSummary room, String hostToken) {
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
            long createdAt,
            long serverNow) {

        static RoomSummary of(Room room, List<Member> members) {
            return new RoomSummary(room.getId(), room.getName(), room.getHostName(), room.getHostAvatarId(),
                    members.size(), members, room.getPlayback(), room.getQueue(), room.getChatHistory(),
                    room.getCreatedAt(), System.currentTimeMillis());
        }
    }

    /** Sent once per connection, after subscribing, to put a name and face in the room. */
    public record JoinRequest(String hostToken, String name, String avatarId) {
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
}
