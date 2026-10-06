package de.kingschnulli.opsuchtchat.core;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stateful classifier for the message formats seen on opsucht.net.
 *
 * The rules intentionally prefer precision over recall. Unknown messages stay in ALL,
 * so a bad rule cannot make a message disappear from the user's chat.
 */
public final class OpsuchtClassifier {
    private static final Duration AUCTION_IDLE_TIMEOUT = Duration.ofSeconds(120);

    private static final List<Pattern> PRIVATE_PATTERNS = patterns(
            "\\bfreunde\\s*[»>]\\s*\\[[^\\]]*(?:->|→)\\s*mir\\s*]",
            "\\bfreunde\\s*[»>]\\s*\\[\\s*mir\\s*(?:->|→)[^\\]]*]",
            "^\\s*\\[(?:pn|pm|msg|privat)\\]",
            "^\\s*(?:pn|pm|privat)\\s*[»>:]"
    );

    private static final List<Pattern> AUCTION_PREFIX_PATTERNS = patterns(
            "^\\s*\\[(?:auktion|auction|versteigerung)]",
            "^\\s*(?:auktion|auction|versteigerung)\\s*[|»>:]"
    );

    private static final List<Pattern> AUCTION_START_PATTERNS = patterns(
            "\\bversteiger(?:e|t|ung|ungen)?\\b",
            "\\bwird\\s+versteigert\\b",
            "\\bneue\\s+auktion\\b",
            "\\b(?:auktion|versteigerung)\\s+(?:gestartet|beginnt|startet)\\b"
    );

    private static final List<Pattern> AUCTION_BID_PATTERNS = patterns(
            "\\b(?:bietet|gebot|geboten|höchstgebot|hoechstgebot|überbietet|ueberbietet|überboten|ueberboten)\\b",
            "\\bneues\\s+höchstgebot\\b",
            "\\baktuell(?:es)?\\s+gebot\\b"
    );

    /**
     * Opsucht player auctions are mostly plain chat. Once an auction is active,
     * bidders often send only a number ("2100", "2108$"), while the seller counts
     * down with "2108 zum ersten/zweiten/dritten".
     */
    private static final List<Pattern> AUCTION_ACTIVE_BID_PATTERNS = patterns(
            "^\\s*\\d[\\d.,]*\\s*(?:\\$|dollar|k|kk|m|mio\\.?|b)?\\s*$",
            "^\\s*(?:und\\s+)?\\d[\\d.,]*\\s*(?:\\$|dollar|k|kk|m|mio\\.?|b)?\\s+zum\\s+(?:ersten|zweiten)\\b.*$",
            "\\bzum\\s+(?:ersten|zweiten)\\b"
    );

    private static final List<Pattern> AUCTION_END_PATTERNS = patterns(
            "\\b(?:wurde|ist)\\s+.*\\bverkauft\\b",
            "\\bverkauft\\s+an\\b",
            "\\b(?:ersteigert|ersteigerte|gewonnen)\\b",
            "\\b(?:auktion|versteigerung)\\s+(?:beendet|abgelaufen|vorbei)\\b",
            "\\bkeine?n?\\s+gebote?\\b",
            "\\bzum\\s+dritten\\b",
            "\\bzum\\s+3\\.?(?:ten)?\\b"
    );

    private static final List<Pattern> ADVERTISING_PATTERNS = patterns(
            "^\\s*\\[(?:werbung|advertisement|shop)]",
            "^\\s*(?:werbung|advertisement|shop)\\s*[|»>:]",
            "\\bwerbeanzeige\\b",
            "\\bmega\\s+sale\\b",
            "(?:^|\\s)/(?:sw|shop)\\b",
            "\\bverkaufe\\b.*(?:\\b(?:für|fuer|msg|angebot|inventar)\\b|/msg)",
            "^\\s*verkaufe\\s+mein\\s+inventar\\b",
            "\\bverkauft\\s+wer\\b",
            "\\bbei\\s+int(?:e)?resse\\b.*(?:/msg|\\bmsg\\b)"
    );

    private static final List<Pattern> SERVER_PREFIX_PATTERNS = patterns(
            "^\\s*\\[(?:server|system|netzwerk|network|discord|opsucht)]",
            "^\\s*(?:server|system|netzwerk|network|discord|opsucht)\\s*[|»>:]",
            "^\\s*freunde\\s*[»>]\\s*\\[\\s*opsucht\\s*(?:->|→)\\s*mir\\s*]"
    );

