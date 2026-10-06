package de.kingschnulli.opsuchtchat.minecraft;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.ChatEnvelope;
import de.kingschnulli.opsuchtchat.core.ChatSource;
import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.DebugCapture;
import de.kingschnulli.opsuchtchat.core.OpsuchtChatEngine;
import de.kingschnulli.opsuchtchat.core.OpsuchtHost;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
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
            "(?m)(?:^|\\n)[^|\\n]+\\|\\s*([A-Za-z0-9_.~-]{1,32})\\s*»"
    );

    private static final int FRAME_X = 6;
    private static final int FRAME_MAX_WIDTH = 430;
    private static final int FRAME_MIN_WIDTH = 300;
    private static final int SIDEBAR_MAX_WIDTH = 124;
    private static final int SIDEBAR_MIN_WIDTH = 102;
    private static final int MESSAGE_BOTTOM_GAP = 42;

    private static final OpsuchtChatEngine ENGINE = new OpsuchtChatEngine();
    private static final Map<GuiMessage, Classification> CLASSIFICATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static DebugCapture debugCapture;
    private static boolean pinsLoaded;

    private OpsuchtChatMinecraft() {
    }

    public static void bootstrap() {
        loadPinnedPartners();
        installFilter();
    }

    public static void installFilter() {
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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        ServerData server = minecraft.getCurrentServer();
        return server != null && OpsuchtHost.matches(server.ip);
    }

    public static boolean isChatFrameActive() {
        Minecraft minecraft = Minecraft.getInstance();
        return isActive()
                && minecraft != null
                && minecraft.gui != null
                && minecraft.gui.screen() instanceof ChatScreen;
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
        int preferred = Math.min(FRAME_MAX_WIDTH, Math.max(FRAME_MIN_WIDTH, (int)Math.round(screenWidth * 0.48)));
        return Math.max(220, Math.min(screenWidth - FRAME_X * 2, preferred));
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
        double scale = Math.max(0.1, minecraft.options.chatScale().get());
        int configured = (int)Math.ceil(ChatComponent.getHeight(minecraft.options.chatHeightFocused().get()) * scale);
        int desired = Math.max(configured, 126);
        return Math.max(72, Math.min(desired, screenHeight - 104));
    }

    public static int frameTop() {
        return messageBottom() - messageHeightPixels() - 4;
    }

    public static int frameBottom() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 0 : minecraft.getWindow().getGuiScaledHeight() - 2;
    }

    public static int sidebarWidth() {
        if (ENGINE.activeCategory() != ChatCategory.PRIVATE) {
            return 0;
        }

        int frameWidth = frameWidth();
        return Math.min(SIDEBAR_MAX_WIDTH, Math.max(SIDEBAR_MIN_WIDTH, frameWidth / 3));
    }

    public static int chatRenderOffsetX() {
        int sidebar = sidebarWidth();
        return FRAME_X + (sidebar == 0 ? 0 : sidebar + 5);
    }

    public static int chatContentWidthLogical() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return 320;
        }

        double scale = Math.max(0.1, minecraft.options.chatScale().get());
        int contentPixels = frameWidth() - sidebarWidth() - 12;
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

        String player = matcher.group(1);
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
                        .withClickEvent(new ClickEvent.SuggestCommand(OPEN_PRIVATE_PREFIX + player))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("PN mit " + player + " öffnen")));
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
                selectPrivatePartner(player);
                return true;
            }
        }

        return false;
    }

    public static void observe(GuiMessage message) {
        ChatEnvelope envelope = envelope(message);
        Classification classification = ENGINE.onIncoming(envelope);
        CLASSIFICATIONS.put(message, classification);
        debugCapture().append(envelope, classification);
    }

    public static boolean isVisibleInSelectedTab(GuiMessage message) {
        ChatCategory active = ENGINE.activeCategory();
        if (active == ChatCategory.ALL) {
            return true;
        }

        Classification classification = CLASSIFICATIONS.get(message);
        if (classification == null) {
            classification = ENGINE.classifyStateless(envelope(message));
            CLASSIFICATIONS.put(message, classification);
        }

        if (active == ChatCategory.PRIVATE) {
            if (classification.category() != ChatCategory.PRIVATE) {
                return false;
            }
            String selectedPartner = ENGINE.activePrivatePartner();
            return selectedPartner == null
                    || selectedPartner.equalsIgnoreCase(classification.privatePartner());
        }

        return classification.category() == active;
    }

    public static void select(ChatCategory category) {
        if (!isActive()) {
            return;
        }
        ENGINE.select(category);
        refreshChatView();
    }

    public static void selectPrivatePartner(String partner) {
        if (!isActive()) {
            return;
        }
        ENGINE.selectPrivatePartner(partner);
        refreshChatView();
    }

    public static void closePrivatePartner(String partner) {
        ENGINE.closePrivatePartner(partner);
        savePinnedPartners();
        refreshChatView();
    }

    public static boolean togglePrivatePinned(String partner) {
        boolean pinned = ENGINE.togglePrivatePinned(partner);
        savePinnedPartners();
        return pinned;
    }

    public static ChatCategory activeCategory() {
        return ENGINE.activeCategory();
    }

    public static String activePrivatePartner() {
        return ENGINE.activePrivatePartner();
    }

    public static boolean isCategorySelected(ChatCategory category) {
        if (ENGINE.activeCategory() != category) {
            return false;
        }
        return category != ChatCategory.PRIVATE || ENGINE.activePrivatePartner() == null;
    }

    public static boolean isPrivatePartnerSelected(String partner) {
        return ENGINE.isPrivatePartnerSelected(partner);
    }

    public static int unread(ChatCategory category) {
        return ENGINE.unread(category);
    }

    public static List<PrivateConversation> recentPrivateConversations() {
        return ENGINE.recentPrivateConversations();
    }

    public static String tabLabel(ChatCategory category) {
        String label = category.label();
        int unread = ENGINE.unread(category);
        if (category == ChatCategory.PRIVATE && unread > 0) {
            label += " [" + compactUnread(unread) + "]";
        }
        return label;
    }

    public static String privateTabLabel(PrivateConversation conversation) {
        String label = shortenPlayerName(conversation.name(), 11);
        if (conversation.unread() > 0) {
            label += " [" + compactUnread(conversation.unread()) + "]";
        }
        return label;
    }

    public static String inputContextLabel() {
        String partner = ENGINE.activePrivatePartner();
        if (partner != null) {
            return "An: " + shortenPlayerName(partner, 13);
        }

        return switch (ENGINE.activeCategory()) {
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
        ENGINE.reset();
    }

    public static boolean handleActivePrivateInput(String input, boolean addToRecent) {
        String partner = ENGINE.activePrivatePartner();
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

        minecraft.player.connection.sendCommand("msg " + partner + " " + message);
        return true;
    }

    public static boolean handleLocalCommand(String input) {
        String command = input == null ? "" : input.trim();
        if (!command.equalsIgnoreCase("/opschat") && !command.toLowerCase(Locale.ROOT).startsWith("/opschat ")) {
            return false;
        }

        if (!isActive()) {
            showOverlay("Opsucht Chat ist nur auf opsucht.net aktiv.");
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
            debugCapture = new DebugCapture(opsuchtDataDir().resolve("debug-chat.jsonl"));
        }
        return debugCapture;
    }

    private static Path opsuchtDataDir() {
        Minecraft minecraft = Minecraft.getInstance();
        Path root = minecraft != null && minecraft.gameDirectory != null
                ? minecraft.gameDirectory.toPath()
                : Path.of(".");
        return root.resolve("opsucht-chat");
    }

    private static Path pinnedFile() {
        return opsuchtDataDir().resolve("pinned-pn.txt");
    }

    private static void loadPinnedPartners() {
        if (pinsLoaded) {
            return;
        }
        pinsLoaded = true;

        Path file = pinnedFile();
        if (!Files.isRegularFile(file)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String player = line.trim();
                if (player.matches("[A-Za-z0-9_.~-]{1,32}")) {
                    ENGINE.restorePinnedPrivatePartner(player);
                }
            }
        } catch (IOException ignored) {
            // Local favorites are convenience state; a read failure must never break chat.
        }
    }

    private static void savePinnedPartners() {
        try {
            Files.createDirectories(opsuchtDataDir());
            Files.write(
                    pinnedFile(),
                    ENGINE.pinnedPrivatePartners(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException ignored) {
            // Local persistence must never break chat.
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
