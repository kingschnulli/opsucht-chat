package de.kingschnulli.opsuchtchat.core;

import java.util.Objects;

public record Classification(ChatCategory category, String ruleId) {
    public Classification {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(ruleId, "ruleId");
    }

    public static Classification all(String ruleId) {
        return new Classification(ChatCategory.ALL, ruleId);
    }
}
