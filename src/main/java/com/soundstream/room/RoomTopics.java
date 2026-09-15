package com.soundstream.room;

public final class RoomTopics {

    private RoomTopics() {
    }

    public static String playback(String roomId) {
        return "/topic/rooms/" + roomId + "/playback";
    }

    public static String listeners(String roomId) {
        return "/topic/rooms/" + roomId + "/listeners";
    }

    public static String chat(String roomId) {
        return "/topic/rooms/" + roomId + "/chat";
    }

    public static String closed(String roomId) {
        return "/topic/rooms/" + roomId + "/closed";
    }
}
