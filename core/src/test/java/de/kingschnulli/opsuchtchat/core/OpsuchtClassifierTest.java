package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OpsuchtClassifierTest {
    private final Instant now = Instant.parse("2026-10-06T18:00:00Z");

    @Test
    void classifiesIncomingPrivateMessageAndExtractsTranscriptData() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] was geht"
        ));

        assertEquals(ChatCategory.PRIVATE, result.category());
        assertEquals("Ruffy333", result.privatePartner());
        assertEquals(PrivateMessageDirection.INCOMING, result.privateDirection());
        assertEquals("was geht", result.privateBody());
    }

    @Test
    void classifiesOutgoingPrivateMessageAndExtractsTranscriptData() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Du -> Ruffy333] jo"
        ));

        assertEquals(ChatCategory.PRIVATE, result.category());
        assertEquals("Ruffy333", result.privatePartner());
        assertEquals(PrivateMessageDirection.OUTGOING, result.privateDirection());
        assertEquals("jo", result.privateBody());
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
        assertNull(result.privatePartner());
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
    void recognizesObservedOpsuchtAuctionVariants() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();

        classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "PLATIN | Kingschnulli » Versteigere [OPSUCHT Kaffeetasse] - start 1$"
        ));

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(5),
                ChatSource.PLAYER,
                "Diamond | JustiniusOG » -1k"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(10),
                ChatSource.PLAYER,
                "PLATIN | HowIMetYourMoney » biete 6k"
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(15),
                ChatSource.PLAYER,
                "PLATIN | Kingschnulli » 5.5k [OPSUCHT Kaffeetasse] zum 2."
        )).category());

        assertEquals(ChatCategory.AUCTION, classifier.classify(new ChatEnvelope(
                now.plusSeconds(20),
                ChatSource.PLAYER,
                "PLATIN | Kingschnulli » [OPSUCHT Kaffeetasse] verkauft - bitte tpa"
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

        assertEquals(ChatCategory.MESSAGE, result.category());
    }

    @Test
    void normalChatGoesToMessageTab() {
        OpsuchtClassifier classifier = new OpsuchtClassifier();
        Classification result = classifier.classify(new ChatEnvelope(
                now,
                ChatSource.PLAYER,
                "Spieler | BossNumber38491 » Sup warum können ..."
        ));

        assertEquals(ChatCategory.MESSAGE, result.category());
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
                "»\nPLATIN | Skiyl » Sicher dir jetzt deine Deals -> /ah Skiyl\n»"
        )).category());
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
