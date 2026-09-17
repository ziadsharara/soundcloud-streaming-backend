package com.soundstream.room;

import java.util.Locale;

/**
 * What a chat attachment is, which decides how the room draws it.
 *
 * The browser says what it meant to send — a voice note is not the same thing as an audio file
 * someone picked from their disk, even though both are audio — and the server checks that claim
 * against the actual content type before believing it.
 */
public enum AttachmentKind {
    VOICE("audio/"),
    VIDEO_NOTE("video/"),
    IMAGE("image/"),
    VIDEO("video/"),
    AUDIO("audio/"),
    FILE("");

    private final String requiredPrefix;

    AttachmentKind(String requiredPrefix) {
        this.requiredPrefix = requiredPrefix;
    }

    /**
     * The kind to store this upload as: what the browser asked for when the content type agrees,
     * and otherwise whatever the content type honestly is.
     */
    public static AttachmentKind of(String requested, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        AttachmentKind asked = parse(requested);
        if (asked != null && type.startsWith(asked.requiredPrefix)) {
            return asked;
        }
        if (type.startsWith("image/")) {
            return IMAGE;
        }
        if (type.startsWith("video/")) {
            return VIDEO;
        }
        if (type.startsWith("audio/")) {
            return AUDIO;
        }
        return FILE;
    }

    private static AttachmentKind parse(String value) {
        if (value == null) {
            return null;
        }
        for (AttachmentKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.strip())) {
                return kind;
            }
        }
        return null;
    }

    public boolean isMedia() {
        return this != FILE;
    }
}
