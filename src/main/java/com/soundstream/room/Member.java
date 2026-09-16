package com.soundstream.room;

/**
 * Someone currently in a room. {@code id} is the STOMP session, so a member disappears
 * the moment their socket closes.
 */
public record Member(String id, String name, String avatarId, boolean host, long joinedAt) {
}
