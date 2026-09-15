package com.soundstream.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BrokerDestinationGuardTest {

    private final BrokerDestinationGuard guard = new BrokerDestinationGuard();

    @Test
    void rejectsClientMessagesSentDirectlyToBrokerTopics() {
        var message = messageTo("/topic/rooms/ABC123/playback");

        assertThatThrownBy(() -> guard.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void allowsClientMessagesThroughValidatedApplicationDestinations() {
        var message = messageTo("/app/rooms/ABC123/playback");

        assertThatNoException().isThrownBy(() -> guard.preSend(message, mock(MessageChannel.class)));
    }

    private static org.springframework.messaging.Message<byte[]> messageTo(String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
