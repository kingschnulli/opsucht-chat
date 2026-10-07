package de.kingschnulli.opsuchtchat.core.server;

import java.util.List;
import java.util.Objects;

public record ServerHubPage(
        String id,
        String label,
        List<ServerCommandSpec> commands
) {
    public ServerHubPage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(commands, "commands");
        commands = List.copyOf(commands);
    }
}
