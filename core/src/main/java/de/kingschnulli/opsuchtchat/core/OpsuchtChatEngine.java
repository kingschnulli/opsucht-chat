package de.kingschnulli.opsuchtchat.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small loader-independent state holder for tab selection, unread counters and
 * recent private conversations.
 */
public final class OpsuchtChatEngine {
    private static final int MAX_TRACKED_PRIVATE_CONVERSATIONS = 12;
    private static final int MAX_VISIBLE_PRIVATE_CONVERSATIONS = 4;

    private final OpsuchtClassifier classifier = new OpsuchtClassifier();
    private final Map<ChatCategory, Integer> unread = new EnumMap<>(ChatCategory.class);
    private final LinkedHashMap<String, ConversationState> privateConversations =
            new LinkedHashMap<>(16, 0.75f, true);

    private ChatCategory activeCategory = ChatCategory.ALL;
    private String activePrivateKey;
    private int unattributedPrivateUnread;

    public OpsuchtChatEngine() {
        for (ChatCategory category : ChatCategory.values()) {
            unread.put(category, 0);
        }
    }

    public synchronized Classification onIncoming(ChatEnvelope message) {
        Classification classification = classifier.classify(message);
        ChatCategory category = classification.category();

        if (category == ChatCategory.PRIVATE) {
            onPrivateIncoming(classification.privatePartner());
        } else if (category != ChatCategory.ALL && category != activeCategory) {
            unread.compute(category, (ignored, current) -> current == null ? 1 : current + 1);
        }

        return classification;
    }

    public synchronized Classification classifyStateless(ChatEnvelope message) {
        return classifier.classifyStateless(message);
    }

    public synchronized void select(ChatCategory category) {
        activeCategory = category;
        activePrivateKey = null;
        unread.put(category, 0);

        if (category == ChatCategory.PRIVATE) {
            unattributedPrivateUnread = 0;
            for (ConversationState state : privateConversations.values()) {
                state.unread = 0;
            }
        }
    }

    public synchronized void selectPrivatePartner(String partner) {
        if (partner == null || partner.isBlank()) {
            select(ChatCategory.PRIVATE);
            return;
        }

        String key = conversationKey(partner);
        ConversationState state = privateConversations.get(key);
        if (state == null) {
            state = new ConversationState(partner.trim());
            privateConversations.put(key, state);
            trimPrivateConversations();
        } else {
            state.displayName = partner.trim();
        }

        activeCategory = ChatCategory.PRIVATE;
        activePrivateKey = key;
        state.unread = 0;
    }

    public synchronized ChatCategory activeCategory() {
        return activeCategory;
    }

    public synchronized String activePrivatePartner() {
        if (activePrivateKey == null) {
            return null;
        }
        ConversationState state = privateConversations.get(activePrivateKey);
        return state == null ? null : state.displayName;
    }

    public synchronized boolean isPrivatePartnerSelected(String partner) {
        return activeCategory == ChatCategory.PRIVATE
                && activePrivateKey != null
                && activePrivateKey.equals(conversationKey(partner));
    }

    public synchronized int unread(ChatCategory category) {
        if (category == ChatCategory.PRIVATE) {
            int total = unattributedPrivateUnread;
            for (ConversationState state : privateConversations.values()) {
                total += state.unread;
            }
            return total;
        }
        return unread.getOrDefault(category, 0);
    }

    public synchronized List<PrivateConversation> recentPrivateConversations() {
        List<PrivateConversation> result = new ArrayList<>();
        for (ConversationState state : privateConversations.values()) {
            result.add(new PrivateConversation(state.displayName, state.unread));
        }
        Collections.reverse(result);
        if (result.size() > MAX_VISIBLE_PRIVATE_CONVERSATIONS) {
            return List.copyOf(result.subList(0, MAX_VISIBLE_PRIVATE_CONVERSATIONS));
        }
        return List.copyOf(result);
    }

    public synchronized void reset() {
        activeCategory = ChatCategory.ALL;
        activePrivateKey = null;
        unattributedPrivateUnread = 0;
        unread.replaceAll((ignored, value) -> 0);
        privateConversations.clear();
        classifier.resetSessionState();
    }

    private void onPrivateIncoming(String partner) {
        if (partner == null || partner.isBlank()) {
            if (activeCategory != ChatCategory.PRIVATE || activePrivateKey != null) {
                unattributedPrivateUnread++;
            }
            return;
        }

        String key = conversationKey(partner);
        ConversationState state = privateConversations.get(key);
        if (state == null) {
            state = new ConversationState(partner.trim());
            privateConversations.put(key, state);
            trimPrivateConversations();
        } else {
            state.displayName = partner.trim();
        }

        boolean readingAllPrivate = activeCategory == ChatCategory.PRIVATE && activePrivateKey == null;
        boolean readingThisPartner = activeCategory == ChatCategory.PRIVATE && key.equals(activePrivateKey);
        if (!readingAllPrivate && !readingThisPartner) {
            state.unread++;
        }
    }

    private void trimPrivateConversations() {
        while (privateConversations.size() > MAX_TRACKED_PRIVATE_CONVERSATIONS) {
            String eldest = privateConversations.keySet().iterator().next();
            if (eldest.equals(activePrivateKey) && privateConversations.size() > 1) {
                // Touch active conversation and drop the next oldest instead.
                ConversationState active = privateConversations.remove(eldest);
                privateConversations.put(eldest, active);
                eldest = privateConversations.keySet().iterator().next();
            }
            privateConversations.remove(eldest);
        }
    }

    private static String conversationKey(String partner) {
        return partner == null ? "" : partner.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ConversationState {
        private String displayName;
        private int unread;

        private ConversationState(String displayName) {
            this.displayName = displayName;
        }
    }
}
