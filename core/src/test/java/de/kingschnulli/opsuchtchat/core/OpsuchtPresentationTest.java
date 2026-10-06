package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.kingschnulli.opsuchtchat.core.presentation.AuctionEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.server.opsucht.OpsuchtServerAdapter;
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
    void opsuchtIdentityBaseHandlesPublicAliases() {
        assertEquals("pg_mystical", adapter.identityBase("~PG_Mystical"));
        assertEquals("pg_mystical", adapter.identityBase(".PG_Mystical"));
    }
}
