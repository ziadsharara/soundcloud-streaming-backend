package com.soundstream.room;

import com.soundstream.room.RoomDtos.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomReactionTest {

    private static final String THUMB = "👍";
    private static final String HEART = "❤️";

    private final RoomService service = new RoomService();
    private final Room room = service.create("Room", "Host", "bun");

    @Test
    void tappingAnEmojiAddsItAndTappingItAgainTakesItBack() {
        ChatMessage message = say("m1");

        assertThat(service.react(room.getId(), message.id(), THUMB, "member-1"))
                .get()
                .extracting(update -> update.reactions().get(THUMB))
                .isEqualTo(java.util.List.of("member-1"));

        assertThat(service.react(room.getId(), message.id(), THUMB, "member-1"))
                .get()
                .extracting(update -> update.reactions().isEmpty())
                .isEqualTo(true);
    }

    @Test
    void severalPeopleCanChooseTheSameEmoji() {
        ChatMessage message = say("m1");

        service.react(room.getId(), message.id(), THUMB, "member-1");
        service.react(room.getId(), message.id(), THUMB, "member-2");

        assertThat(room.reactionsFor(message.id()).get(THUMB)).containsExactly("member-1", "member-2");
    }

    @Test
    void theSameEmojiWithAndWithoutItsVariationSelectorIsOneReaction() {
        ChatMessage message = say("m1");

        service.react(room.getId(), message.id(), HEART, "member-1");
        service.react(room.getId(), message.id(), "❤", "member-2");

        assertThat(room.reactionsFor(message.id())).containsOnlyKeys(HEART);
        assertThat(room.reactionsFor(message.id()).get(HEART)).containsExactly("member-1", "member-2");
    }

    @Test
    void onlyEmojiFromThePaletteAreAccepted() {
        ChatMessage message = say("m1");

        assertThat(service.react(room.getId(), message.id(), "<script>", "member-1")).isEmpty();
        assertThat(service.react(room.getId(), message.id(), "🦄", "member-1")).isEmpty();
        assertThat(room.reactionsFor(message.id())).isEmpty();
    }

    @Test
    void aMessageThatIsNotInTheRoomCannotBeReactedTo() {
        assertThat(service.react(room.getId(), "made-up", THUMB, "member-1")).isEmpty();
        assertThat(room.getReactions()).isEmpty();
    }

    @Test
    void reactionsLeaveWithTheMessageTheyBelongTo() {
        ChatMessage first = say("first");
        service.react(room.getId(), first.id(), THUMB, "member-1");

        // Push it out of the 50-message history.
        for (int i = 0; i < 50; i++) {
            say("filler-" + i);
        }

        assertThat(room.getReactions()).doesNotContainKey(first.id());
    }

    private ChatMessage say(String id) {
        ChatMessage message = new ChatMessage(id, "", "member-1", "Someone", "bun", "TEXT", "hi", "", null, false,
                System.currentTimeMillis());
        room.addChatMessage(message);
        return message;
    }
}
