package com.soundstream.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;

/** Prevents browser clients from bypassing /app handlers and publishing straight to broker topics. */
final class BrokerDestinationGuard implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (type == SimpMessageType.MESSAGE
                && destination != null
                && (destination.equals("/topic") || destination.startsWith("/topic/"))) {
            throw new MessageDeliveryException(message, "Clients cannot publish directly to broker topics");
        }
        return message;
    }
}
