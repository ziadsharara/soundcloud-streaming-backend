package com.soundstream.room;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Who is in each room, and which of them actually have the music playing.
 *
 * A browser announces itself on /app/rooms/{id}/join once its subscriptions are in place, reports
 * whether its player is running on /app/rooms/{id}/status, and is dropped when the socket closes.
 */
@Component
public class PresenceTracker {

    /**
     * A joining client subscribes and then sends its join in the same breath. The broadcast is
     * delayed a moment so the new member's own subscription is registered before the list goes out.
     */
    private static final long BROADCAST_DELAY_MS = 250;

    private final RoomService rooms;
    private final SimpMessagingTemplate messaging;
    /** STOMP session id -> where that session is sitting. */
    private final Map<String, Presence> sessions = new ConcurrentHashMap<>();

    public PresenceTracker(RoomService rooms, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.messaging = messaging;
    }

    /** Places a session in a room, replacing any earlier identity it announced. */
    public void join(String roomId, String sessionId, String memberId, String name, String avatarId, boolean host) {
        Optional<Room> room = rooms.find(roomId);
        if (room.isEmpty() || sessionId == null) {
            return;
        }
        Member member = new Member(memberId, name, avatarId, host, false, System.currentTimeMillis());
        sessions.put(sessionId, new Presence(roomId, member));
        room.get().markOccupied();
        broadcastMembers(roomId);
    }

    /** Records whether this session's player is actually playing, in step with the host. */
    public void setListening(String sessionId, boolean listening) {
        Presence presence = sessions.get(sessionId);
        if (presence == null || presence.member().listening() == listening) {
            return;
        }
        sessions.put(sessionId, new Presence(presence.roomId(), presence.member().withListening(listening)));
        broadcastMembers(presence.roomId());
    }

    /**
     * One entry per person, not per socket: someone with the room open in two tabs is one member,
     * and counts as listening if any of their tabs is playing.
     */
    public List<Member> members(String roomId) {
        Map<String, Member> byPerson = new LinkedHashMap<>();
        sessions.values().stream()
                .filter(presence -> presence.roomId().equals(roomId))
                .map(Presence::member)
                .sorted(Comparator.comparing(Member::host).reversed().thenComparingLong(Member::joinedAt))
                .forEach(member -> byPerson.merge(member.id(), member,
                        (kept, other) -> kept.listening() || other.listening() ? kept.withListening(true) : kept));
        return List.copyOf(new ArrayList<>(byPerson.values()));
    }

    public int count(String roomId) {
        return members(roomId).size();
    }

    /** The member behind a STOMP session, used to attribute chat without trusting the payload. */
    public Optional<Member> member(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId)).map(Presence::member);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Presence presence = sessions.remove(event.getSessionId());
        if (presence == null) {
            return;
        }
        // Start the empty-room clock from the moment the last member leaves.
        rooms.find(presence.roomId()).ifPresent(Room::markOccupied);
        broadcastMembers(presence.roomId());
    }

    private void broadcastMembers(String roomId) {
        CompletableFuture.runAsync(
                () -> messaging.convertAndSend(RoomTopics.members(roomId), members(roomId)),
                CompletableFuture.delayedExecutor(BROADCAST_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private record Presence(String roomId, Member member) {
    }
}
