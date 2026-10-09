package de.kingschnulli.opsuchtchat.core;

import de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loader-independent state holder for tab selection, unread counters, recent
 * private conversations and a compact local PN transcript cache.
 */
public class ChatEngine {
    private static final int MAX_TRACKED_PRIVATE_CONVERSATIONS = 24;
    private static final int MAX_VISIBLE_PRIVATE_CONVERSATIONS = 8;
    private static final int MAX_MESSAGES_PER_CONVERSATION = 250;

    private final ChatServerAdapter adapter;
    private final Map<ChatCategory, Integer> unread = new EnumMap<>(ChatCategory.class);
    private final LinkedHashMap<String, ConversationState> privateConversations =
            new LinkedHashMap<>(24, 0.75f, true);
    private final Map<String, List<PrivateMessageEntry>> privateMessages = new LinkedHashMap<>();

    private ChatCategory activeCategory = ChatCategory.ALL;
    private String activePrivateKey;
    private int unattributedPrivateUnread;

    public ChatEngine(ChatServerAdapter adapter) {
        this.adapter = adapter;
        for (ChatCategory category : ChatCategory.values()) {
            unread.put(category, 0);
        }
    }

    public ChatServerAdapter adapter() {
        return adapter;
    }

    public synchronized Classification onIncoming(ChatEnvelope message) {
        Classification classification = adapter.classify(message);
        ChatCategory category = classification.category();

        if (category == ChatCategory.PRIVATE) {
            onPrivateIncoming(message.receivedAt(), classification);
        } else if (category != ChatCategory.ALL && category != activeCategory) {
            unread.compute(category, (ignored, current) -> current == null ? 1 : current + 1);
        }

        return classification;
    }

    public synchronized Classification classifyStateless(ChatEnvelope message) {
        return adapter.classifyStateless(message);
    }

    public synchronized void select(ChatCategory category) {
        activeCategory = category;

        if (category == ChatCategory.PRIVATE) {
            if (activePrivateKey == null || !privateConversations.containsKey(activePrivateKey)) {
                activePrivateKey = mostRecentConversationKey();
            }
            if (activePrivateKey != null) {
                ConversationState state = privateConversations.get(activePrivateKey);
                if (state != null) {
                    state.unread = 0;
                }
            }
            unattributedPrivateUnread = 0;
        } else {
            activePrivateKey = null;
            unread.put(category, 0);
        }
    }

