package de.kingschnulli.opsuchtchat.core.server;

import java.util.Objects;

public record PlayerActionSpec(
        String id,
        String label,
        String command,
        PlayerActionMode mode,
        boolean primary
) {
    public PlayerActionSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(mode, "mode");
    }
}
