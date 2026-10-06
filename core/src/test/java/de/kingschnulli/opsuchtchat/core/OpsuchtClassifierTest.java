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