    public synchronized void touchSocialConversation(
            String partner,
            String preview,
            Instant receivedAt
    ) {
        if (partner == null || partner.isBlank()) {
            return;
        }

        String key = conversationKey(partner);
        ConversationState state = privateConversations.get(key);
        if (state == null) {
            state = new ConversationState(partner.trim());
            privateConversations.put(key, state);
        } else {
            state.displayName = partner.trim();
        }

        state.preview = preview == null ? "" : preview.trim();
        state.lastMessageAt = receivedAt == null ? Instant.now() : receivedAt;

        boolean readingThisPartner = activeCategory == ChatCategory.PRIVATE && key.equals(activePrivateKey);
        if (!readingThisPartner) {
            state.unread++;
        }

        trimPrivateConversations();
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

    public synchronized void closePrivatePartner(String partner) {
        String key = conversationKey(partner);
        if (key.isEmpty()) {
            return;
        }

        privateConversations.remove(key);
        privateMessages.remove(key);

        if (key.equals(activePrivateKey)) {
            activePrivateKey = mostRecentConversationKey();
            activeCategory = ChatCategory.PRIVATE;
            if (activePrivateKey != null) {
                ConversationState state = privateConversations.get(activePrivateKey);
                if (state != null) {
                    state.unread = 0;
                }
            }
        }
    }

    public synchronized boolean togglePrivatePinned(String partner) {
        if (partner == null || partner.isBlank()) {
            return false;
        }

        String key = conversationKey(partner);
        ConversationState state = privateConversations.get(key);
        if (state == null) {
            state = new ConversationState(partner.trim());
            privateConversations.put(key, state);
        }

        state.pinned = !state.pinned;
        trimPrivateConversations();
        return state.pinned;
    }

    public synchronized void restorePinnedPrivatePartner(String partner) {
        if (partner == null || partner.isBlank()) {
            return;
        }

        String key = conversationKey(partner);
        ConversationState state = privateConversations.get(key);
        if (state == null) {
            state = new ConversationState(partner.trim());
            privateConversations.put(key, state);
        } else {
            state.displayName = partner.trim();
        }
        state.pinned = true;
    }

    public synchronized void restorePrivateMessage(PrivateMessageEntry message) {
        if (message == null || message.partner().isBlank()) {
            return;
        }

        String key = conversationKey(message.partner());
        ConversationState state = privateConversations.get(key);
        if (state == null) {
            state = new ConversationState(message.partner());
            privateConversations.put(key, state);
        }
        state.preview = message.body();
        state.lastMessageAt = message.receivedAt();
        appendPrivateMessage(key, message);
        trimPrivateConversations();
    }

    public synchronized List<String> pinnedPrivatePartners() {
        List<String> result = new ArrayList<>();
        for (ConversationState state : privateConversations.values()) {
            if (state.pinned) {
                result.add(state.displayName);
            }
        }
        return List.copyOf(result);
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

    public synchronized boolean isPrivatePinned(String partner) {
        ConversationState state = privateConversations.get(conversationKey(partner));
        return state != null && state.pinned;
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
        List<ConversationState> recent = new ArrayList<>(privateConversations.values());
        Collections.reverse(recent);

        List<PrivateConversation> result = new ArrayList<>();
        for (ConversationState state : recent) {
            if (state.pinned) {
                result.add(state.snapshot());
            }
        }
        for (ConversationState state : recent) {
            if (!state.pinned) {
                result.add(state.snapshot());
            }
        }

        if (result.size() > MAX_VISIBLE_PRIVATE_CONVERSATIONS) {
            return List.copyOf(result.subList(0, MAX_VISIBLE_PRIVATE_CONVERSATIONS));
        }
        return List.copyOf(result);
    }

    public synchronized List<PrivateMessageEntry> privateMessages(String partner) {
        String key = conversationKey(partner);
        List<PrivateMessageEntry> messages = privateMessages.get(key);
        return messages == null ? List.of() : List.copyOf(messages);
    }

    public synchronized List<PrivateMessageEntry> allPrivateMessages() {
        List<PrivateMessageEntry> result = new ArrayList<>();
        for (List<PrivateMessageEntry> messages : privateMessages.values()) {
            result.addAll(messages);
        }
        result.sort(java.util.Comparator.comparing(PrivateMessageEntry::receivedAt));
        return List.copyOf(result);
    }

    public synchronized void resetTransientState() {
        activeCategory = ChatCategory.ALL;
        activePrivateKey = null;
        unattributedPrivateUnread = 0;
        unread.replaceAll((ignored, value) -> 0);
        adapter.resetSessionState();

        for (ConversationState state : privateConversations.values()) {
            state.unread = 0;
        }
    }

    private void onPrivateIncoming(Instant receivedAt, Classification classification) {
        String partner = classification.privatePartner();
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
        } else {
            state.displayName = partner.trim();
        }

        String body = classification.privateBody();
        if (body != null && !body.isBlank()) {
            PrivateMessageEntry entry = new PrivateMessageEntry(
                    receivedAt,
                    partner.trim(),
                    classification.privateDirection() == null
                            ? PrivateMessageDirection.UNKNOWN
                            : classification.privateDirection(),
                    body.trim()
            );
            state.preview = entry.body();
            state.lastMessageAt = receivedAt;
            appendPrivateMessage(key, entry);
        }

        boolean readingThisPartner = activeCategory == ChatCategory.PRIVATE && key.equals(activePrivateKey);
        if (!readingThisPartner) {
            state.unread++;
        }

        trimPrivateConversations();
    }

    private void appendPrivateMessage(String key, PrivateMessageEntry entry) {
        List<PrivateMessageEntry> messages = privateMessages.computeIfAbsent(key, ignored -> new ArrayList<>());
        messages.add(entry);
        if (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
            messages.subList(0, messages.size() - MAX_MESSAGES_PER_CONVERSATION).clear();
        }
    }

    private String mostRecentConversationKey() {
        String mostRecent = null;
        for (String key : privateConversations.keySet()) {
            mostRecent = key;
        }
        return mostRecent;
    }

    private void trimPrivateConversations() {
        while (unpinnedCount() > MAX_TRACKED_PRIVATE_CONVERSATIONS) {
            String removable = null;
            for (Map.Entry<String, ConversationState> entry : privateConversations.entrySet()) {
                if (!entry.getValue().pinned && !entry.getKey().equals(activePrivateKey)) {
                    removable = entry.getKey();
                    break;
                }
            }
            if (removable == null) {
                break;
            }
            privateConversations.remove(removable);
            privateMessages.remove(removable);
        }
    }

    private int unpinnedCount() {
        int count = 0;
        for (ConversationState state : privateConversations.values()) {
            if (!state.pinned) {
                count++;
            }
        }
        return count;
    }

    private static String conversationKey(String partner) {
        return partner == null ? "" : partner.trim().toLowerCase(Locale.ROOT);
    }

    private static final class ConversationState {
        private String displayName;
        private int unread;
        private boolean pinned;
        private String preview = "";
        private Instant lastMessageAt = Instant.EPOCH;

        private ConversationState(String displayName) {
            this.displayName = displayName;
        }

        private PrivateConversation snapshot() {
            return new PrivateConversation(displayName, unread, pinned, preview, lastMessageAt);
        }
    }
}
