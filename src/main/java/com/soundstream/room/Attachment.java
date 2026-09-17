package com.soundstream.room;

/**
 * What the room knows about one thing somebody sent: everything except the bytes, which live on
 * disk beside the room and are read back through {@link AttachmentStore}.
 */
public record Attachment(
        String id,
        String name,
        String contentType,
        long size,
        AttachmentKind kind,
        long durationMs,
        String memberId,
        long createdAt) {
}
