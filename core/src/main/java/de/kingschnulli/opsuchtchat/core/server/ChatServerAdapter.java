package de.kingschnulli.opsuchtchat.core.server;

import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.SocialFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.TextRange;
import java.util.List;
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

    default String friendAddCommand(String partner) {
        return null;
    }

    default String friendMenuCommand() {
        return null;
    }

    default String friendRequestsCommand() {
        return null;
    }

    default List<ServerHubPage> serverHubPages() {
        return List.of();
    }

    default List<String> defaultServerCommandFavorites() {
        return List.of();
    }

    default List<PlayerActionSpec> playerActions(String partner) {
        return List.of();
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

    default TextRange publicBodyRange(String text) {
        return null;
    }

    default TextRange serverBodyRange(String text) {
        return null;
    }

    default String serverClickActionLabel(String messageText, String clickableText) {
        return clickableText;
    }

    default ServerFeedEvent parseServerEvent(String text) {
        return null;
    }

    default SocialFeedEvent parseSocialEvent(String text) {
        return null;
    }

    default AuctionFeedEvent parseAuctionEvent(String text) {
        return null;
    }

    default String identityBase(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
