package de.kingschnulli.opsuchtchat.minecraft;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.ChatEngine;
import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.ChatSource;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.DebugCapture;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import de.kingschnulli.opsuchtchat.core.PrivateMessageDirection;
import de.kingschnulli.opsuchtchat.core.PrivateMessageEntry;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionSessionSnapshot;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionSessionTracker;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.TextRange;
import de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter;
import de.kingschnulli.opsuchtchat.core.server.PlayerActionMode;
import de.kingschnulli.opsuchtchat.core.server.PlayerActionSpec;
import de.kingschnulli.opsuchtchat.core.server.ServerAdapterRegistry;
import de.kingschnulli.opsuchtchat.core.server.ServerCommandMode;
import de.kingschnulli.opsuchtchat.core.server.ServerCommandSpec;
import de.kingschnulli.opsuchtchat.core.server.ServerHubPage;
import de.kingschnulli.opsuchtchat.core.social.LocalSocialStore;
import de.kingschnulli.opsuchtchat.core.social.PlayerIdentity;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

public final class OpsuchtChatMinecraft {
    private static final String OPEN_PRIVATE_PREFIX = "/opschat pn ";

    private static final int FRAME_X = 5;
    private static final int FEED_FRAME_MAX_WIDTH = 250;
    private static final int FEED_FRAME_MIN_WIDTH = 185;
    private static final int SOCIAL_FRAME_MAX_WIDTH = 420;
    private static final int SOCIAL_FRAME_MIN_WIDTH = 300;
    private static final int SIDEBAR_MAX_WIDTH = 132;
    private static final int SIDEBAR_MIN_WIDTH = 104;
    private static final int MESSAGE_BOTTOM_GAP = 42;
    private static final int MAX_FEED_MESSAGES = 800;

    private static final Map<GuiMessage, Classification> CLASSIFICATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<ChatViewMessage> FEED =
            Collections.synchronizedList(new ArrayList<>());
    private static final AuctionSessionTracker AUCTION_TRACKER = new AuctionSessionTracker();

    private static ChatEngine engine;
    private static ChatServerAdapter adapter;
    private static LocalSocialStore socialStore;
    private static String activeServerAddress;
    private static DebugCapture debugCapture;
    private static InteractionDebugCapture interactionDebugCapture;
    private static Map<String, String> aliasCache = Map.of();
    private static final Map<String, String> observedPublicAliases = new HashMap<>();
    private static final Set<String> importantMessageKeys = new HashSet<>();
    private static final Set<String> serverCommandFavorites = new HashSet<>();

    private OpsuchtChatMinecraft() {
    }

    public static void bootstrap() {
        ensureServerState();
        installFilter();
    }

