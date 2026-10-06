package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpsuchtChatEngineTest {
    private final Instant now = Instant.parse("2026-10-06T18:00:00Z");

    @Test
    void privateUnreadCounterResetsWhenAggregatePnTabIsOpened() {
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
        assertEquals(0, engine.unread(ChatCategory.PRIVATE));
    }

    @Test
    void recentPrivateConversationsArePerPlayerAndMostRecentFirst() {
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
        engine.onIncoming(new ChatEnvelope(
                now.plusSeconds(2),
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] drei"
        ));

        List<PrivateConversation> recent = engine.recentPrivateConversations();
        assertEquals("Ruffy333", recent.get(0).name());
        assertEquals(2, recent.get(0).unread());
        assertEquals("OtherUser", recent.get(1).name());
        assertEquals(1, recent.get(1).unread());
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
}
