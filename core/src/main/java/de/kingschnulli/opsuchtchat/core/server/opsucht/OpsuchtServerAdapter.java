package de.kingschnulli.opsuchtchat.core.server.opsucht;

import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.OpsuchtClassifier;
import de.kingschnulli.opsuchtchat.core.OpsuchtHost;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.TextRange;
import de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpsuchtServerAdapter implements ChatServerAdapter {
    private static final Pattern PUBLIC_CHAT = Pattern.compile(
            "(?s)^\\s*»?\\s*(.*?)\\s*\\|\\s*(.+?)\\s*»\\s*(.*?)\\s*»?\\s*$"
    );
    private static final Pattern AMOUNT = Pattern.compile(
            "(?i)(-?\\d[\\d.,]*)\\s*(\\$|dollar|k|kk|m|mio\\.?|b)?"
    );
    private static final Pattern ITEM = Pattern.compile("\\[([^\\]]+)]");

    private final OpsuchtClassifier classifier = new OpsuchtClassifier();

    @Override
    public String id() {
        return "opsucht";
    }

    @Override
    public String displayName() {
        return "OPSUCHT";
    }

    @Override
    public boolean matchesAddress(String address) {
        return OpsuchtHost.matches(address);
    }

    @Override
    public Classification classify(ChatEnvelope message) {
        return classifier.classify(message);
    }

    @Override
    public Classification classifyStateless(ChatEnvelope message) {
        return classifier.classifyStateless(message);
    }

    @Override
    public void resetSessionState() {
        classifier.resetSessionState();
    }

    @Override
    public String privateMessageCommand(String partner, String message) {
        return "msg " + partner + " " + message;
    }

    @Override
    public String paymentCommand(String partner, String amount) {
        return "pay " + partner + " " + amount;
    }

    @Override
    public String friendAddCommand(String partner) {
        return "freund hinzufügen " + partner;
    }

    @Override
    public String friendMenuCommand() {
        return "freund";
    }

    @Override
    public String friendRequestsCommand() {
        return "freund anfragen";
    }

    @Override
    public PublicChatLine parsePublicChat(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PUBLIC_CHAT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }

        String rank = clean(matcher.group(1));
        String player = clean(matcher.group(2));
        String body = clean(matcher.group(3));

        if (!player.matches("[A-Za-z0-9_.~-]{1,32}")) {
            return null;
        }

        return new PublicChatLine(rank, player, body);
    }

    @Override
    public TextRange publicPlayerRange(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PUBLIC_CHAT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }

        int start = matcher.start(2);
        int end = matcher.end(2);
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new TextRange(start, end);
    }

    @Override
    public TextRange publicBodyRange(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PUBLIC_CHAT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }

        int start = matcher.start(3);
        int end = matcher.end(3);
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new TextRange(start, end);
    }

    @Override
    public ServerFeedEvent parseServerEvent(String text) {
        String cleaned = clean(text);
        if (cleaned.isBlank()) {
            return null;
        }

        String body = cleaned;
        String lower = cleaned.toLowerCase(Locale.ROOT);

        if (lower.startsWith("opsucht")) {
            int separator = cleaned.indexOf('»');
            if (separator >= 0) {
                body = cleaned.substring(separator + 1).trim();
            }
        } else if (lower.startsWith("freunde") && lower.contains("opsucht")) {
            int bracket = cleaned.indexOf(']');
            if (bracket >= 0 && bracket + 1 < cleaned.length()) {
                body = cleaned.substring(bracket + 1).trim();
            }
        }

        String bodyLower = body.toLowerCase(Locale.ROOT);

        if (bodyLower.contains("bungeecord") || bodyLower.contains("does not provide recipes to jei")) {
            return new ServerFeedEvent(ServerEventKind.INFO, "Client / Proxy", body, null);
        }

        if (body.startsWith("➜")) {
            String action = body.substring(1).trim();
            return new ServerFeedEvent(ServerEventKind.ACTION, "Befehl", action, action);
        }
        if (bodyLower.contains(" hat dir ") && bodyLower.contains("$") && bodyLower.contains("gegeben")) {
            return new ServerFeedEvent(ServerEventKind.MONEY, "Zahlung erhalten", body, null);
        }
        if (bodyLower.contains("teleport")) {
            return new ServerFeedEvent(ServerEventKind.TELEPORT, "Teleport", body, extractCommand(body));
        }
        if (bodyLower.contains("/vote") || bodyLower.contains("vote unterstützt")) {
            return new ServerFeedEvent(ServerEventKind.VOTE, "Vote", body, null);
        }
        if (bodyLower.contains("gefangen")
                || bodyLower.contains("fänge verkauft")
                || bodyLower.contains("hat angebissen")
                || bodyLower.contains("fadenkreuz")) {
            return new ServerFeedEvent(ServerEventKind.FISHING, "Angeln", body, null);
        }
        if (bodyLower.contains("verkaufsangebot")
                || bodyLower.contains("preis für")
                || bodyLower.contains("marktsystem")
                || bodyLower.contains("/markt")) {
            return new ServerFeedEvent(ServerEventKind.MARKET, "Markt", body, extractCommand(body));
        }
        if (bodyLower.contains("job-level") || bodyLower.contains("lohn von")) {
            return new ServerFeedEvent(ServerEventKind.JOB, "Job", body, null);
        }
        if (bodyLower.contains("booster")) {
            return new ServerFeedEvent(ServerEventKind.BOOSTER, "Booster", body, null);
        }

        return new ServerFeedEvent(ServerEventKind.INFO, "OPSUCHT", body, extractCommand(body));
    }

    @Override
    public AuctionFeedEvent parseAuctionEvent(String text) {
        PublicChatLine chat = parsePublicChat(text);
        if (chat == null) {
            return null;
        }

        String body = chat.body();
        String lower = body.toLowerCase(Locale.ROOT);
        AuctionEventKind kind;

        if (lower.contains("verkauft")) {
            kind = AuctionEventKind.SOLD;
        } else if (lower.matches(".*\\bzum\\s+(?:ersten|zweiten|dritten|2\\.|3\\.).*")) {
            kind = AuctionEventKind.COUNTDOWN;
        } else if (lower.contains("versteiger")) {
            kind = AuctionEventKind.START;
        } else if (lower.matches("\\s*-?\\d[\\d.,]*\\s*(?:\\$|dollar|k|kk|m|mio\\.?|b)?\\s*")
                || lower.matches(".*\\bbiete\\s+-?\\d.*")
                || lower.matches(".*\\bgebot\\b.*")) {
            kind = AuctionEventKind.BID;
        } else {
            kind = AuctionEventKind.OTHER;
        }

        Matcher amountMatcher = AMOUNT.matcher(body);
        String amount = amountMatcher.find()
                ? amountMatcher.group(1) + (amountMatcher.group(2) == null ? "" : amountMatcher.group(2))
                : null;

        Matcher itemMatcher = ITEM.matcher(body);
        String item = itemMatcher.find() ? itemMatcher.group(1) : null;

        return new AuctionFeedEvent(kind, chat.player(), body, amount, item);
    }

    @Override
    public String identityBase(String value) {
        if (value == null) {
            return "";
        }

        String normalized = clean(value).toLowerCase(Locale.ROOT);
        while (normalized.startsWith("~") || normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String extractCommand(String body) {
        Matcher matcher = Pattern.compile("(?<!\\S)/(?:[a-zA-Z][a-zA-Z0-9_-]*)(?:\\s+[^\\s]+)*").matcher(body);
        return matcher.find() ? matcher.group() : null;
    }
}
