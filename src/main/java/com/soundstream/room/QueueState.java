package com.soundstream.room;

import java.util.List;

/** The host-managed room queue shared with every listener. */
public record QueueState(List<String> trackUrls, int activeIndex, long serverTime) {
}
