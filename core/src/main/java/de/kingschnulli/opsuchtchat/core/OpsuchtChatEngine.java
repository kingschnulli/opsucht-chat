package de.kingschnulli.opsuchtchat.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Small loader-independent state holder for tab selection and unread counters.
 */
public final class OpsuchtChatEngine {
    private final OpsuchtClassifier classifier = new OpsuchtClassifier();
    private final Map<ChatCategory, Integer> unread = new EnumMap<>(ChatCategory.class);
    private ChatCategory activeCategory = ChatCategory.ALL;

    public OpsuchtChatEngine() {
        for (ChatCategory category : ChatCategory.values()) {
            unread.put(category, 0);
        }
    }

    public synchronized Classification onIncoming(ChatEnvelope message) {
        Classification classification = classifier.classify(message);
        ChatCategory category = classification.category();
        if (category != ChatCategory.ALL && category != activeCategory) {
            unread.compute(category, (ignored, current) -> current == null ? 1 : current + 1);
        }
        return classification;
    }

    public synchronized Classification classifyStateless(ChatEnvelope message) {
        return classifier.classifyStateless(message);
    }

    public synchronized void select(ChatCategory category) {
        activeCategory = category;
        unread.put(category, 0);
    }

    public synchronized ChatCategory activeCategory() {
        return activeCategory;
    }

    public synchronized int unread(ChatCategory category) {
        return unread.getOrDefault(category, 0);
    }

    public synchronized void reset() {
        activeCategory = ChatCategory.ALL;
        unread.replaceAll((ignored, value) -> 0);
        classifier.resetSessionState();
    }
}
