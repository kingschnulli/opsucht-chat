package de.kingschnulli.opsuchtchat.core.server;

import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.TextRange;
import java.util.Locale;

/**
 * Server-specific semantics live behind this boundary.
 *
 * The UI and social model must not know how a server formats PMs, auctions,
 * advertisements, public player names, aliases, server events or commands.
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
        PublicChatLine parsed = parsePublicChat(text);
        return parsed == null ? null : parsed.player();
    }

    default PublicChatLine parsePublicChat(String text) {
        return null;
    }

    default TextRange publicPlayerRange(String text) {
        return null;
    }

    default ServerFeedEvent parseServerEvent(String text) {
        return null;
    }

    default AuctionFeedEvent parseAuctionEvent(String text) {
        return null;
    }

    default String identityBase(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
