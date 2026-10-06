package de.kingschnulli.opsuchtchat.core.social;

import de.kingschnulli.opsuchtchat.core.PrivateMessageDirection;
import de.kingschnulli.opsuchtchat.core.PrivateMessageEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * Tiny dependency-free local social store.
 *
 * Data is deliberately server-adapter scoped so a future second server cannot
 * accidentally mix identities or private-message history with Opsucht.
 */
public final class LocalSocialStore {
    private static final long COMPACT_AFTER_BYTES = 4L * 1024L * 1024L;
    private static final int COMPACT_KEEP_LINES = 5_000;

    private final Path directory;
    private final Path favoritesFile;
    private final Path privateHistoryFile;

    public LocalSocialStore(Path rootDirectory, String adapterId) {
        this.directory = rootDirectory.resolve("social").resolve(sanitize(adapterId));
        this.favoritesFile = directory.resolve("favorites.txt");
        this.privateHistoryFile = directory.resolve("private-messages.tsv");
    }

    public List<String> loadFavorites() {
        if (!Files.isRegularFile(favoritesFile)) {
            return List.of();
        }

        try {
            return Files.readAllLines(favoritesFile, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public void saveFavorites(Collection<String> favorites) {
        try {
            Files.createDirectories(directory);
            Files.write(
                    favoritesFile,
                    favorites.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ignored) {
            // Local convenience state must never break chat.
        }
    }

    public void importLegacyFavorites(Path legacyFile) {
        if (Files.isRegularFile(favoritesFile) || !Files.isRegularFile(legacyFile)) {
            return;
        }

        try {
            Files.createDirectories(directory);
            Files.copy(legacyFile, favoritesFile);
        } catch (IOException ignored) {
            // Migration is best effort only.
        }
    }

    public void appendPrivateMessage(PrivateMessageEntry message) {
        try {
            Files.createDirectories(directory);
            String line = message.receivedAt().toEpochMilli()
                    + "\t" + message.direction().name()
                    + "\t" + encode(message.partner())
                    + "\t" + encode(message.body())
                    + "\n";

            Files.writeString(
                    privateHistoryFile,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

            compactIfNeeded();
        } catch (IOException ignored) {
            // Private history is local UX state; never interfere with the live chat.
        }
    }

    public List<PrivateMessageEntry> loadPrivateMessages(int maxEntries) {
        if (maxEntries <= 0 || !Files.isRegularFile(privateHistoryFile)) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(privateHistoryFile, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - maxEntries);
            List<PrivateMessageEntry> result = new ArrayList<>();

            for (int i = start; i < lines.size(); i++) {
                PrivateMessageEntry entry = parse(lines.get(i));
                if (entry != null) {
                    result.add(entry);
                }
            }

            return List.copyOf(result);
        } catch (IOException ignored) {
            return List.of();
        }
    }

    public Path directory() {
        return directory;
    }

    private void compactIfNeeded() throws IOException {
        if (!Files.isRegularFile(privateHistoryFile) || Files.size(privateHistoryFile) < COMPACT_AFTER_BYTES) {
            return;
        }

        List<String> lines = Files.readAllLines(privateHistoryFile, StandardCharsets.UTF_8);
        int start = Math.max(0, lines.size() - COMPACT_KEEP_LINES);
        Files.write(
                privateHistoryFile,
                lines.subList(start, lines.size()),
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static PrivateMessageEntry parse(String line) {
        String[] parts = line.split("\\t", 4);
        if (parts.length != 4) {
            return null;
        }

        try {
            return new PrivateMessageEntry(
                    Instant.ofEpochMilli(Long.parseLong(parts[0])),
                    decode(parts[2]),
                    PrivateMessageDirection.valueOf(parts[1]),
                    decode(parts[3])
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String sanitize(String value) {
        String sanitized = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }
}
