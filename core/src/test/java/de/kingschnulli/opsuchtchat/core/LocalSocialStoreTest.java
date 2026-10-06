package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.kingschnulli.opsuchtchat.core.social.LocalSocialStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSocialStoreTest {
    @TempDir
    Path temp;

    @Test
    void persistsFavoritesAndPrivateHistoryPerAdapter() {
        LocalSocialStore store = new LocalSocialStore(temp, "opsucht");
        store.saveFavorites(List.of("SkyDecaxy", ".PG_Mystical"));

        PrivateMessageEntry message = new PrivateMessageEntry(
                Instant.parse("2026-10-06T22:37:51Z"),
                "SkyDecaxy",
                PrivateMessageDirection.OUTGOING,
                "test zurück"
        );
        store.appendPrivateMessage(message);

        assertEquals(List.of("SkyDecaxy", ".PG_Mystical"), store.loadFavorites());
        assertEquals(List.of(message), store.loadPrivateMessages(100));
    }

    @Test
    void persistsAliasesAndImportantPrivateMessages() {
        LocalSocialStore store = new LocalSocialStore(temp, "opsucht");
        store.saveAlias("~PG_Mystical", ".PG_Mystical");

        assertEquals(
                Map.of("~pg_mystical", ".PG_Mystical"),
                store.loadAliases()
        );

        PrivateMessageEntry message = new PrivateMessageEntry(
                Instant.parse("2026-10-06T22:40:00Z"),
                ".PG_Mystical",
                PrivateMessageDirection.INCOMING,
                "wichtige info"
        );

        assertFalse(store.isImportant(message));
        assertTrue(store.setImportant(message, true));
        assertTrue(store.isImportant(message));
        assertFalse(store.setImportant(message, false));
        assertFalse(store.isImportant(message));
    }

    @Test
    void isolatesSocialDataBetweenServerAdapters() {
        LocalSocialStore opsucht = new LocalSocialStore(temp, "opsucht");
        LocalSocialStore another = new LocalSocialStore(temp, "another-server");

        opsucht.saveFavorites(List.of("OnlyOnOpsucht"));

        assertEquals(List.of("OnlyOnOpsucht"), opsucht.loadFavorites());
        assertTrue(another.loadFavorites().isEmpty());
    }
}
