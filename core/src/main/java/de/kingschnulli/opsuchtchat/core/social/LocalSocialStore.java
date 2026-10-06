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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tiny dependency-free local social store.
 *
 * Data is deliberately server-adapter scoped so a future second server cannot
 * accidentally mix identities or private-message history with another server.
 */
public final class LocalSocialStore {
    private static final long COMPACT_AFTER_BYTES = 4L * 1024L * 1024L;
    private static final int COMPACT_KEEP_LINES = 5_000;

    private final Path directory;
    private final Path favoritesFile;
    private final Path privateHistoryFile;
    private final Path aliasesFile;
    private final Path importantMessagesFile;

    public LocalSocialStore(Path rootDirectory, String adapterId) {
        this.directory = rootDirectory.resolve("social").resolve(sanitize(adapterId));
        this.favoritesFile = directory.resolve("favorites.txt");
        this.privateHistoryFile = directory.resolve("private-messages.tsv");
        this.aliasesFile = directory.resolve("aliases.tsv");
        this.importantMessagesFile = directory.resolve("important-private.txt");
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

    public Map<String, String> loadAliases() {
        if (!Files.isRegularFile(aliasesFile)) {
            return Map.of();
        }

        try {
            Map<String, String> result = new LinkedHashMap<>();
            for (String line : Files.readAllLines(aliasesFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 2);
                if (parts.length != 2) {
                    continue;
                }

                try {
                    String alias = decode(parts[0]);
                    String canonical = decode(parts[1]);
                    if (!alias.isBlank() && !canonical.isBlank()) {
                        result.put(identityKey(alias), canonical);
                    }
                } catch (RuntimeException ignored) {
                    // Ignore a damaged line and preserve the rest of the directory.
                }
            }
            return Map.copyOf(result);
        } catch (IOException ignored) {
            return Map.of();
        }
    }

    public void saveAlias(String alias, String canonical) {
        if (alias == null || canonical == null || alias.isBlank() || canonical.isBlank()) {
            return;
        }

        Map<String, String> aliases = new LinkedHashMap<>(loadAliases());
        aliases.put(identityKey(alias), canonical.trim());

        try {
            Files.createDirectories(directory);
            List<String> lines = aliases.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "\t" + encode(entry.getValue()))
                    .toList();
            Files.write(
                    aliasesFile,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ignored) {
            // Identity hints are optional convenience state.
        }
    }

    public Set<String> loadImportantMessageKeys() {
        if (!Files.isRegularFile(importantMessagesFile)) {
            return Set.of();
        }

        try {
            return Set.copyOf(new LinkedHashSet<>(Files.readAllLines(importantMessagesFile, StandardCharsets.UTF_8)));
        } catch (IOException ignored) {
            return Set.of();
        }
    }

    public boolean setImportant(PrivateMessageEntry message, boolean important) {
        Set<String> keys = new LinkedHashSet<>(loadImportantMessageKeys());
        String key = privateMessageKey(message);
        boolean changed = important ? keys.add(key) : keys.remove(key);

        if (!changed) {
            return important;
        }

        try {
            Files.createDirectories(directory);
            Files.write(
                    importantMessagesFile,
                    keys,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ignored) {
            return !important;
        }

        return important;
    }

    public boolean isImportant(PrivateMessageEntry message) {
        return loadImportantMessageKeys().contains(privateMessageKey(message));
    }

    public Path directory() {
        return directory;
    }

    public static String privateMessageKey(PrivateMessageEntry message) {
        String raw = message.receivedAt().toEpochMilli()
                + "|" + message.direction().name()
                + "|" + identityKey(message.partner())
                + "|" + message.body();
        return encode(raw);
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
        String[] parts = line.split("\t", 4);
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

    private static String identityKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
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
