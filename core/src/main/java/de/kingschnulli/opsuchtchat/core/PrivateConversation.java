package de.kingschnulli.opsuchtchat.core;

import java.time.Instant;

/**
 * Snapshot used by client UIs for recent private conversations.
 */
public record PrivateConversation(
        String name,
        int unread,
        boolean pinned,
        String preview,
        Instant lastMessageAt
) {
}
