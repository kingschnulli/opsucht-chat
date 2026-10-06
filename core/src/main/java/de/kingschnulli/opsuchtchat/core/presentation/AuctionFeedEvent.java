package de.kingschnulli.opsuchtchat.core.presentation;

public record AuctionFeedEvent(
        AuctionEventKind kind,
        String player,
        String body,
        String amount,
        String item
) {
}
