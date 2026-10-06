package de.kingschnulli.opsuchtchat.core;

import java.util.Objects;

public record Classification(
        ChatCategory category,
        String ruleId,
        String privatePartner,
        PrivateMessageDirection privateDirection,
        String privateBody
) {
    public Classification {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(ruleId, "ruleId");
    }

    public Classification(ChatCategory category, String ruleId) {
        this(category, ruleId, null, null, null);
    }

    public Classification(ChatCategory category, String ruleId, String privatePartner) {
        this(category, ruleId, privatePartner, null, null);
    }

    public static Classification message(String ruleId) {
        return new Classification(ChatCategory.MESSAGE, ruleId);
    }
}
