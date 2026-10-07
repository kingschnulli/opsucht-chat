package de.kingschnulli.opsuchtchat.core.server;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record ServerCommandSpec(
        String id,
        String label,
        String command,
        String description,
        List<String> aliases,
        ServerCommandMode mode
) {
    public ServerCommandSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(mode, "mode");
        aliases = List.copyOf(aliases);
    }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String value = query.trim().toLowerCase(Locale.ROOT);
        if (id.toLowerCase(Locale.ROOT).contains(value)
                || label.toLowerCase(Locale.ROOT).contains(value)
                || command.toLowerCase(Locale.ROOT).contains(value)
                || description.toLowerCase(Locale.ROOT).contains(value)) {
            return true;
        }

        return aliases.stream().anyMatch(alias -> alias.toLowerCase(Locale.ROOT).contains(value));
    }
}
