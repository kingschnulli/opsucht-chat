package de.kingschnulli.opsuchtchat.core;

import java.time.Instant;
import java.util.Objects;

public record ChatEnvelope(
        Instant receivedAt,
        ChatSource source,
        String text
) {
    public ChatEnvelope {
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(text, "text");
    }

    public static ChatEnvelope now(ChatSource source, String text) {
        return new ChatEnvelope(Instant.now(), source, text);
    }
}
