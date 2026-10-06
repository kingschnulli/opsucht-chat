package de.kingschnulli.opsuchtchat.core.server.opsucht;

import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.OpsuchtClassifier;
import de.kingschnulli.opsuchtchat.core.OpsuchtHost;
import de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpsuchtServerAdapter implements ChatServerAdapter {
    private static final Pattern PUBLIC_PLAYER = Pattern.compile(
            "(?m)(?:^|\\n)[^|\\n]+\\|\\s*(.+?)\\s*»"
    );

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
    public String extractPublicPlayerName(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = PUBLIC_PLAYER.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1)
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .trim();

        return value.matches("[A-Za-z0-9_.~-]{1,32}") ? value : null;
    }

    @Override
    public String identityBase(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .trim()
                .toLowerCase(Locale.ROOT);

        while (normalized.startsWith("~") || normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }

        return normalized;
    }
}
