package com.soundstream.room;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoomReceiptTest {

    private final RoomService rooms = new RoomService();
    private final Room room = rooms.create("Room", "Host", "bun");

    @Test
    void recordsDeliveryAndReadingSeparately() {
        room.markReceipt("member-0001", false, 1_000);
        room.markReceipt("member-0001", true, 900);

        var receipt = room.getReceipts().get("member-0001");
        assertThat(receipt.deliveredAt()).isEqualTo(1_000);
        assertThat(receipt.readAt()).isEqualTo(900);
    }

    @Test
    void onlyEverMovesForward() {
        room.markReceipt("member-0001", true, 5_000);
        // A late or out-of-order acknowledgement must not un-read earlier messages.
        room.markReceipt("member-0001", true, 2_000);

        assertThat(room.getReceipts().get("member-0001").readAt()).isEqualTo(5_000);
    }

    @Test
    void keepsEachMemberSeparate() {
        room.markReceipt("member-0001", true, 3_000);
        room.markReceipt("member-0002", false, 3_000);

        assertThat(room.getReceipts()).hasSize(2);
        assertThat(room.getReceipts().get("member-0002").readAt()).isZero();
    }

    @Test
    void ignoresAMissingMemberId() {
        room.markReceipt(null, true, 1_000);
        room.markReceipt("  ", true, 1_000);

        assertThat(room.getReceipts()).isEmpty();
    }
}
