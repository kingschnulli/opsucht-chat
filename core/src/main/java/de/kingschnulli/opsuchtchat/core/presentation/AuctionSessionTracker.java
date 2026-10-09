package de.kingschnulli.opsuchtchat.core.presentation;

public final class AuctionSessionTracker {
    private boolean active;
    private String seller;
    private String item;
    private String currentBidder;
    private String currentAmount;
    private AuctionEventKind phase = AuctionEventKind.OTHER;

    public synchronized void accept(AuctionFeedEvent event) {
        if (event == null) {
            return;
        }

        switch (event.kind()) {
            case START -> {
                active = true;
                seller = event.player();
                item = event.item();
                currentBidder = null;
                currentAmount = event.amount();
                phase = AuctionEventKind.START;
            }
            case BID -> {
                if (!active) {
                    active = true;
                }
                currentBidder = event.player();
                if (event.amount() != null) {
                    currentAmount = event.amount();
                }
                phase = AuctionEventKind.BID;
            }
            case COUNTDOWN -> {
                if (!active) {
                    active = true;
                }
                if (event.amount() != null) {
                    currentAmount = event.amount();
                }
                if (item == null && event.item() != null) {
                    item = event.item();
                }
                phase = AuctionEventKind.COUNTDOWN;
            }
            case SOLD -> {
                if (event.amount() != null) {
                    currentAmount = event.amount();
                }
                if (item == null && event.item() != null) {
                    item = event.item();
                }
                phase = AuctionEventKind.SOLD;
                active = false;
            }
            case CANCELLED -> {
                if (item == null && event.item() != null) {
                    item = event.item();
                }
                phase = AuctionEventKind.CANCELLED;
                active = false;
            }
            case OTHER -> {
                if (active && event.amount() != null) {
                    currentAmount = event.amount();
                }
            }
        }
    }

    public synchronized AuctionSessionSnapshot snapshot() {
        return new AuctionSessionSnapshot(
                active,
                seller,
                item,
                currentBidder,
                currentAmount,
                phase
        );
    }

    public synchronized void reset() {
        active = false;
        seller = null;
        item = null;
        currentBidder = null;
        currentAmount = null;
        phase = AuctionEventKind.OTHER;
    }
}
