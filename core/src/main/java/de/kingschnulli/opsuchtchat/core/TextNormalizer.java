package de.kingschnulli.opsuchtchat.core;

import java.text.Normalizer;
import java.util.Locale;

final class TextNormalizer {
    private TextNormalizer() {
    }

    static String normalize(String input) {
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\uFEFF", "")
                .trim()
                .replaceAll("\\s+", " ");
        return value.toLowerCase(Locale.ROOT);
    }
}
