package de.kingschnulli.opsuchtchat.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.kingschnulli.opsuchtchat.core.social.LocalSocialStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
}
