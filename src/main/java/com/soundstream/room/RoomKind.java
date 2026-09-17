package com.soundstream.room;

/**
 * What a room is for.
 *
 * A music room is the original thing: one player, everyone in sync, with chat beside it.
 * A chat room is the same room with the music taken out — the same people, faces, stickers,
 * reactions and receipts, and no player at all.
 */
public enum RoomKind {
    MUSIC,
    CHAT;

    /** Anything unrecognised is a music room, which is what a plain create request means. */
    public static RoomKind parse(String value) {
        if (value == null) {
            return MUSIC;
        }
        return CHAT.name().equalsIgnoreCase(value.strip()) ? CHAT : MUSIC;
    }

    public boolean playsMusic() {
        return this == MUSIC;
    }
}