    private Instant auctionActiveUntil = Instant.EPOCH;

    public Classification classify(ChatEnvelope message) {
        String text = TextNormalizer.normalize(message.text());
        String playerContent = playerContent(text);
        Instant now = message.receivedAt();

        // Official Opsucht announcements can deliberately look like a private
        // message ("FREUNDE » [OPSUCHT -> Mir]"). Keep those in SERVER, not PN.
        if (matchesAny(SERVER_PREFIX_PATTERNS, text)) {
            return new Classification(ChatCategory.SERVER, "server.prefix");
        }

        if (matchesAny(PRIVATE_PATTERNS, text)) {
            return new Classification(ChatCategory.PRIVATE, "private.explicit");
        }

        boolean auctionPrefixed = matchesAny(AUCTION_PREFIX_PATTERNS, text);
        boolean auctionStart = auctionPrefixed || matchesAny(AUCTION_START_PATTERNS, playerContent);
        if (auctionStart) {
            openAuction(now);
            return new Classification(ChatCategory.AUCTION, auctionPrefixed ? "auction.prefix" : "auction.start");
        }

        if (isAuctionActive(now) && matchesAny(AUCTION_END_PATTERNS, playerContent)) {
            closeAuction();
            return new Classification(ChatCategory.AUCTION, "auction.end");
        }

        if (isAuctionActive(now)
                && (matchesAny(AUCTION_BID_PATTERNS, playerContent)
                || matchesAny(AUCTION_ACTIVE_BID_PATTERNS, playerContent))) {
            extendAuction(now);
            return new Classification(ChatCategory.AUCTION, "auction.bid");
        }

        if (matchesAny(ADVERTISING_PATTERNS, playerContent)) {
            return new Classification(ChatCategory.ADVERTISING, "advertising.player");
        }

        return Classification.all("fallback.all");
    }

    /**
     * Stateless fallback used only when a platform sees a message that was not observed live.
     * It deliberately does not infer auction follow-up state.
     */
    public Classification classifyStateless(ChatEnvelope message) {
        String text = TextNormalizer.normalize(message.text());
        String playerContent = playerContent(text);

        if (matchesAny(SERVER_PREFIX_PATTERNS, text)) {
            return new Classification(ChatCategory.SERVER, "server.prefix");
        }
        if (matchesAny(PRIVATE_PATTERNS, text)) {
            return new Classification(ChatCategory.PRIVATE, "private.explicit");
        }
        if (matchesAny(AUCTION_PREFIX_PATTERNS, text) || matchesAny(AUCTION_START_PATTERNS, playerContent)) {
            return new Classification(ChatCategory.AUCTION, "auction.stateless");
        }
        if (matchesAny(ADVERTISING_PATTERNS, playerContent)) {
            return new Classification(ChatCategory.ADVERTISING, "advertising.player");
        }
        return Classification.all("fallback.all");
    }

    public void resetSessionState() {
        closeAuction();
    }

    private boolean isAuctionActive(Instant now) {
        if (now.isAfter(auctionActiveUntil)) {
            closeAuction();
            return false;
        }
        return auctionActiveUntil.isAfter(Instant.EPOCH);
    }

    private void openAuction(Instant now) {
        auctionActiveUntil = now.plus(AUCTION_IDLE_TIMEOUT);
    }

    private void extendAuction(Instant now) {
        auctionActiveUntil = now.plus(AUCTION_IDLE_TIMEOUT);
    }

    private void closeAuction() {
        auctionActiveUntil = Instant.EPOCH;
    }

    /**
     * Removes the rank/name prefix from a normal player chat line while leaving
     * non-player/system formats untouched. Wrapped chat messages often look like:
     * "» PLATIN | Name » message »".
     */
    private static String playerContent(String text) {
        String value = text.trim();
        if (value.endsWith("»")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        int separator = value.lastIndexOf('»');
        if (separator >= 0) {
            return value.substring(separator + 1).trim();
        }
        return value;
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> patterns(String... regexes) {
        return java.util.Arrays.stream(regexes)
                .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                .toList();
    }
}
