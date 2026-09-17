package com.soundstream.room;

public final class RoomTopics {

    private RoomTopics() {
    }

    public static String playback(String roomId) {
        return "/topic/rooms/" + roomId + "/playback";
    }

    public static String members(String roomId) {
        return "/topic/rooms/" + roomId + "/members";
    }

    public static String queue(String roomId) {
        return "/topic/rooms/" + roomId + "/queue";
    }

    public static String chat(String roomId) {
        return "/topic/rooms/" + roomId + "/chat";
    }

    public static String typing(String roomId) {
        return "/topic/rooms/" + roomId + "/typing";
    }

    public static String reactions(String roomId) {
        return "/topic/rooms/" + roomId + "/reactions";
    }

    public static String receipts(String roomId) {
        return "/topic/rooms/" + roomId + "/receipts";
    }

    public static String closed(String roomId) {
        return "/topic/rooms/" + roomId + "/closed";
    }
}
