package com.soundstream.room;

/**
 * Someone currently in a room.
 *
 * <p>{@code id} is the browser's own stable id rather than the socket, so one person stays one
 * member across reconnects and tabs. {@code listening} is the part that matters in a listening
 * room: being present is not the same as having audio actually playing and in step with the host,
 * which is what this reports.
 */
public record Member(
        String id,
        String name,
        String avatarId,
        boolean host,
        boolean listening,
        long joinedAt) {

    Member withListening(boolean nowListening) {
        return new Member(id, name, avatarId, host, nowListening, joinedAt);
    }
}
