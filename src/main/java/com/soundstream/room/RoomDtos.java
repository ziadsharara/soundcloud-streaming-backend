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
            @NotBlank @Size(max = 40) String hostName) {
    }

    public record CreateRoomResponse(RoomSummary room, String hostToken) {
    }

    /** Public view of a room — never includes the host token. */
    public record RoomSummary(
            String id,
            String name,
            String hostName,
            int listeners,
            PlaybackState playback,
            QueueState queue,
            List<ChatMessage> chat,
            long createdAt,
            long serverNow) {

        static RoomSummary of(Room room, int listeners) {
            return new RoomSummary(room.getId(), room.getName(), room.getHostName(), listeners,
                    room.getPlayback(), room.getQueue(), room.getChatHistory(), room.getCreatedAt(),
                    System.currentTimeMillis());
        }
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

    public record ChatRequest(String hostToken, String author, String text) {
    }

    public record ChatMessage(String id, String author, String text, boolean host, long serverTime) {
    }
}
