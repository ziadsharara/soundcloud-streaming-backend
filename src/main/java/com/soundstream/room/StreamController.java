package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;
import com.soundstream.room.RoomDtos.ChatRequest;
import com.soundstream.room.RoomDtos.PlaybackUpdate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Real-time room traffic. Payloads are validated here, then fanned out on the room's topics.
 */
@Controller
public class StreamController {

    private final RoomService rooms;
    private final SimpMessagingTemplate messaging;

    public StreamController(RoomService rooms, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.messaging = messaging;
    }

    @MessageMapping("/rooms/{roomId}/playback")
    public void playback(@DestinationVariable String roomId, PlaybackUpdate update) {
        rooms.updatePlayback(roomId, update)
                .ifPresent(state -> messaging.convertAndSend(RoomTopics.playback(roomId), state));
    }

    @MessageMapping("/rooms/{roomId}/chat")
    public void chat(@DestinationVariable String roomId, ChatRequest request) {
        if (request == null) {
            return;
        }
        String text = RoomService.clip(request.text(), 500);
        if (text.isEmpty()) {
            return;
        }
        rooms.find(roomId).ifPresent(room -> {
            boolean host = room.isHost(request.hostToken());
            String author = host ? room.getHostName() : RoomService.clip(request.author(), 40);
            ChatMessage message = new ChatMessage(author.isEmpty() ? "anon" : author, text, host,
                    System.currentTimeMillis());
            messaging.convertAndSend(RoomTopics.chat(roomId), message);
        });
    }
}
