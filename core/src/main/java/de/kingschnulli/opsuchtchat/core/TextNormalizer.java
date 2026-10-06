package de.kingschnulli.opsuchtchat.core;

import java.text.Normalizer;
import java.util.Locale;

final class TextNormalizer {
    private TextNormalizer() {
    }

    static String clean(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
                // Some Opsucht messages contain literal legacy Minecraft formatting
                // codes inside the component text. They are visual metadata, not
                // part of the message format we want to classify.
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    static String normalize(String input) {
        return clean(input).toLowerCase(Locale.ROOT);
    }
}
