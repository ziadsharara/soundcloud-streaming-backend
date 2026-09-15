package com.soundstream.room;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts who is in each room by tracking subscriptions to the room's playback topic.
 */
@Component
public class PresenceTracker {

    private static final Pattern PLAYBACK_TOPIC = Pattern.compile("^/topic/rooms/([^/]+)/playback$");
    /** Subscribe events fire before the broker registers the subscription, so delay the broadcast slightly. */
    private static final long BROADCAST_DELAY_MS = 300;

    private final RoomService rooms;
    private final SimpMessagingTemplate messaging;
    /** "sessionId:subscriptionId" -> subscription. A session counts once per room. */
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public PresenceTracker(RoomService rooms, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.messaging = messaging;
    }

    public int count(String roomId) {
        return (int) subscriptions.values().stream()
                .filter(subscription -> roomId.equals(subscription.roomId()))
                .map(Subscription::sessionId)
                .distinct()
                .count();
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headers.getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = PLAYBACK_TOPIC.matcher(destination);
        if (!matcher.matches() || rooms.find(matcher.group(1)).isEmpty()) {
            return;
        }
        String roomId = matcher.group(1);
        subscriptions.put(key(headers.getSessionId(), headers.getSubscriptionId()),
                new Subscription(roomId, headers.getSessionId()));
        broadcastCount(roomId);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Subscription subscription = subscriptions.remove(key(headers.getSessionId(), headers.getSubscriptionId()));
        if (subscription != null) {
            broadcastCount(subscription.roomId());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String prefix = event.getSessionId() + ":";
        Set<String> affectedRooms = new HashSet<>();
        subscriptions.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                affectedRooms.add(entry.getValue().roomId());
                return true;
            }
            return false;
        });
        affectedRooms.forEach(this::broadcastCount);
    }

    private void broadcastCount(String roomId) {
        CompletableFuture.runAsync(
                () -> messaging.convertAndSend(RoomTopics.listeners(roomId), count(roomId)),
                CompletableFuture.delayedExecutor(BROADCAST_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private static String key(String sessionId, String subscriptionId) {
        return sessionId + ":" + subscriptionId;
    }

    private record Subscription(String roomId, String sessionId) {
    }
}
