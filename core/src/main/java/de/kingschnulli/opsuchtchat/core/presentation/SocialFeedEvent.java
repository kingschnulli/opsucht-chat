package de.kingschnulli.opsuchtchat.core.presentation;

public record SocialFeedEvent(
        SocialEventKind kind,
        String actor,
        String title,
        String body
) {
}
