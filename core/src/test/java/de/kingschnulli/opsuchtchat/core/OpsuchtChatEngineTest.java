package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OpsuchtChatEngineTest {
    private final Instant now = Instant.parse("2026-10-06T18:00:00Z");

    @Test
    void privateUnreadCounterResetsWhenTabIsOpened() {
        OpsuchtChatEngine engine = new OpsuchtChatEngine();

        engine.onIncoming(new ChatEnvelope(
                now,
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] eins"
        ));
        engine.onIncoming(new ChatEnvelope(
                now.plusSeconds(1),
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] zwei"
        ));

        assertEquals(2, engine.unread(ChatCategory.PRIVATE));

        engine.select(ChatCategory.PRIVATE);
        assertEquals(0, engine.unread(ChatCategory.PRIVATE));

        engine.onIncoming(new ChatEnvelope(
                now.plusSeconds(2),
                ChatSource.SERVER_SYSTEM,
                "FREUNDE » [Ruffy333 -> Mir] drei"
        ));
        assertEquals(0, engine.unread(ChatCategory.PRIVATE));
    }
}
