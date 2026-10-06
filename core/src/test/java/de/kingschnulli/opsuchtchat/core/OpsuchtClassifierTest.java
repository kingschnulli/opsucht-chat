package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OpsuchtClassifierTest {
    private final Instant now = Instant.parse("2026-10-06T18:00:00Z");

    @Test
    void classifiesFriendPrivateMessages() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] was geht"
        ));

        assertEquals(ChatCategory.PRIVATE, result.category());
    }

    @Test
    void keepsAuctionFollowUpTogether() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "Spieler | Steve » versteigere Diamantschwert"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(20),
                ChatSource.SERVER_SYSTEM,
                "Alex bietet 10.000$"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(40),
                ChatSource.SERVER_SYSTEM,
                "Das Item wurde an Alex verkauft"
        )).category());
    }

    @Test
    void recognizesRealPlayerAuctionBidsAndCountdown() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "Diamond | ~PG_Mystical » versteigere Testitem"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(5),
                ChatSource.PLAYER,
                "Spieler | Mikasa2009 » 2002$"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(10),
                ChatSource.PLAYER,
                "Ultra | ~Dom_P_Can » 2100"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(15),
                ChatSource.PLAYER,
                "Diamond | ~PG_Mystical » 2108 dollar zum ersten"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(20),
                ChatSource.PLAYER,
                "Diamond | ~PG_Mystical » 2108 zum zweiten"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(25),
                ChatSource.PLAYER,
                "Diamond | ~PG_Mystical » und 2108 zum dritten vk"
        )).category());

        // "zum dritten" closes the state, so later bare numbers are normal chat again.
        assertEquals(ChatCategory.ALL, classifier.classify(new ChatEnvelope(
                now.plusSeconds(30),
                ChatSource.PLAYER,
                "Spieler | Jemand » 2500"
        )).category());
    }

    @Test
    void auctionStateExpires() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();

        classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "Spieler | Steve » versteigere Diamantschwert"
        ));

        Classification result = classifier.classify(new ChatEnvelope(
                now.plusSeconds(121),
                ChatSource.SERVER_SYSTEM,
                "Alex bietet 10.000$"
        ));

        assertEquals(ChatCategory.ALL, result.category());
    }

    @Test
    void unknownPlayerChatStaysInAll() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "Spieler | BossNumber38491 » Sup warum können ..."
        ));

        assertEquals(ChatCategory.ALL, result.category());
    }

    @Test
    void opsuchtServerPrefixGoesToServerWithLegacyFormatting() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();

        assertEquals(ChatCategory.SERVER, classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "§c§lOPSUCHT§r §8»§r §7§fSchon die neuen Kisten Items gesehen?"
        )).category());

        assertEquals(ChatCategory.SERVER, classifier.classify(new ChatEnvelope(
                now.plusSeconds(1),
                ChatSource.SERVER_SYSTEM,
                "OPSUCHT » Du wurdest teleportiert."
        )).category());
    }

    @Test
    void officialOpsuchtFriendStylePromotionIsServerNotPrivate() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "§b§lFREUNDE §8» §7[§cOPSUCHT §7-> §cMir§7] §fPsst... /warp kisten"
        ));

        assertEquals(ChatCategory.SERVER, result.category());
    }

    @Test
    void explicitServerPrefixGoesToServer() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "SERVER | Der Server startet in 60 Sekunden neu."
        ));

        assertEquals(ChatCategory.SERVER, result.category());
    }

    @Test
    void recognizesObservedPlayerAdvertising() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();

        assertEquals(ChatCategory.ADVERTISING, classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "»\nPLATIN | TayzZ » MEGA SALE - Die wichtigsten Items bei uns im Shop - /sw Gildenshop\n»"
        )).category());

        assertEquals(ChatCategory.ADVERTISING, classifier.classify(new ChatEnvelope(
                now.plusSeconds(1),
                ChatSource.PLAYER,
                "»\nPLATIN | WeeF07 » ich verkaufe einen [Zeus Gewitterbogen] für 10m bei intresse /msg me\n»"
        )).category());

        assertEquals(ChatCategory.ADVERTISING, classifier.classify(new ChatEnvelope(
                now.plusSeconds(2),
                ChatSource.PLAYER,
                "»\nPLATIN | KenjiTheWerwolf » Verkaufe mein Inventar\n»"
        )).category());

        assertEquals(ChatCategory.ADVERTISING, classifier.classify(new ChatEnvelope(
                now.plusSeconds(3),
                ChatSource.PLAYER,
                "»\nPLATIN | KenjiTheWerwolf » Verkauft wer ein großes merge\n»"
        )).category());
    }

    @Test
    void conversationalSaleMessageDoesNotAutomaticallyBecomeAdvertising() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "Spieler | WomLord80 » nein ich verkaufe dir was"
        ));

        assertEquals(ChatCategory.ALL, result.category());
    }

    @Test
    void advertisingPrefixGoesToAdvertising() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "WERBUNG | Besuche jetzt unseren Shop"
        ));

        assertEquals(ChatCategory.ADVERTISING, result.category());
    }

    @Test
    void unknownSystemChatStaysInAllBecauseServersOftenUseSystemPacketsForPlayerChat() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "Spieler | BossNumber38491 » Hallo zusammen"
        ));

        assertEquals(ChatCategory.ALL, result.category());
    }

    @Test
    void discordBridgeIsServerNoise() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "Discord | Julien9386 » Test"
        ));

        assertEquals(ChatCategory.SERVER, result.category());
    }
}
