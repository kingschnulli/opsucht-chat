package de.kingschnulli.opsuchtchat.core.social;

import java.util.List;
import java.util.Objects;

public record PlayerIdentity(
        String displayName,
        String canonicalName,
        String uuid,
        boolean online,
        List<String> aliases
) {
    public PlayerIdentity {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(canonicalName, "canonicalName");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
