package de.kingschnulli.opsuchtchat.core.server;

import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.Classification;
import java.util.Locale;

/**
 * Server-specific semantics live behind this boundary.
 *
 * The UI and social model must not know how a server formats PMs, auctions,
 * advertisements, public player names, aliases or commands.
 */
public interface ChatServerAdapter {
    String id();

    String displayName();

    boolean matchesAddress(String address);

    Classification classify(ChatEnvelope message);

    Classification classifyStateless(ChatEnvelope message);

    void resetSessionState();

    String privateMessageCommand(String partner, String message);

    default String paymentCommand(String partner, String amount) {
        return "pay " + partner + " " + amount;
    }

    default String extractPublicPlayerName(String text) {
        return null;
    }

    default String identityBase(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
