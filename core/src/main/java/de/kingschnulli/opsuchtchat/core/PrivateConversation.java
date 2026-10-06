package de.kingschnulli.opsuchtchat.core;

/**
 * Snapshot used by client UIs for the most recently active private conversations.
 */
public record PrivateConversation(String name, int unread) {
}
