package de.kingschnulli.opsuchtchat.core;

import java.time.Instant;
import java.util.Objects;

public record PrivateMessageEntry(
        Instant receivedAt,
        String partner,
        PrivateMessageDirection direction,
        String body
) {
    public PrivateMessageEntry {
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(partner, "partner");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(body, "body");
    }
}
