package de.kingschnulli.opsuchtchat.core;

import java.util.Objects;

public record Classification(
        ChatCategory category,
        String ruleId,
        String privatePartner
) {
    public Classification {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(ruleId, "ruleId");
    }

    public Classification(ChatCategory category, String ruleId) {
        this(category, ruleId, null);
    }

    public static Classification message(String ruleId) {
        return new Classification(ChatCategory.MESSAGE, ruleId);
    }
}
