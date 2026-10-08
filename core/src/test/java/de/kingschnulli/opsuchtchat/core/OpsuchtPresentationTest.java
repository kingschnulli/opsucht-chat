package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.kingschnulli.opsuchtchat.core.presentation.AuctionEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionSessionTracker;
import de.kingschnulli.opsuchtchat.core.server.PlayerActionMode;
import de.kingschnulli.opsuchtchat.core.server.ServerCommandMode;
import de.kingschnulli.opsuchtchat.core.server.ServerHubPage;
import de.kingschnulli.opsuchtchat.core.server.opsucht.OpsuchtServerAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpsuchtPresentationTest {
    private final OpsuchtServerAdapter adapter = new OpsuchtServerAdapter();

    @Test
    void parsesPublicChatIntoRankPlayerAndBody() {
        PublicChatLine line = adapter.parsePublicChat(
                "»\nPLATIN | ~§4S§ck§4y§cc§6h§ee§en » Viel spaß damit ^^\n»"
        );

        assertNotNull(line);
        assertEquals("PLATIN", line.rank());
        assertEquals("~Skychen", line.player());
        assertEquals("Viel spaß damit ^^", line.body());
    }

    @Test
    void exposesServerBodyRangeWithoutDroppingInteractiveText() {
        String raw = "§c§lOPSUCHT§r §8»§r §7§fSpielerX hat dir eine Teleport-Anfrage geschickt. Klicke hier zum Annehmen.";
        var range = adapter.serverBodyRange(raw);

        assertNotNull(range);
        assertEquals(
                "SpielerX hat dir eine Teleport-Anfrage geschickt. Klicke hier zum Annehmen.",
                raw.substring(range.start(), range.end()).replaceAll("(?i)§[0-9A-FK-ORX]", "")
        );

        String friendStyle = "§b§lFREUNDE §8» §7[§cOPSUCHT §7-> §cMir§7] §fPsst... /warp kisten";
        var friendRange = adapter.serverBodyRange(friendStyle);
        assertNotNull(friendRange);
        assertEquals(
                "Psst... /warp kisten",
                friendStyle.substring(friendRange.start(), friendRange.end()).replaceAll("(?i)§[0-9A-FK-ORX]", "")
        );
    }

    @Test
    void labelsInteractiveServerActions() {
        assertEquals(
                "Annehmen",
                adapter.serverClickActionLabel(
                        "SpielerX hat dir eine Teleport-Anfrage geschickt. Klicke hier zum Annehmen.",
                        "hier"
                )
        );
        assertEquals(
                "Ablehnen",
                adapter.serverClickActionLabel(
                        "Klicke Ablehnen, wenn du die Anfrage nicht möchtest.",
                        "Ablehnen"
                )
        );
    }

    @Test
    void parsesServerPaymentsAndCommands() {
        ServerFeedEvent money = adapter.parseServerEvent(
                "OPSUCHT » .EinfachJohn9215 hat dir 5.500$ gegeben."
        );
        assertEquals(ServerEventKind.MONEY, money.kind());

        ServerFeedEvent action = adapter.parseServerEvent(
                "§c§lOPSUCHT§r §8»§r §7§f➜ §b/tpa <name>"
        );
        assertEquals(ServerEventKind.ACTION, action.kind());
        assertEquals("/tpa <name>", action.action());

        ServerFeedEvent proxy = adapter.parseServerEvent(
                "This BungeeCord server does not provide recipes to JEI."
        );
        assertEquals(ServerEventKind.INFO, proxy.kind());
        assertEquals("Client / Proxy", proxy.title());
    }

    @Test
    void parsesAuctionTimeline() {
        AuctionFeedEvent start = adapter.parseAuctionEvent(
                "PLATIN | Kingschnulli » Versteigere [OPSUCHT Kaffeetasse] - start 1$"
        );
        assertEquals(AuctionEventKind.START, start.kind());
        assertEquals("OPSUCHT Kaffeetasse", start.item());

        AuctionFeedEvent bid = adapter.parseAuctionEvent(
                "Diamond | JustiniusOG » 5.5k"
        );
        assertEquals(AuctionEventKind.BID, bid.kind());
        assertEquals("5.5k", bid.amount());

        AuctionFeedEvent count = adapter.parseAuctionEvent(
                "PLATIN | Kingschnulli » 5.5k [OPSUCHT Kaffeetasse] zum 2."
        );
        assertEquals(AuctionEventKind.COUNTDOWN, count.kind());

        AuctionFeedEvent sold = adapter.parseAuctionEvent(
                "PLATIN | Kingschnulli » [OPSUCHT Kaffeetasse] verkauft - bitte tpa"
        );
        assertEquals(AuctionEventKind.SOLD, sold.kind());
    }

    @Test
    void exposesOpsuchtSocialCommands() {
        assertEquals("freund hinzufügen SkyDecaxy", adapter.friendAddCommand("SkyDecaxy"));
        assertEquals("freund", adapter.friendMenuCommand());
        assertEquals("freund anfragen", adapter.friendRequestsCommand());
        assertEquals("pay SkyDecaxy 5000", adapter.paymentCommand("SkyDecaxy", "5000"));
    }

    @Test
    void exposesContextualPlayerActions() {
        var actions = adapter.playerActions("SkyDecaxy");

        assertEquals(
                List.of("friend", "pay", "tpa", "ignore", "realname"),
                actions.stream().map(action -> action.id()).toList()
        );
        assertEquals(PlayerActionMode.PAY_AMOUNT, actions.get(1).mode());
        assertEquals("tpa SkyDecaxy", actions.get(2).command());
    }

    @Test
    void tracksAuctionSessionSummary() {
        AuctionSessionTracker tracker = new AuctionSessionTracker();

        tracker.accept(adapter.parseAuctionEvent(
                "PLATIN | Kingschnulli » Versteigere [OPSUCHT Kaffeetasse] - start 1$"
        ));
        tracker.accept(adapter.parseAuctionEvent(
                "Diamond | JustiniusOG » 5.5k"
        ));

        var snapshot = tracker.snapshot();
        assertEquals(true, snapshot.active());
        assertEquals("Kingschnulli", snapshot.seller());
        assertEquals("OPSUCHT Kaffeetasse", snapshot.item());
        assertEquals("JustiniusOG", snapshot.currentBidder());
        assertEquals("5.5k", snapshot.currentAmount());
    }

    @Test
    void exposesCommandHubPagesAndSafeInteractionModes() {
        List<ServerHubPage> pages = adapter.serverHubPages();

        assertEquals(
                List.of("quick", "social", "travel", "economy", "home", "utility", "info"),
                pages.stream().map(ServerHubPage::id).toList()
        );

        var pay = pages.stream()
                .flatMap(page -> page.commands().stream())
                .filter(command -> command.id().equals("pay"))
                .findFirst()
                .orElseThrow();
        var spawn = pages.stream()
                .flatMap(page -> page.commands().stream())
                .filter(command -> command.id().equals("spawn"))
                .findFirst()
                .orElseThrow();

        assertEquals(ServerCommandMode.PREFILL, pay.mode());
        assertEquals(ServerCommandMode.RUN, spawn.mode());
        assertEquals(List.of("home", "spawn", "pay", "ah"), adapter.defaultServerCommandFavorites());
    }

    @Test
    void opsuchtIdentityBaseHandlesPublicAliases() {
        assertEquals("pg_mystical", adapter.identityBase("~PG_Mystical"));
        assertEquals("pg_mystical", adapter.identityBase(".PG_Mystical"));
    }
}