    public static void installFilter() {
        ensureServerState();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null && minecraft.gui.hud != null) {
            Predicate<GuiMessage> vanilla = vanillaFilter();
            if (!isActive()) {
                minecraft.gui.hud.getChat().setVisibleMessageFilter(vanilla);
                return;
            }
            minecraft.gui.hud.getChat().setVisibleMessageFilter(
                    message -> vanilla.test(message) && isVisibleInSelectedTab(message)
            );
        }
    }

    public static boolean isActive() {
        ensureServerState();
        return adapter != null && engine != null;
    }

    public static boolean isChatFrameActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return isActive()
                && minecraft != null
                && minecraft.gui != null
                && minecraft.gui.screen() instanceof ChatScreen;
    }

    public static String adapterId() {
        ensureServerState();
        return adapter == null ? "unknown" : adapter.id();
    }

    public static int frameX() {
        return FRAME_X;
    }

    public static int frameWidth() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            boolean workspace = activeCategory() == ChatCategory.PRIVATE || activeCategory() == ChatCategory.SERVER;
            return workspace ? SOCIAL_FRAME_MAX_WIDTH : FEED_FRAME_MAX_WIDTH;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        boolean workspace = activeCategory() == ChatCategory.PRIVATE || activeCategory() == ChatCategory.SERVER;
        int preferred = (int)Math.round(screenWidth * (workspace ? 0.58 : 0.36));
        int min = workspace ? SOCIAL_FRAME_MIN_WIDTH : FEED_FRAME_MIN_WIDTH;
        int max = workspace ? SOCIAL_FRAME_MAX_WIDTH : FEED_FRAME_MAX_WIDTH;
        preferred = Math.max(min, Math.min(max, preferred));
        return Math.max(170, Math.min(screenWidth - FRAME_X * 2, preferred));
    }

    public static int messageBottom() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return 0;
        }
        return minecraft.getWindow().getGuiScaledHeight() - MESSAGE_BOTTOM_GAP;
    }

    public static int messageHeightPixels() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return 120;
        }

        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        boolean workspace = activeCategory() == ChatCategory.PRIVATE || activeCategory() == ChatCategory.SERVER;
        int target = (int)Math.round(screenHeight * (workspace ? 0.56 : 0.38));
        int max = Math.max(78, Math.min(workspace ? 190 : 125, screenHeight - 92));
        return Math.max(78, Math.min(max, target));
    }

    public static int frameTop() {
        return messageBottom() - messageHeightPixels() - 3;
    }

    public static int frameBottom() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 0 : minecraft.getWindow().getGuiScaledHeight() - 2;
    }

    public static int sidebarWidth() {
        if (activeCategory() != ChatCategory.PRIVATE) {
            return 0;
        }

        int frameWidth = frameWidth();
        return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, (int)Math.round(frameWidth * 0.34)));
    }

    public static int serverSidebarWidth() {
        if (activeCategory() != ChatCategory.SERVER) {
            return 0;
        }

        int frameWidth = frameWidth();
        return Math.min(118, Math.max(92, (int)Math.round(frameWidth * 0.28)));
    }

    public static int chatRenderOffsetX() {
        return FRAME_X + 3;
    }

    public static int chatContentWidthLogical() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return 320;
        }

        double scale = Math.max(0.1, minecraft.options.chatScale().get());
        int contentPixels = frameWidth() - 12;
        return Math.max(40, (int)Math.floor(contentPixels / scale));
    }

    public static int chatContentHeightLogical() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return 180;
        }

        double scale = Math.max(0.1, minecraft.options.chatScale().get());
        return Math.max(20, (int)Math.floor(messageHeightPixels() / scale));
    }

    public static boolean allowedByVanilla(GuiMessage message) {
        return vanillaFilter().test(message);
    }

    public static Component decorateClickablePlayerName(Component contents, GuiMessageSource source) {
        if (!isActive() || source != GuiMessageSource.PLAYER) {
            return contents;
        }

        String plain = contents.getString();
        String player = adapter.extractPublicPlayerName(plain);
        if (player == null) {
            return contents;
        }

        observedPublicAliases.put(serverIdentityBase(player), player);
        String privateTarget = resolvePrivateTarget(player);

        TextRange playerRange = adapter.publicPlayerRange(plain);
        if (playerRange == null) {
            return contents;
        }
        int targetStart = playerRange.start();
        int targetEnd = playerRange.end();
        MutableComponent result = Component.empty();
        int cursor = 0;

        for (Component part : contents.toFlatList()) {
            String text = part.getString();
            int partStart = cursor;
            int partEnd = cursor + text.length();

            if (partEnd <= targetStart || partStart >= targetEnd) {
                result.append(Component.literal(text).withStyle(part.getStyle()));
            } else {
                int localStart = Math.max(0, targetStart - partStart);
                int localEnd = Math.min(text.length(), targetEnd - partStart);

                if (localStart > 0) {
                    result.append(Component.literal(text.substring(0, localStart)).withStyle(part.getStyle()));
                }

                Style playerStyle = part.getStyle()
                        .withClickEvent(new ClickEvent.SuggestCommand(OPEN_PRIVATE_PREFIX + privateTarget))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                                privateTarget.equals(player)
                                        ? "PN mit " + player + " öffnen"
                                        : "PN mit " + privateTarget + " öffnen (" + player + ")"
                        )));
                result.append(Component.literal(text.substring(localStart, localEnd)).withStyle(playerStyle));

                if (localEnd < text.length()) {
                    result.append(Component.literal(text.substring(localEnd)).withStyle(part.getStyle()));
                }
            }

            cursor = partEnd;
        }

        return cursor == plain.length() ? result : contents;
    }

    public static boolean handlePlayerNameClick(Style style) {
        if (!isActive() || style == null) {
            return false;
        }

        ClickEvent event = style.getClickEvent();
        if (event instanceof ClickEvent.SuggestCommand suggest
                && suggest.command().startsWith(OPEN_PRIVATE_PREFIX)) {
            String player = suggest.command().substring(OPEN_PRIVATE_PREFIX.length()).trim();
            if (!player.isEmpty()) {
                selectPrivatePartner(resolvePrivateTarget(player));
                return true;
            }
        }

        return false;
    }

    public static void observe(GuiMessage message) {
        ensureServerState();
        if (engine == null) {
            return;
        }

        ChatEnvelope envelope = envelope(message);
        Classification classification = engine.onIncoming(envelope);
        CLASSIFICATIONS.put(message, classification);
        debugCapture().append(envelope, classification);
        if (debugCapture().enabled()) {
            interactionDebugCapture().append(message);
        }

        PublicChatLine publicChat = adapter.parsePublicChat(message.content().getString());
        ServerFeedEvent serverEvent = classification.category() == ChatCategory.SERVER
                ? adapter.parseServerEvent(message.content().getString())
                : null;
        AuctionFeedEvent auctionEvent = classification.category() == ChatCategory.AUCTION
                ? adapter.parseAuctionEvent(message.content().getString())
                : null;
        if (auctionEvent != null) {
            AUCTION_TRACKER.accept(auctionEvent);
        }

        FEED.add(new ChatViewMessage(
                envelope.receivedAt(),
                message,
                classification,
                publicChat,
                serverEvent,
                auctionEvent
        ));
        trimFeed();

        if (classification.category() == ChatCategory.PRIVATE && classification.privatePartner() != null) {
            String partner = classification.privatePartner();
            learnAliasesForCanonical(partner);
            if (socialStore != null) {
                socialStore.setConversationClosed(partner, false);
                if (isStoredFavorite(partner)) {
                    engine.restorePinnedPrivatePartner(partner);
                }
            }
        }

        if (socialStore != null
                && classification.category() == ChatCategory.PRIVATE
                && classification.privatePartner() != null
                && classification.privateBody() != null
                && !classification.privateBody().isBlank()) {
            socialStore.appendPrivateMessage(new PrivateMessageEntry(
                    envelope.receivedAt(),
                    classification.privatePartner(),
                    classification.privateDirection() == null
                            ? PrivateMessageDirection.UNKNOWN
                            : classification.privateDirection(),
                    classification.privateBody()
            ));
        }
    }

    public static boolean isVisibleInSelectedTab(GuiMessage message) {
        ensureServerState();
        if (engine == null) {
            return true;
        }

        ChatCategory active = engine.activeCategory();
        if (active == ChatCategory.ALL) {
            return true;
        }

        Classification classification = CLASSIFICATIONS.get(message);
        if (classification == null) {
            classification = engine.classifyStateless(envelope(message));
            CLASSIFICATIONS.put(message, classification);
        }

        if (active == ChatCategory.PRIVATE) {
            if (classification.category() != ChatCategory.PRIVATE) {
                return false;
            }
            String selectedPartner = engine.activePrivatePartner();
            return selectedPartner == null
                    || selectedPartner.equalsIgnoreCase(classification.privatePartner());
        }

        return classification.category() == active;
    }

    public static void select(ChatCategory category) {
        if (!isActive()) {
            return;
        }
        engine.select(category);
        refreshChatView();
    }

    public static void selectPrivatePartner(String partner) {
        if (!isActive()) {
            return;
        }

        String target = resolvePrivateTarget(partner);
        if (socialStore != null) {
            socialStore.setConversationClosed(target, false);

            if (engine.privateMessages(target).isEmpty()) {
                for (PrivateMessageEntry message : socialStore.loadPrivateMessagesForPartner(target, 250)) {
                    engine.restorePrivateMessage(message);
                }
            }

            if (isStoredFavorite(target)) {
                engine.restorePinnedPrivatePartner(target);
            }
        }

        engine.selectPrivatePartner(target);
        refreshChatView();
    }

    public static void closePrivatePartner(String partner) {
        if (!isActive()) {
            return;
        }

        String target = resolvePrivateTarget(partner);
        if (socialStore != null) {
            socialStore.setConversationClosed(target, true);
        }
        engine.closePrivatePartner(target);
        refreshChatView();
    }

    public static boolean togglePrivatePinned(String partner) {
        if (!isActive()) {
            return false;
        }
        boolean pinned = engine.togglePrivatePinned(partner);
        saveFavorites();
        return pinned;
    }

    public static ChatCategory activeCategory() {
        ensureServerState();
        return engine == null ? ChatCategory.ALL : engine.activeCategory();
    }

    public static String activePrivatePartner() {
        ensureServerState();
        return engine == null ? null : engine.activePrivatePartner();
    }

    public static boolean isCategorySelected(ChatCategory category) {
        return engine != null && engine.activeCategory() == category;
    }

    public static boolean isPrivatePartnerSelected(String partner) {
        return engine != null && engine.isPrivatePartnerSelected(partner);
    }

    public static int unread(ChatCategory category) {
        return engine == null ? 0 : engine.unread(category);
    }

    public static List<PrivateConversation> recentPrivateConversations() {
        return engine == null ? List.of() : engine.recentPrivateConversations();
    }

    public static List<PrivateMessageEntry> privateMessages(String partner) {
        return engine == null || partner == null ? List.of() : engine.privateMessages(partner);
    }

    public static List<PrivateMessageEntry> importantPrivateMessages() {
        if (engine == null || socialStore == null) {
            return List.of();
        }

        return engine.allPrivateMessages().stream()
                .filter(OpsuchtChatMinecraft::isImportant)
                .sorted(Comparator.comparing(PrivateMessageEntry::receivedAt))
                .toList();
    }

    public static List<ChatViewMessage> visibleFeedMessages() {
        if (engine == null) {
            return List.of();
        }

        ChatCategory active = engine.activeCategory();
        synchronized (FEED) {
            if (active == ChatCategory.ALL) {
                return List.copyOf(FEED);
            }
            return FEED.stream()
                    .filter(entry -> entry.classification().category() == active)
                    .toList();
        }
    }

    public static List<ServerHubPage> serverHubPages() {
        ensureServerState();
        return adapter == null ? List.of() : adapter.serverHubPages();
    }

    public static List<ServerCommandSpec> serverCommandsForPage(String pageId, String query) {
        ensureServerState();
        if (adapter == null) {
            return List.of();
        }

        String normalizedPage = pageId == null ? "" : pageId.trim();
        String normalizedQuery = query == null ? "" : query.trim();

        if (!normalizedQuery.isBlank()) {
            return adapter.serverHubPages().stream()
                    .flatMap(page -> page.commands().stream())
                    .filter(command -> command.matches(normalizedQuery))
                    .distinct()
                    .toList();
        }

        return adapter.serverHubPages().stream()
                .filter(page -> page.id().equals(normalizedPage))
                .findFirst()
                .map(ServerHubPage::commands)
                .orElse(List.of());
    }

    public static List<ServerCommandSpec> favoriteServerCommands() {
        ensureServerState();
        if (adapter == null) {
            return List.of();
        }

        Map<String, ServerCommandSpec> byId = new java.util.LinkedHashMap<>();
        for (ServerHubPage page : adapter.serverHubPages()) {
            for (ServerCommandSpec command : page.commands()) {
                byId.putIfAbsent(command.id(), command);
            }
        }

        return byId.values().stream()
                .filter(command -> serverCommandFavorites.contains(command.id()))
                .toList();
    }

    public static boolean isServerCommandFavorite(String id) {
        return id != null && serverCommandFavorites.contains(id);
    }

    public static boolean toggleServerCommandFavorite(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        boolean favorite;
        if (serverCommandFavorites.contains(id)) {
            serverCommandFavorites.remove(id);
            favorite = false;
        } else {
            serverCommandFavorites.add(id);
            favorite = true;
        }

        if (socialStore != null) {
            socialStore.saveCommandFavorites(serverCommandFavorites);
        }
        return favorite;
    }

    public static boolean executeServerCommand(ServerCommandSpec command) {
        if (!isActive() || command == null || command.mode() != ServerCommandMode.RUN) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }

        minecraft.player.connection.sendCommand(command.command());
        return true;
    }

    public static String prefillServerCommand(ServerCommandSpec command) {
        if (command == null) {
            return "";
        }
        return "/" + command.command() + " ";
    }

    public static String resolvedPrivateTarget(String displayedName) {
        return resolvePrivateTarget(displayedName);
    }

    public static Component publicBodyComponent(ChatViewMessage entry) {
        if (entry == null || adapter == null || entry.publicChat() == null) {
            return entry == null ? Component.empty() : entry.message().content();
        }

        String plain = entry.message().content().getString();
        TextRange range = adapter.publicBodyRange(plain);
        if (range == null) {
            return Component.literal(entry.publicChat().body());
        }

        return sliceComponent(entry.message().content(), range.start(), range.end());
    }

    public static Component serverBodyComponent(ChatViewMessage entry) {
        if (entry == null || adapter == null || entry.serverEvent() == null) {
            return entry == null ? Component.empty() : entry.message().content();
        }

        String plain = entry.message().content().getString();
        TextRange range = adapter.serverBodyRange(plain);
        if (range == null) {
            // Prefer the original component over a literal fallback so server-provided
            // click/hover actions can never silently disappear in a generic adapter.
            return entry.message().content();
        }

        return sliceComponent(entry.message().content(), range.start(), range.end());
    }

    public static PlayerIdentity playerIdentity(String displayedName) {
        if (displayedName == null || displayedName.isBlank()) {
            return null;
        }

        String canonical = resolvePrivateTarget(displayedName);
        PlayerInfo info = playerInfo(canonical);
        String uuid = info == null ? null : info.getProfile().id().toString();

        List<String> aliases = new ArrayList<>();
        for (Map.Entry<String, String> entry : aliasCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(canonical)) {
                aliases.add(entry.getKey());
            }
        }
        if (!displayedName.equalsIgnoreCase(canonical)
                && aliases.stream().noneMatch(alias -> alias.equalsIgnoreCase(displayedName))) {
            aliases.add(displayedName);
        }

        return new PlayerIdentity(
                displayedName,
                canonical,
                uuid,
                info != null,
                aliases
        );
    }

    public static List<PlayerActionSpec> playerActions(String displayedName) {
        ensureServerState();
        if (adapter == null || displayedName == null || displayedName.isBlank()) {
            return List.of();
        }
        return adapter.playerActions(resolvePrivateTarget(displayedName));
    }

    public static boolean executePlayerAction(PlayerActionSpec action) {
        if (!isActive() || action == null || action.mode() != PlayerActionMode.RUN) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }

        String command = action.command();
        if (command == null || command.isBlank()) {
            return false;
        }

        minecraft.player.connection.sendCommand(command);
        return true;
    }

    public static String prefillPlayerAction(PlayerActionSpec action) {
        if (action == null || action.command() == null) {
            return "";
        }
        return "/" + action.command() + (action.command().endsWith(" ") ? "" : " ");
    }

    public static List<ServerClickAction> serverClickActions(ChatViewMessage entry) {
        if (entry == null || adapter == null || entry.serverEvent() == null) {
            return List.of();
        }

        Map<ClickEvent, StringBuilder> labels = new LinkedHashMap<>();
        for (Component part : entry.message().content().toFlatList()) {
            ClickEvent click = part.getStyle().getClickEvent();
            String text = part.getString();
            if (click == null || text == null || text.isBlank()) {
                continue;
            }
            labels.computeIfAbsent(click, ignored -> new StringBuilder()).append(text);
        }

        List<ServerClickAction> result = new ArrayList<>();
        for (Map.Entry<ClickEvent, StringBuilder> clickable : labels.entrySet()) {
            String label = adapter.serverClickActionLabel(
                    entry.message().content().getString(),
                    clickable.getValue().toString().trim()
            );
            if (label == null || label.isBlank()) {
                label = "Aktion";
            }
            result.add(new ServerClickAction(label, clickable.getKey()));
        }
        return List.copyOf(result);
    }

    public static AuctionSessionSnapshot auctionSession() {
        return AUCTION_TRACKER.snapshot();
    }

    public static PlayerSkin playerSkin(String playerName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || playerName == null || playerName.isBlank()) {
            return null;
        }

        String target = resolvePrivateTarget(playerName);
        PlayerInfo online = playerInfo(target);
        if (online != null) {
            return online.getSkin();
        }

        try {
            return minecraft.playerSkinRenderCache()
                    .getOrDefault(ResolvableProfile.createUnresolved(target))
                    .playerSkin();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static PlayerInfo playerInfo(String playerName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null || playerName == null) {
            return null;
        }

        String target = resolvePrivateTarget(playerName);
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String profileName = info.getProfile().name();
            if (profileName.equalsIgnoreCase(target)) {
                return info;
            }
        }

        String base = serverIdentityBase(target);
        PlayerInfo match = null;
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (serverIdentityBase(info.getProfile().name()).equals(base)) {
                if (match != null) {
                    return null;
                }
                match = info;
            }
        }
        return match;
    }

    public static boolean isImportant(PrivateMessageEntry message) {
        return message != null
                && importantMessageKeys.contains(LocalSocialStore.privateMessageKey(message));
    }

    public static boolean setImportant(PrivateMessageEntry message, boolean important) {
        if (socialStore == null || message == null) {
            return false;
        }

        boolean stored = socialStore.setImportant(message, important);
        String key = LocalSocialStore.privateMessageKey(message);
        if (stored) {
            importantMessageKeys.add(key);
        } else {
            importantMessageKeys.remove(key);
        }
        return stored;
    }

    public static String tabLabel(ChatCategory category) {
        String label = category.label();
        int unread = unread(category);
        if (category == ChatCategory.PRIVATE && unread > 0) {
            label += " [" + compactUnread(unread) + "]";
        }
        return label;
    }

    public static String privateTabLabel(PrivateConversation conversation) {
        String label = shortenPlayerName(conversation.name(), 12);
        if (conversation.unread() > 0) {
            label += " [" + compactUnread(conversation.unread()) + "]";
        }
        return label;
    }

    public static String inputContextLabel() {
        String partner = activePrivatePartner();
        if (partner != null) {
            return "An: " + shortenPlayerName(partner, 13);
        }

        return switch (activeCategory()) {
            case ALL -> "ALL";
            case MESSAGE -> "MSG";
            case PRIVATE -> "PN";
            case AUCTION -> "AUKTION";
            case SERVER -> "SERVER";
            case ADVERTISING -> "WERBUNG";
        };
    }

    public static void onChatCleared() {
        CLASSIFICATIONS.clear();
        FEED.clear();
        AUCTION_TRACKER.reset();
        if (engine != null) {
            engine.resetTransientState();
        }
    }

    public static String paymentPrefix(String partner) {
        if (!isActive() || partner == null || partner.isBlank()) {
            return "/pay ";
        }
        return "/pay " + resolvePrivateTarget(partner) + " ";
    }

    public static boolean handlePaymentInput(String partner, String amount, boolean addToRecent) {
        if (!isActive() || partner == null || amount == null) {
            return false;
        }

        String value = amount.trim();
        if (value.isEmpty() || value.startsWith("/")) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }

        String target = resolvePrivateTarget(partner);
        String command = adapter.paymentCommand(target, value);
        if (command == null || command.isBlank()) {
            return false;
        }

        if (addToRecent && minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.getChat().addRecentChat("/" + command);
        }

        minecraft.player.connection.sendCommand(command);
        return true;
    }

    public static boolean requestFriend(String partner) {
        if (!isActive() || partner == null || partner.isBlank()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }

        String target = resolvePrivateTarget(partner);
        String command = adapter.friendAddCommand(target);
        if (command == null || command.isBlank()) {
            return false;
        }

        minecraft.player.connection.sendCommand(command);
        return true;
    }

    public static boolean handleActivePrivateInput(String input, boolean addToRecent) {
        if (!isActive()) {
            return false;
        }

        String partner = engine.activePrivatePartner();
        if (partner == null || input == null) {
            return false;
        }

        String message = input.trim();
        if (message.isEmpty() || message.startsWith("/")) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return false;
        }

        if (addToRecent && minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.getChat().addRecentChat(input);
        }

        minecraft.player.connection.sendCommand(adapter.privateMessageCommand(partner, message));
        return true;
    }

    public static boolean handleLocalCommand(String input) {
        String command = input == null ? "" : input.trim();
        if (!command.equalsIgnoreCase("/opschat") && !command.toLowerCase(Locale.ROOT).startsWith("/opschat ")) {
            return false;
        }

        if (!isActive()) {
            showOverlay("Opsucht Chat: kein unterstützter Server erkannt.");
            return true;
        }

        String[] parts = command.split("\\s+");
        if (parts.length == 1 || (parts.length >= 2 && parts[1].equalsIgnoreCase("help"))) {
            showOverlay("Opsucht Chat: /opschat debug | /opschat tab <all|msg|pn|auktion|server|werbung>");
            return true;
        }

        if (parts[1].equalsIgnoreCase("debug")) {
            boolean enabled = debugCapture().toggle();
            String file = debugCapture().outputFile().toAbsolutePath().toString();
            showOverlay(enabled
                    ? "Opsucht Chat Debug AN: " + file + " + debug-interactions.jsonl"
                    : "Opsucht Chat Debug AUS");
            return true;
        }

        if (parts[1].equalsIgnoreCase("pn") && parts.length >= 3) {
            selectPrivatePartner(parts[2]);
            return true;
        }

        if (parts[1].equalsIgnoreCase("tab") && parts.length >= 3) {
            ChatCategory category = parseCategory(parts[2]);
            if (category != null) {
                select(category);
                showOverlay("Opsucht Chat: " + category.label());
            } else {
                showOverlay("Unbekannter Tab. all | msg | pn | auktion | server | werbung");
            }
            return true;
        }

        showOverlay("Opsucht Chat: /opschat debug | /opschat tab <...>");
        return true;
    }

    private static ChatCategory parseCategory(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "all" -> ChatCategory.ALL;
            case "msg", "message", "chat" -> ChatCategory.MESSAGE;
            case "pn", "pm", "private", "privat" -> ChatCategory.PRIVATE;
            case "auktion", "auction" -> ChatCategory.AUCTION;
            case "server", "system" -> ChatCategory.SERVER;
            case "werbung", "ads", "advertising" -> ChatCategory.ADVERTISING;
            default -> null;
        };
    }

    private static ChatEnvelope envelope(GuiMessage message) {
        return new ChatEnvelope(
                Instant.now(),
                mapSource(message.source()),
                message.content().getString()
        );
    }

    private static ChatSource mapSource(GuiMessageSource source) {
        if (source == null) {
            return ChatSource.UNKNOWN;
        }
        return switch (source) {
            case PLAYER -> ChatSource.PLAYER;
            case SYSTEM_SERVER -> ChatSource.SERVER_SYSTEM;
            case SYSTEM_CLIENT -> ChatSource.CLIENT_SYSTEM;
        };
    }

    private static Predicate<GuiMessage> vanillaFilter() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            return minecraft.player.chatAbilities().visibleMessagesFilter();
        }
        return ignored -> true;
    }

    private static DebugCapture debugCapture() {
        if (debugCapture == null) {
            debugCapture = new DebugCapture(rootDataDir().resolve("debug-chat.jsonl"));
        }
        return debugCapture;
    }

    private static InteractionDebugCapture interactionDebugCapture() {
        if (interactionDebugCapture == null) {
            interactionDebugCapture = new InteractionDebugCapture(
                    rootDataDir().resolve("debug-interactions.jsonl")
            );
        }
        return interactionDebugCapture;
    }

    private static Path rootDataDir() {
        Minecraft minecraft = Minecraft.getInstance();
        Path root = minecraft != null && minecraft.gameDirectory != null
                ? minecraft.gameDirectory.toPath()
                : Path.of(".");
        return root.resolve("opsucht-chat");
    }

    private static void ensureServerState() {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData server = minecraft == null ? null : minecraft.getCurrentServer();
        String address = server == null ? null : server.ip;

        if (Objects.equals(activeServerAddress, address) && (address == null || adapter != null || engine == null)) {
            return;
        }

        activeServerAddress = address;
        CLASSIFICATIONS.clear();
        FEED.clear();
        AUCTION_TRACKER.reset();

        adapter = ServerAdapterRegistry.resolve(address);
        engine = adapter == null ? null : new ChatEngine(adapter);
        socialStore = null;
        importantMessageKeys.clear();
        serverCommandFavorites.clear();

        if (adapter == null || engine == null) {
            return;
        }

        socialStore = new LocalSocialStore(rootDataDir(), adapter.id());
        aliasCache = socialStore.loadAliases();
        observedPublicAliases.clear();
        importantMessageKeys.addAll(socialStore.loadImportantMessageKeys());

        if (socialStore.hasCommandFavoritesFile()) {
            serverCommandFavorites.addAll(socialStore.loadCommandFavorites());
        } else {
            serverCommandFavorites.addAll(adapter.defaultServerCommandFavorites());
            socialStore.saveCommandFavorites(serverCommandFavorites);
        }

        if ("opsucht".equals(adapter.id())) {
            socialStore.importLegacyFavorites(rootDataDir().resolve("pinned-pn.txt"));
        }

        Set<String> closedConversations = socialStore.loadClosedConversations();

        for (String favorite : socialStore.loadFavorites()) {
            if (!closedConversations.contains(favorite.trim().toLowerCase(Locale.ROOT))) {
                engine.restorePinnedPrivatePartner(favorite);
            }
        }

        for (PrivateMessageEntry message : socialStore.loadPrivateMessages(1_000)) {
            if (!closedConversations.contains(message.partner().trim().toLowerCase(Locale.ROOT))) {
                engine.restorePrivateMessage(message);
            }
        }
    }

    private static String resolvePrivateTarget(String displayedName) {
        if (displayedName == null || displayedName.isBlank()) {
            return displayedName;
        }

        String trimmed = displayedName.trim();
        String persisted = aliasCache.get(trimmed.toLowerCase(Locale.ROOT));
        if (persisted != null && !persisted.isBlank()) {
            return persisted;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getConnection() != null) {
            String base = serverIdentityBase(trimmed);
            String candidate = null;

            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                String profileName = info.getProfile().name();
                if (profileName.equalsIgnoreCase(trimmed)) {
                    return profileName;
                }
                if (serverIdentityBase(profileName).equals(base)) {
                    if (candidate != null && !candidate.equalsIgnoreCase(profileName)) {
                        candidate = null;
                        break;
                    }
                    candidate = profileName;
                }
            }

            if (candidate != null) {
                rememberAlias(trimmed, candidate);
                return candidate;
            }
        }

        if (engine != null) {
            String base = serverIdentityBase(trimmed);
            String candidate = null;
            for (PrivateConversation conversation : engine.recentPrivateConversations()) {
                if (serverIdentityBase(conversation.name()).equals(base)) {
                    if (candidate != null && !candidate.equalsIgnoreCase(conversation.name())) {
                        return trimmed;
                    }
                    candidate = conversation.name();
                }
            }
            if (candidate != null) {
                rememberAlias(trimmed, candidate);
                return candidate;
            }
        }

        return trimmed;
    }

    private static void learnAliasesForCanonical(String canonical) {
        String base = serverIdentityBase(canonical);
        String alias = observedPublicAliases.get(base);
        if (alias != null && !alias.equalsIgnoreCase(canonical)) {
            rememberAlias(alias, canonical);
        }
    }

    private static void rememberAlias(String alias, String canonical) {
        if (alias == null || canonical == null || alias.equalsIgnoreCase(canonical)) {
            return;
        }

        Map<String, String> updated = new HashMap<>(aliasCache);
        updated.put(alias.trim().toLowerCase(Locale.ROOT), canonical.trim());
        aliasCache = Map.copyOf(updated);

        if (socialStore != null) {
            socialStore.saveAlias(alias, canonical);
        }
    }

    private static String serverIdentityBase(String value) {
        return adapter == null ? (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) : adapter.identityBase(value);
    }

    private static Component sliceComponent(Component source, int start, int end) {
        if (start < 0 || end < start) {
            return source;
        }

        MutableComponent result = Component.empty();
        int cursor = 0;

        for (Component part : source.toFlatList()) {
            String text = part.getString();
            int partStart = cursor;
            int partEnd = cursor + text.length();

            if (partEnd <= start || partStart >= end) {
                cursor = partEnd;
                continue;
            }

            int localStart = Math.max(0, start - partStart);
            int localEnd = Math.min(text.length(), end - partStart);
            if (localEnd > localStart) {
                result.append(Component.literal(text.substring(localStart, localEnd)).withStyle(part.getStyle()));
            }

            cursor = partEnd;
        }

        return result.getString().isEmpty() ? source : result;
    }

    private static void trimFeed() {
        synchronized (FEED) {
            int overflow = FEED.size() - MAX_FEED_MESSAGES;
            if (overflow > 0) {
                FEED.subList(0, overflow).clear();
            }
        }
    }

    private static boolean isStoredFavorite(String partner) {
        if (socialStore == null || partner == null) {
            return false;
        }

        return socialStore.loadFavorites().stream()
                .anyMatch(value -> value.equalsIgnoreCase(partner));
    }

    private static void saveFavorites() {
        if (socialStore != null && engine != null) {
            socialStore.saveFavorites(engine.pinnedPrivatePartners());
        }
    }

    private static void refreshChatView() {
        installFilter();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.getChat().resetChatScroll();
        }
    }

    private static String compactUnread(int unread) {
        return unread > 99 ? "99+" : Integer.toString(unread);
    }

    private static String shortenPlayerName(String name, int maxChars) {
        if (name == null || name.length() <= maxChars) {
            return name == null ? "?" : name;
        }
        if (maxChars <= 1) {
            return "…";
        }
        return name.substring(0, maxChars - 1) + "…";
    }

    private static void showOverlay(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gui != null && minecraft.gui.hud != null) {
            minecraft.gui.hud.setOverlayMessage(Component.literal(text), false);
        }
    }
}
