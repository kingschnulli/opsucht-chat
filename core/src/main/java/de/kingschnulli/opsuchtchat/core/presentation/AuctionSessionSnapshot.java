package de.kingschnulli.opsuchtchat.core.presentation;

public record AuctionSessionSnapshot(
        boolean active,
        String seller,
        String item,
        String currentBidder,
        String currentAmount,
        AuctionEventKind phase
) {
    public static AuctionSessionSnapshot empty() {
        return new AuctionSessionSnapshot(false, null, null, null, null, AuctionEventKind.OTHER);
    }
}
