package de.kingschnulli.opsuchtchat.core.presentation;

public record ServerFeedEvent(
        ServerEventKind kind,
        String title,
        String body,
        String action
) {
}
