package com.soundstream.room;

import java.util.List;
import java.util.Optional;

/**
 * The emoji a message can be reacted with.
 *
 * A fixed palette rather than "any text the client sends": reactions are echoed to everyone in the
 * room, so the set of things that can appear there is decided here, not by a browser.
 */
public final class Reactions {

    /** Kept in step with the palette the chat panel draws. */
    public static final List<String> PALETTE = List.of("👍", "❤️", "😂",
            "😮", "😢", "🙏", "🔥", "🎵");

    private Reactions() {
    }

    /**
     * Matches what the client sent against the palette, ignoring the emoji variation selector:
     * the same emoji arrives with or without it depending on the keyboard, and they mean the same
     * thing. The stored form is always the palette's, so everyone sees one reaction, not two.
     */
    public static Optional<String> canonical(String emoji) {
        if (emoji == null) {
            return Optional.empty();
        }
        String bare = strip(emoji);
        return PALETTE.stream().filter(candidate -> strip(candidate).equals(bare)).findFirst();
    }

    private static String strip(String emoji) {
        return emoji.strip().replace("️", "");
    }
}
