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
import de.kingschnulli.opsuchtchat.core.server.ChatServerAdapter;
import de.kingschnulli.opsuchtchat.core.server.ServerAdapterRegistry;
import de.kingschnulli.opsuchtchat.core.social.LocalSocialStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

public final class OpsuchtChatMinecraft {
    private static final String OPEN_PRIVATE_PREFIX = "/opschat pn ";
    private static final Pattern PLAYER_CHAT_NAME = Pattern.compile(
            "(?m)(?:^|\\n)[^|\\n]+\\|\\s*(.+?)\\s*»"
    );

    private static final int FRAME_X = 5;
    private static final int FRAME_MAX_WIDTH = 250;
    private static final int FRAME_MIN_WIDTH = 185;
    private static final int SIDEBAR_MAX_WIDTH = 88;
    private static final int SIDEBAR_MIN_WIDTH = 68;
    private static final int MESSAGE_BOTTOM_GAP = 42;

    private static final Map<GuiMessage, Classification> CLASSIFICATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static ChatEngine engine;
    private static ChatServerAdapter adapter;
    private static LocalSocialStore socialStore;
    private static String activeServerAddress;
    private static DebugCapture debugCapture;
    private static Map<String, String> aliasCache = Map.of();
    private static final Map<String, String> observedPublicAliases = new HashMap<>();

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
            return FRAME_MAX_WIDTH;
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int preferred = (int)Math.round(screenWidth * 0.36);
        preferred = Math.max(FRAME_MIN_WIDTH, Math.min(FRAME_MAX_WIDTH, preferred));
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
        int target = (int)Math.round(screenHeight * 0.38);
        int max = Math.max(78, Math.min(125, screenHeight - 92));
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
        Matcher matcher = PLAYER_CHAT_NAME.matcher(plain);
        if (!matcher.find()) {
            return contents;
        }

        String rawPlayer = matcher.group(1);
        String player = rawPlayer
                .replaceAll("(?i)§[0-9A-FK-ORX]", "")
                .trim();
        if (!player.matches("[A-Za-z0-9_.~-]{1,32}")) {
            return contents;
        }

        observedPublicAliases.put(identityBase(player), player);
        String privateTarget = resolvePrivateTarget(player);

        int targetStart = matcher.start(1);
        int targetEnd = matcher.end(1);
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

        if (classification.category() == ChatCategory.PRIVATE && classification.privatePartner() != null) {
            learnAliasesForCanonical(classification.privatePartner());
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
        engine.selectPrivatePartner(resolvePrivateTarget(partner));
        refreshChatView();
    }

    public static void closePrivatePartner(String partner) {
        if (!isActive()) {
            return;
        }
        engine.closePrivatePartner(partner);
        saveFavorites();
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
        if (engine == null || engine.activeCategory() != category) {
            return false;
        }
        return category != ChatCategory.PRIVATE || engine.activePrivatePartner() == null;
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

        String base = identityBase(target);
        PlayerInfo match = null;
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (identityBase(info.getProfile().name()).equals(base)) {
                if (match != null) {
                    return null;
                }
                match = info;
            }
        }
        return match;
    }

    public static boolean isImportant(PrivateMessageEntry message) {
        return socialStore != null && socialStore.isImportant(message);
    }

    public static boolean setImportant(PrivateMessageEntry message, boolean important) {
        return socialStore != null && socialStore.setImportant(message, important);
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
        if (engine != null) {
            engine.resetTransientState();
        }
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
            showOverlay(enabled ? "Opsucht Chat Debug AN: " + file : "Opsucht Chat Debug AUS");
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

        adapter = ServerAdapterRegistry.resolve(address);
        engine = adapter == null ? null : new ChatEngine(adapter);
        socialStore = null;

        if (adapter == null || engine == null) {
            return;
        }

        socialStore = new LocalSocialStore(rootDataDir(), adapter.id());
        aliasCache = socialStore.loadAliases();
        observedPublicAliases.clear();

        if ("opsucht".equals(adapter.id())) {
            socialStore.importLegacyFavorites(rootDataDir().resolve("pinned-pn.txt"));
        }

        for (String favorite : socialStore.loadFavorites()) {
            engine.restorePinnedPrivatePartner(favorite);
        }

        for (PrivateMessageEntry message : socialStore.loadPrivateMessages(1_000)) {
            engine.restorePrivateMessage(message);
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
            String base = identityBase(trimmed);
            String candidate = null;

            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                String profileName = info.getProfile().name();
                if (profileName.equalsIgnoreCase(trimmed)) {
                    return profileName;
                }
                if (identityBase(profileName).equals(base)) {
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
            String base = identityBase(trimmed);
            String candidate = null;
            for (PrivateConversation conversation : engine.recentPrivateConversations()) {
                if (identityBase(conversation.name()).equals(base)) {
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
        String base = identityBase(canonical);
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

    private static String identityBase(String value) {
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
