package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpsuchtChatEngineTest {
    private final Instant now = Instant.parse("2026-10-06T18:00:00Z");

    @Test
    void openingPnSelectsMostRecentConversationWithoutClearingOthers() {
        OpsuchtChatEngine engine = new OpsuchtChatEngine();

        engine.onIncoming(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] eins"
        ));
        engine.onIncoming(new ChatEnvelope(
                now.plusSeconds(1),
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [OtherUser -> Mir] zwei"
        ));

        assertEquals(2, engine.unread(ChatCategory.PRIVATE));

        engine.select(ChatCategory.PRIVATE);
        assertEquals("OtherUser", engine.activePrivatePartner());
        assertEquals(1, engine.unread(ChatCategory.PRIVATE));
    }

    @Test
    void transcriptKeepsDirectionBodyAndPreview() {
        OpsuchtChatEngine engine = new OpsuchtChatEngine();

        engine.onIncoming(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] eins"
        ));
        engine.onIncoming(new ChatEnvelope(
                now.plusSeconds(1),
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Du -> Ruffy333] zwei"
        ));

        List<PrivateMessageEntry> messages = engine.privateMessages("Ruffy333");
        assertEquals(2, messages.size());
        assertEquals(PrivateMessageDirection.INCOMING, messages.get(0).direction());
        assertEquals("eins", messages.get(0).body());
        assertEquals(PrivateMessageDirection.OUTGOING, messages.get(1).direction());
        assertEquals("zwei", messages.get(1).body());

        PrivateConversation conversation = engine.recentPrivateConversations().get(0);
        assertEquals("zwei", conversation.preview());
        assertEquals(now.plusSeconds(1), conversation.lastMessageAt());
    }

    @Test
    void selectingNamedPrivateConversationOnlyClearsThatConversation() {
        OpsuchtChatEngine engine = new OpsuchtChatEngine();

        engine.onIncoming(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] eins"
        ));
        engine.onIncoming(new ChatEnvelope(
                now.plusSeconds(1),
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [OtherUser -> Mir] zwei"
        ));

        engine.selectPrivatePartner("Ruffy333");

        List<PrivateConversation> recent = engine.recentPrivateConversations();
        PrivateConversation ruffy = recent.stream().filter(c -> c.name().equals("Ruffy333")).findFirst().orElseThrow();
        PrivateConversation other = recent.stream().filter(c -> c.name().equals("OtherUser")).findFirst().orElseThrow();

        assertEquals(0, ruffy.unread());
        assertEquals(1, other.unread());
        assertEquals("Ruffy333", engine.activePrivatePartner());
    }

    @Test
    void socialRequestCreatesUnreadConversationPreview() {
        OpsuchtChatEngine engine = new OpsuchtChatEngine();

        engine.touchSocialConversation(
                "SkyDecaxy",
                "Teleport-Anfrage",
                now
        );

        assertEquals(1, engine.unread(ChatCategory.PRIVATE));
        PrivateConversation conversation = engine.recentPrivateConversations().get(0);
        assertEquals("SkyDecaxy", conversation.name());
        assertEquals("Teleport-Anfrage", conversation.preview());

        engine.selectPrivatePartner("SkyDecaxy");
        assertEquals(0, engine.unread(ChatCategory.PRIVATE));
    }

    @Test
    void pinSurvivesTransientResetAndCloseRemovesIt() {
        OpsuchtChatEngine engine = new OpsuchtChatEngine();
        engine.selectPrivatePartner("Ruffy333");

        assertTrue(engine.togglePrivatePinned("Ruffy333"));
        assertTrue(engine.recentPrivateConversations().get(0).pinned());

        engine.resetTransientState();
        assertEquals("Ruffy333", engine.recentPrivateConversations().get(0).name());
        assertTrue(engine.recentPrivateConversations().get(0).pinned());

        engine.closePrivatePartner("Ruffy333");
        assertTrue(engine.recentPrivateConversations().isEmpty());
        assertFalse(engine.pinnedPrivatePartners().contains("Ruffy333"));
    }
}
