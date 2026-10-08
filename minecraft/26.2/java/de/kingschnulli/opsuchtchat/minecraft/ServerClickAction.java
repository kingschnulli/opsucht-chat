package de.kingschnulli.opsuchtchat.minecraft;

import java.util.Objects;
import net.minecraft.network.chat.ClickEvent;

public record ServerClickAction(
        String label,
        ClickEvent clickEvent
) {
    public ServerClickAction {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(clickEvent, "clickEvent");
    }
}
