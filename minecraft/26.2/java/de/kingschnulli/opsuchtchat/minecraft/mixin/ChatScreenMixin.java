package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import de.kingschnulli.opsuchtchat.core.PrivateMessageDirection;
import de.kingschnulli.opsuchtchat.core.PrivateMessageEntry;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.minecraft.ChatViewMessage;
import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Unique
    private static final int TAB_GAP = 1;
    @Unique
    private static final int TAB_HEIGHT = 16;
    @Unique
    private static final int INPUT_CONTEXT_WIDTH = 76;
    @Unique
    private static final int PRIVATE_TAB_SLOTS = 8;
    @Unique
    private static final int CONVERSATION_ROW_HEIGHT = 28;

    @Unique
    private static final int PANEL_BG = 0xD414181F;
    @Unique
    private static final int PANEL_BG_ALT = 0xE0181E25;
    @Unique
    private static final int PANEL_BG_SELECTED = 0xE0264C65;
    @Unique
    private static final int CARD_BG = 0xB51A2028;
    @Unique
    private static final int BORDER = 0xD064707C;
    @Unique
    private static final int ACCENT = 0xFF31A8E6;
    @Unique
    private static final int TEXT = 0xFFF3F6F8;
    @Unique
    private static final int MUTED = 0xFF9AA6B2;
    @Unique
    private static final int INCOMING = 0xFFFFD34D;
    @Unique
    private static final int OUTGOING = 0xFF45C7FF;
    @Unique
    private static final int UNREAD = 0xFFD94B4B;
    @Unique
    private static final int GREEN = 0xFF5DD56C;
    @Unique
    private static final int RED = 0xFFFF5A5A;
    @Unique
    private static final int GOLD = 0xFFFFC83D;
    @Unique
    private static final int PURPLE = 0xFFD65CFF;

    @Unique
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    @Shadow
    protected EditBox input;

    @Unique
    private final Map<ChatCategory, FrameButton> opsuchtChat$buttons = new EnumMap<>(ChatCategory.class);
    @Unique
    private final ConversationButton[] opsuchtChat$privateRows = new ConversationButton[PRIVATE_TAB_SLOTS];
    @Unique
    private final FrameButton[] opsuchtChat$privatePinButtons = new FrameButton[PRIVATE_TAB_SLOTS];
    @Unique
    private final FrameButton[] opsuchtChat$privateCloseButtons = new FrameButton[PRIVATE_TAB_SLOTS];
    @Unique
    private final String[] opsuchtChat$privatePartners = new String[PRIVATE_TAB_SLOTS];
    @Unique
    private final List<PrivateMessageHitbox> opsuchtChat$messageHitboxes = new ArrayList<>();
    @Unique
    private final List<FeedHitbox> opsuchtChat$feedHitboxes = new ArrayList<>();

    @Unique
    private FrameButton opsuchtChat$importantButton;
    @Unique
    private boolean opsuchtChat$importantOnly;
    @Unique
    private int opsuchtChat$feedScroll;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void opsuchtChat$initFrame(CallbackInfo ci) {
        OpsuchtChatMinecraft.installFilter();
        opsuchtChat$buttons.clear();

        if (!OpsuchtChatMinecraft.isActive()) {
            return;
        }

        opsuchtChat$positionInput();
        opsuchtChat$addMainTabs();
        opsuchtChat$addPrivateSidebarButtons();
        opsuchtChat$addImportantButton();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void opsuchtChat$drawFrame(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()) {
            return;
        }

        opsuchtChat$positionInput();
        opsuchtChat$positionMainTabs();
        opsuchtChat$refreshPrivateSidebar();

        int x = OpsuchtChatMinecraft.frameX();
        int width = OpsuchtChatMinecraft.frameWidth();
        int right = x + width;
        int top = OpsuchtChatMinecraft.frameTop();
        int bottom = OpsuchtChatMinecraft.frameBottom();
        int messageBottom = OpsuchtChatMinecraft.messageBottom();
        int inputTop = this.height - 21;

        graphics.fill(x, top, right, bottom, PANEL_BG);
        graphics.fill(x, top, right, top + 1, BORDER);
        graphics.fill(x, bottom - 1, right, bottom, BORDER);
        graphics.fill(x, top, x + 1, bottom, BORDER);
        graphics.fill(right - 1, top, right, bottom, BORDER);

        graphics.fill(x + 1, messageBottom, right - 1, messageBottom + 1, BORDER);
        graphics.fill(x + 1, inputTop - 2, right - 1, inputTop - 1, BORDER);

        if (OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE) {
            int sidebar = OpsuchtChatMinecraft.sidebarWidth();
            int sidebarRight = x + sidebar;
            graphics.fill(x + 1, top + 1, sidebarRight, messageBottom, PANEL_BG_ALT);
            graphics.fill(sidebarRight, top + 1, sidebarRight + 1, messageBottom, BORDER);
            graphics.text(this.font, "PN", x + 6, top + 5, TEXT, false);

            String partner = OpsuchtChatMinecraft.activePrivatePartner();
            if (opsuchtChat$importantOnly) {
                graphics.text(this.font, "★ Wichtige Nachrichten", sidebarRight + 7, top + 5, GOLD, false);
            } else if (partner != null) {
                int headerX = sidebarRight + 7;
                opsuchtChat$renderFace(graphics, partner, headerX, top + 3, 14);
                graphics.text(this.font, partner, headerX + 19, top + 5, TEXT, false);

                PlayerInfo info = OpsuchtChatMinecraft.playerInfo(partner);
                if (info != null) {
                    graphics.fill(headerX + 12, top + 14, headerX + 15, top + 17, GREEN);
                }
            } else {
                graphics.text(this.font, "Unterhaltung auswählen", sidebarRight + 7, top + 5, MUTED, false);
            }
        }

        if (this.input.getValue().isEmpty()) {
            graphics.text(this.font, "Nachricht schreiben …", x + 7, inputTop + 4, MUTED, false);
        }

        int contextLeft = right - INPUT_CONTEXT_WIDTH - 4;
        graphics.fill(contextLeft, inputTop, right - 4, bottom - 4, PANEL_BG_ALT);
        String context = opsuchtChat$importantOnly && OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE
                ? "★ WICHTIG"
                : OpsuchtChatMinecraft.inputContextLabel();
        int contextX = contextLeft + Math.max(3, (INPUT_CONTEXT_WIDTH - this.font.width(context)) / 2);
        graphics.text(this.font, context, contextX, inputTop + 4, TEXT, false);
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"
            )
    )
    private void opsuchtChat$renderMessages(
            ChatComponent chat,
            GuiGraphicsExtractor graphics,
            Font font,
            int ticks,
            int mouseX,
            int mouseY,
            ChatComponent.DisplayMode displayMode,
            boolean changeCursorOnInsertions
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()) {
            chat.extractRenderState(graphics, font, ticks, mouseX, mouseY, displayMode, changeCursorOnInsertions);
            return;
        }

        if (OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE) {
            opsuchtChat$renderPrivateTranscript(graphics, font);
        } else {
            opsuchtChat$renderStructuredFeed(graphics, font);
        }
    }

    @Redirect(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent;captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V"
            )
    )
    private void opsuchtChat$captureClickableText(
            ChatComponent chat,
            ActiveTextCollector collector,
            int screenHeight,
            int ticks,
            ChatComponent.DisplayMode displayMode
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()) {
            chat.captureClickableText(collector, screenHeight, ticks, displayMode);
        }
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
            )
    )
    private void opsuchtChat$replaceVanillaInputBackground(
            GuiGraphicsExtractor graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int color
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()) {
            graphics.fill(x0, y0, x1, y1, color);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$handleFrameClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()) {
            return;
        }

        int mouseX = (int)event.x();
        int mouseY = (int)event.y();

        if (event.button() == 1 && OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE) {
            for (PrivateMessageHitbox hitbox : opsuchtChat$messageHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    boolean important = !OpsuchtChatMinecraft.isImportant(hitbox.message());
                    OpsuchtChatMinecraft.setImportant(hitbox.message(), important);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (event.button() == 0) {
            for (FeedHitbox hitbox : opsuchtChat$feedHitboxes) {
                if (!hitbox.contains(mouseX, mouseY)) {
                    continue;
                }

                if (hitbox.action() == FeedAction.PLAYER) {
                    opsuchtChat$importantOnly = false;
                    opsuchtChat$feedScroll = 0;
                    OpsuchtChatMinecraft.selectPrivatePartner(hitbox.value());
                    this.setInitialFocus(this.input);
                    cir.setReturnValue(true);
                    return;
                }

                if (hitbox.action() == FeedAction.COMMAND) {
                    this.input.setValue(hitbox.value());
                    this.input.moveCursorToEnd(false);
                    this.setInitialFocus(this.input);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$scrollFrame(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()) {
            return;
        }

        int x = OpsuchtChatMinecraft.frameX();
        int right = x + OpsuchtChatMinecraft.frameWidth();
        int top = OpsuchtChatMinecraft.frameTop();
        int bottom = OpsuchtChatMinecraft.messageBottom();
        if (mouseX < x || mouseX > right || mouseY < top || mouseY > bottom) {
            return;
        }

        int max;
        if (OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE) {
            List<PrivateMessageEntry> messages = opsuchtChat$privateSource();
            max = Math.max(0, messages.size() - 1);
        } else {
            max = Math.max(0, OpsuchtChatMinecraft.visibleFeedMessages().size() - 1);
        }

        int delta = scrollY > 0 ? 3 : scrollY < 0 ? -3 : 0;
        opsuchtChat$feedScroll = Math.max(0, Math.min(max, opsuchtChat$feedScroll + delta));
        cir.setReturnValue(true);
    }

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$openPrivateFromPlayerName(
            Style clicked,
            boolean allowInsertions,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (OpsuchtChatMinecraft.handlePlayerNameClick(clicked)) {
            opsuchtChat$importantOnly = false;
            opsuchtChat$feedScroll = 0;
            this.setInitialFocus(this.input);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$handleLocalCommand(String message, boolean addToRecent, CallbackInfo ci) {
        if (OpsuchtChatMinecraft.handleLocalCommand(message)
                || OpsuchtChatMinecraft.handleActivePrivateInput(message, addToRecent)) {
            opsuchtChat$feedScroll = 0;
            ci.cancel();
        }
    }

    @Unique
    private void opsuchtChat$renderStructuredFeed(GuiGraphicsExtractor graphics, Font font) {
        opsuchtChat$feedHitboxes.clear();
        opsuchtChat$messageHitboxes.clear();

        List<ChatViewMessage> messages = OpsuchtChatMinecraft.visibleFeedMessages();
        int x = OpsuchtChatMinecraft.frameX() + 6;
        int right = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 6;
        int top = OpsuchtChatMinecraft.frameTop() + 4;
        int bottom = OpsuchtChatMinecraft.messageBottom() - 4;
        int available = Math.max(20, bottom - top);

        if (messages.isEmpty()) {
            graphics.text(font, emptyFeedText(), x, top + 5, MUTED, false);
            return;
        }

        int end = Math.max(0, messages.size() - Math.min(opsuchtChat$feedScroll, Math.max(0, messages.size() - 1)));
        List<FeedLayout> visible = new ArrayList<>();
        int used = 0;

        for (int i = end - 1; i >= 0; i--) {
            ChatViewMessage entry = messages.get(i);
            FeedLayout layout = opsuchtChat$layoutFeed(font, entry, Math.max(60, right - x));
            if (!visible.isEmpty() && used + layout.height() > available) {
                break;
            }
            visible.add(0, layout);
            used += layout.height();
        }

        int y = bottom - used;
        for (FeedLayout layout : visible) {
            y = opsuchtChat$renderFeedLayout(graphics, font, layout, x, right, y);
        }

        if (opsuchtChat$feedScroll > 0) {
            String marker = "↑ ältere Nachrichten";
            graphics.text(font, marker, right - font.width(marker), top, MUTED, false);
        }
    }

    @Unique
    private FeedLayout opsuchtChat$layoutFeed(Font font, ChatViewMessage entry, int width) {
        if (entry.classification().category() == ChatCategory.SERVER && entry.serverEvent() != null) {
            List<FormattedCharSequence> lines = font.split(Component.literal(entry.serverEvent().body()), Math.max(40, width - 12));
            return new FeedLayout(entry, lines, 15 + Math.max(1, lines.size()) * 9 + 5);
        }

        if (entry.classification().category() == ChatCategory.AUCTION && entry.auctionEvent() != null) {
            List<FormattedCharSequence> lines = font.split(Component.literal(entry.auctionEvent().body()), Math.max(40, width - 12));
            return new FeedLayout(entry, lines, 15 + Math.max(1, lines.size()) * 9 + 5);
        }

        if (entry.classification().category() == ChatCategory.PRIVATE
                && entry.classification().privateBody() != null) {
            List<FormattedCharSequence> lines = font.split(
                    Component.literal(entry.classification().privateBody()),
                    Math.max(40, width - 28)
            );
            return new FeedLayout(entry, lines, 12 + Math.max(1, lines.size()) * 9 + 4);
        }

        if (entry.publicChat() != null) {
            List<FormattedCharSequence> lines = font.split(
                    Component.literal(entry.publicChat().body()),
                    Math.max(40, width - 22)
            );
            return new FeedLayout(entry, lines, 12 + Math.max(1, lines.size()) * 9 + 4);
        }

        List<FormattedCharSequence> lines = font.split(entry.message().content(), Math.max(40, width - 4));
        return new FeedLayout(entry, lines, Math.max(1, lines.size()) * 9 + 5);
    }

    @Unique
    private int opsuchtChat$renderFeedLayout(
            GuiGraphicsExtractor graphics,
            Font font,
            FeedLayout layout,
            int x,
            int right,
            int y
    ) {
        ChatViewMessage entry = layout.entry();
        ChatCategory category = entry.classification().category();

        if (category == ChatCategory.SERVER && entry.serverEvent() != null) {
            opsuchtChat$renderServerEvent(graphics, font, entry, layout.lines(), x, right, y);
            return y + layout.height();
        }

        if (category == ChatCategory.AUCTION && entry.auctionEvent() != null) {
            opsuchtChat$renderAuctionEvent(graphics, font, entry, layout.lines(), x, right, y);
            return y + layout.height();
        }

        if (category == ChatCategory.PRIVATE && entry.classification().privateBody() != null) {
            opsuchtChat$renderPrivateInFeed(graphics, font, entry, layout.lines(), x, right, y);
            return y + layout.height();
        }

        if (entry.publicChat() != null) {
            opsuchtChat$renderPublicChat(graphics, font, entry, layout.lines(), x, right, y);
            return y + layout.height();
        }

        int lineY = y + 1;
        for (FormattedCharSequence line : layout.lines()) {
            graphics.text(font, line, x, lineY, TEXT, false);
            lineY += 9;
        }
        return y + layout.height();
    }

    @Unique
    private void opsuchtChat$renderPublicChat(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatViewMessage entry,
            List<FormattedCharSequence> lines,
            int x,
            int right,
            int y
    ) {
        PublicChatLine chat = entry.publicChat();
        String resolved = OpsuchtChatMinecraft.resolvedPrivateTarget(chat.player());
        opsuchtChat$renderFace(graphics, resolved, x, y + 1, 14);

        int headerX = x + 18;
        String rank = cleanRank(chat.rank());
        int rankColor = rankColor(rank);
        graphics.text(font, rank, headerX, y, rankColor, false);
        int afterRank = headerX + font.width(rank);
        graphics.text(font, " | ", afterRank, y, MUTED, false);

        int playerX = afterRank + font.width(" | ");
        graphics.text(font, chat.player(), playerX, y, TEXT, false);
        int playerRight = playerX + font.width(chat.player());
        opsuchtChat$feedHitboxes.add(new FeedHitbox(
                playerX,
                y,
                playerRight,
                y + 10,
                FeedAction.PLAYER,
                resolved
        ));

        String time = TIME_FORMAT.format(entry.receivedAt());
        graphics.text(font, time, right - font.width(time), y, MUTED, false);

        if (entry.classification().category() == ChatCategory.ADVERTISING) {
            String badge = "WERBUNG";
            int badgeX = right - font.width(time) - font.width(badge) - 7;
            graphics.text(font, badge, badgeX, y, GOLD, false);
        }

        int lineY = y + 11;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, headerX, lineY, TEXT, false);
            lineY += 9;
        }
    }

    @Unique
    private void opsuchtChat$renderServerEvent(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatViewMessage entry,
            List<FormattedCharSequence> lines,
            int x,
            int right,
            int y
    ) {
        ServerFeedEvent event = entry.serverEvent();
        int accent = serverColor(event.kind());

        graphics.fill(x, y, right, y + 13 + Math.max(1, lines.size()) * 9 + 3, CARD_BG);
        graphics.fill(x, y, x + 2, y + 13 + Math.max(1, lines.size()) * 9 + 3, accent);

        String label = serverLabel(event.kind());
        graphics.text(font, label, x + 6, y + 3, accent, false);
        graphics.text(font, event.title(), x + 8 + font.width(label), y + 3, TEXT, false);

        String time = TIME_FORMAT.format(entry.receivedAt());
        graphics.text(font, time, right - font.width(time) - 4, y + 3, MUTED, false);

        int lineY = y + 14;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x + 7, lineY, TEXT, false);
            lineY += 9;
        }

        if (event.action() != null && !event.action().isBlank()) {
            int actionY = lineY - 9;
            int actionWidth = Math.min(font.width(event.action()), Math.max(1, right - x - 16));
            opsuchtChat$feedHitboxes.add(new FeedHitbox(
                    x + 7,
                    actionY,
                    x + 7 + actionWidth,
                    actionY + 9,
                    FeedAction.COMMAND,
                    event.action()
            ));
        }
    }

    @Unique
    private void opsuchtChat$renderAuctionEvent(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatViewMessage entry,
            List<FormattedCharSequence> lines,
            int x,
            int right,
            int y
    ) {
        AuctionFeedEvent event = entry.auctionEvent();
        int accent = auctionColor(event.kind());
        String badge = auctionLabel(event.kind());

        graphics.fill(x, y, right, y + 13 + Math.max(1, lines.size()) * 9 + 3, CARD_BG);
        graphics.fill(x, y, x + 2, y + 13 + Math.max(1, lines.size()) * 9 + 3, accent);

        graphics.text(font, badge, x + 6, y + 3, accent, false);
        int playerX = x + 9 + font.width(badge);
        graphics.text(font, event.player(), playerX, y + 3, TEXT, false);
        opsuchtChat$feedHitboxes.add(new FeedHitbox(
                playerX,
                y + 2,
                playerX + font.width(event.player()),
                y + 12,
                FeedAction.PLAYER,
                OpsuchtChatMinecraft.resolvedPrivateTarget(event.player())
        ));

        String rightText = event.amount() == null ? TIME_FORMAT.format(entry.receivedAt()) : event.amount();
        graphics.text(font, rightText, right - font.width(rightText) - 4, y + 3, accent, false);

        int lineY = y + 14;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x + 7, lineY, TEXT, false);
            lineY += 9;
        }
    }

    @Unique
    private void opsuchtChat$renderPrivateInFeed(
            GuiGraphicsExtractor graphics,
            Font font,
            ChatViewMessage entry,
            List<FormattedCharSequence> lines,
            int x,
            int right,
            int y
    ) {
        boolean outgoing = entry.classification().privateDirection() == PrivateMessageDirection.OUTGOING;
        String partner = entry.classification().privatePartner();
        if (partner == null) {
            partner = "PN";
        }

        if (outgoing) {
            opsuchtChat$renderSelfFace(graphics, x, y + 1, 14);
        } else {
            opsuchtChat$renderFace(graphics, partner, x, y + 1, 14);
        }

        int textX = x + 18;
        String label = outgoing ? "Du → " + partner : partner + " → Du";
        graphics.text(font, "PN", textX, y, OUTGOING, false);
        graphics.text(font, label, textX + font.width("PN") + 5, y, TEXT, false);

        String time = TIME_FORMAT.format(entry.receivedAt());
        graphics.text(font, time, right - font.width(time), y, MUTED, false);

        int lineY = y + 11;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, textX, lineY, TEXT, false);
            lineY += 9;
        }
    }

    @Unique
    private void opsuchtChat$renderPrivateTranscript(GuiGraphicsExtractor graphics, Font font) {
        opsuchtChat$messageHitboxes.clear();
        opsuchtChat$feedHitboxes.clear();

        int x = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.sidebarWidth() + 7;
        int right = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 6;
        int top = OpsuchtChatMinecraft.frameTop() + 20;
        int bottom = OpsuchtChatMinecraft.messageBottom() - 4;
        int textWidth = Math.max(36, right - x - 24);

        List<PrivateMessageEntry> messages = opsuchtChat$privateSource();
        if (messages.isEmpty()) {
            String empty = opsuchtChat$importantOnly
                    ? "Noch keine wichtigen Nachrichten."
                    : "Noch keine lokal gespeicherten Nachrichten.";
            graphics.text(font, empty, x, top + 7, MUTED, false);
            return;
        }

        int end = Math.max(0, messages.size() - Math.min(opsuchtChat$feedScroll, Math.max(0, messages.size() - 1)));
        List<PrivateMessageLayout> visible = new ArrayList<>();
        int available = Math.max(20, bottom - top);
        int used = 0;

        for (int i = end - 1; i >= 0; i--) {
            PrivateMessageEntry entry = messages.get(i);
            List<FormattedCharSequence> lines = font.split(Component.literal(entry.body()), textWidth);
            int height = 13 + Math.max(1, lines.size()) * 9 + 4;

            if (!visible.isEmpty() && used + height > available) {
                break;
            }

            visible.add(0, new PrivateMessageLayout(entry, lines, height));
            used += height;
        }

        int y = bottom - used;
        for (PrivateMessageLayout layout : visible) {
            PrivateMessageEntry entry = layout.entry();
            boolean outgoing = entry.direction() == PrivateMessageDirection.OUTGOING;

            int avatarX = x;
            int avatarY = y + 1;
            if (outgoing) {
                opsuchtChat$renderSelfFace(graphics, avatarX, avatarY, 14);
            } else {
                opsuchtChat$renderFace(graphics, entry.partner(), avatarX, avatarY, 14);
            }

            String sender = outgoing ? "Du" : entry.partner();
            int senderColor = outgoing ? OUTGOING : INCOMING;
            graphics.text(font, sender, x + 19, y, senderColor, false);

            String time = TIME_FORMAT.format(entry.receivedAt());
            graphics.text(font, time, right - font.width(time), y, MUTED, false);

            boolean important = OpsuchtChatMinecraft.isImportant(entry);
            if (important) {
                graphics.text(font, "★", right - font.width(time) - font.width("★") - 5, y, GOLD, false);
            }

            int lineY = y + 11;
            for (FormattedCharSequence line : layout.lines()) {
                graphics.text(font, line, x + 19, lineY, TEXT, false);
                lineY += 9;
            }

            opsuchtChat$messageHitboxes.add(new PrivateMessageHitbox(
                    x,
                    y,
                    right,
                    y + layout.height(),
                    entry
            ));
            y += layout.height();
        }

        if (opsuchtChat$feedScroll > 0) {
            graphics.text(font, "↑", right - font.width("↑"), top, MUTED, false);
        }
    }

    @Unique
    private List<PrivateMessageEntry> opsuchtChat$privateSource() {
        if (opsuchtChat$importantOnly) {
            return OpsuchtChatMinecraft.importantPrivateMessages();
        }
        String partner = OpsuchtChatMinecraft.activePrivatePartner();
        return partner == null ? List.of() : OpsuchtChatMinecraft.privateMessages(partner);
    }

    @Unique
    private String emptyFeedText() {
        return switch (OpsuchtChatMinecraft.activeCategory()) {
            case MESSAGE -> "Noch keine normalen Nachrichten.";
            case AUCTION -> "Keine laufenden Auktionsmeldungen.";
            case SERVER -> "Noch keine Servermeldungen.";
            case ADVERTISING -> "Keine Werbung erkannt.";
            default -> "Noch keine Nachrichten.";
        };
    }

    @Unique
    private static String cleanRank(String rank) {
        if (rank == null || rank.isBlank()) {
            return "Spieler";
        }
        return rank.replaceAll("^[^A-Za-zÄÖÜäöü0-9~.]+", "").trim();
    }

    @Unique
    private static int rankColor(String rank) {
        String value = rank.toLowerCase(Locale.ROOT);
        if (value.contains("platin")) return 0xFF59E9E0;
        if (value.contains("premium")) return 0xFFFFB51F;
        if (value.contains("supreme")) return 0xFFE458E9;
        if (value.contains("diamond")) return 0xFF45D7E9;
        if (value.contains("ultra")) return 0xFF57C9FF;
        if (value.contains("legende")) return 0xFFF5E94A;
        if (value.contains("creator")) return 0xFFFFA04A;
        return MUTED;
    }

    @Unique
    private static String serverLabel(ServerEventKind kind) {
        return switch (kind) {
            case MONEY -> "GELD";
            case TELEPORT -> "TP";
            case VOTE -> "VOTE";
            case FISHING -> "FISCH";
            case MARKET -> "MARKT";
            case JOB -> "JOB";
            case BOOSTER -> "BOOST";
            case ACTION -> "➜";
            case INFO -> "OPSUCHT";
        };
    }

    @Unique
    private static int serverColor(ServerEventKind kind) {
        return switch (kind) {
            case MONEY, JOB -> GREEN;
            case TELEPORT -> OUTGOING;
            case VOTE -> PURPLE;
            case FISHING -> 0xFF59D3D8;
            case MARKET -> GOLD;
            case BOOSTER -> 0xFFFF8F40;
            case ACTION -> ACCENT;
            case INFO -> RED;
        };
    }

    @Unique
    private static String auctionLabel(AuctionEventKind kind) {
        return switch (kind) {
            case START -> "START";
            case BID -> "GEBOT";
            case COUNTDOWN -> "COUNT";
            case SOLD -> "VERKAUFT";
            case OTHER -> "AUKTION";
        };
    }

    @Unique
    private static int auctionColor(AuctionEventKind kind) {
        return switch (kind) {
            case START -> GOLD;
            case BID -> OUTGOING;
            case COUNTDOWN -> PURPLE;
            case SOLD -> GREEN;
            case OTHER -> MUTED;
        };
    }

    @Unique
    private static String firstLetter(String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        String cleaned = value;
        while (cleaned.startsWith("~") || cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isBlank()) {
            return "?";
        }
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    @Unique
    private void opsuchtChat$positionInput() {
        int x = OpsuchtChatMinecraft.frameX();
        int width = OpsuchtChatMinecraft.frameWidth();
        int inputWidth = Math.max(50, width - INPUT_CONTEXT_WIDTH - 17);
        this.input.setRectangle(inputWidth, 13, x + 7, this.height - 20);
    }

    @Unique
    private void opsuchtChat$addMainTabs() {
        for (ChatCategory category : ChatCategory.values()) {
            FrameButton button = new FrameButton(
                    0,
                    0,
                    40,
                    TAB_HEIGHT,
                    Component.literal(OpsuchtChatMinecraft.tabLabel(category)),
                    ignored -> {
                        opsuchtChat$importantOnly = false;
                        opsuchtChat$feedScroll = 0;
                        OpsuchtChatMinecraft.select(category);
                    },
                    () -> OpsuchtChatMinecraft.isCategorySelected(category)
            );
            this.addRenderableWidget(button);
            opsuchtChat$buttons.put(category, button);
        }
        opsuchtChat$positionMainTabs();
    }

    @Unique
    private void opsuchtChat$positionMainTabs() {
        if (opsuchtChat$buttons.isEmpty()) {
            return;
        }

        int x = OpsuchtChatMinecraft.frameX() + 3;
        int frameWidth = OpsuchtChatMinecraft.frameWidth();
        int count = ChatCategory.values().length;
        int usable = frameWidth - 6 - (count - 1) * TAB_GAP;
        int y = this.height - 40;

        int[] widths = new int[count];
        int required = 0;
        int index = 0;
        for (ChatCategory category : ChatCategory.values()) {
            String label = OpsuchtChatMinecraft.tabLabel(category);
            widths[index] = Math.max(23, this.font.width(label) + 6);
            required += widths[index++];
        }

        if (required > usable) {
            double factor = usable / (double)required;
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Math.max(22, (int)Math.floor(widths[i] * factor));
            }
        } else {
            int extra = (usable - required) / count;
            for (int i = 0; i < widths.length; i++) {
                widths[i] += extra;
            }
        }

        index = 0;
        for (ChatCategory category : ChatCategory.values()) {
            FrameButton button = opsuchtChat$buttons.get(category);
            int width = widths[index++];
            int remaining = OpsuchtChatMinecraft.frameX() + frameWidth - 3 - x;
            width = Math.min(width, Math.max(20, remaining));
            button.setRectangle(width, TAB_HEIGHT, x, y);
            button.setMessage(Component.literal(OpsuchtChatMinecraft.tabLabel(category)));
            x += width + TAB_GAP;
        }
    }

    @Unique
    private void opsuchtChat$addPrivateSidebarButtons() {
        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            final int slot = i;

            ConversationButton row = new ConversationButton(
                    0, 0, 60, CONVERSATION_ROW_HEIGHT,
                    ignored -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        if (partner != null) {
                            opsuchtChat$importantOnly = false;
                            opsuchtChat$feedScroll = 0;
                            OpsuchtChatMinecraft.selectPrivatePartner(partner);
                        }
                    },
                    () -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        return !opsuchtChat$importantOnly
                                && partner != null
                                && OpsuchtChatMinecraft.isPrivatePartnerSelected(partner);
                    }
            );
            row.visible = false;
            this.addRenderableWidget(row);
            opsuchtChat$privateRows[i] = row;

            FrameButton pin = new FrameButton(
                    0, 0, 13, 13, Component.literal("☆"),
                    ignored -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        if (partner != null) {
                            OpsuchtChatMinecraft.togglePrivatePinned(partner);
                        }
                    },
                    () -> false
            );
            pin.visible = false;
            this.addRenderableWidget(pin);
            opsuchtChat$privatePinButtons[i] = pin;

            FrameButton close = new FrameButton(
                    0, 0, 13, 13, Component.literal("×"),
                    ignored -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        if (partner != null) {
                            OpsuchtChatMinecraft.closePrivatePartner(partner);
                        }
                    },
                    () -> false
            );
            close.visible = false;
            this.addRenderableWidget(close);
            opsuchtChat$privateCloseButtons[i] = close;
        }
    }

    @Unique
    private void opsuchtChat$addImportantButton() {
        opsuchtChat$importantButton = new FrameButton(
                0, 0, 18, 14, Component.literal("★"),
                ignored -> {
                    opsuchtChat$importantOnly = !opsuchtChat$importantOnly;
                    opsuchtChat$feedScroll = 0;
                },
                () -> opsuchtChat$importantOnly
        );
        opsuchtChat$importantButton.visible = false;
        this.addRenderableWidget(opsuchtChat$importantButton);
    }

    @Unique
    private void opsuchtChat$refreshPrivateSidebar() {
        boolean visible = OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE;
        List<PrivateConversation> conversations = OpsuchtChatMinecraft.recentPrivateConversations();

        int x = OpsuchtChatMinecraft.frameX() + 3;
        int top = OpsuchtChatMinecraft.frameTop();
        int y = top + 18;
        int sidebarWidth = OpsuchtChatMinecraft.sidebarWidth();
        int availableSlots = Math.max(0, (OpsuchtChatMinecraft.messageBottom() - y - 2) / CONVERSATION_ROW_HEIGHT);
        int maxVisible = Math.min(PRIVATE_TAB_SLOTS, availableSlots);
        int rowWidth = Math.max(58, sidebarWidth - 6);

        if (opsuchtChat$importantButton != null) {
            opsuchtChat$importantButton.visible = visible;
            if (visible) {
                opsuchtChat$importantButton.setRectangle(18, 14, x + rowWidth - 18, top + 2);
            }
        }

        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            ConversationButton row = opsuchtChat$privateRows[i];
            FrameButton pin = opsuchtChat$privatePinButtons[i];
            FrameButton close = opsuchtChat$privateCloseButtons[i];

            if (visible && i < conversations.size() && i < maxVisible) {
                PrivateConversation conversation = conversations.get(i);
                opsuchtChat$privatePartners[i] = conversation.name();

                row.setRectangle(rowWidth, CONVERSATION_ROW_HEIGHT - 1, x, y + i * CONVERSATION_ROW_HEIGHT);
                row.setConversation(conversation);

                pin.setRectangle(13, 13, x + rowWidth - 29, y + i * CONVERSATION_ROW_HEIGHT + 2);
                pin.setMessage(Component.literal(conversation.pinned() ? "★" : "☆"));
                pin.setSelectedSupplier(conversation::pinned);

                close.setRectangle(13, 13, x + rowWidth - 15, y + i * CONVERSATION_ROW_HEIGHT + 2);

                row.visible = true;
                pin.visible = true;
                close.visible = true;
            } else {
                opsuchtChat$privatePartners[i] = null;
                row.visible = false;
                pin.visible = false;
                close.visible = false;
            }
        }
    }

    @Unique
    private static class FrameButton extends Button.Plain {
        private BooleanSupplier selected;

        private FrameButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                Button.OnPress onPress,
                BooleanSupplier selected
        ) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.selected = selected;
        }

        private void setSelectedSupplier(BooleanSupplier selected) {
            this.selected = selected;
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            boolean isSelected = selected != null && selected.getAsBoolean();
            int background = isSelected
                    ? PANEL_BG_SELECTED
                    : (this.isHovered() ? 0xE02A333D : 0xD0181E25);

            graphics.fill(getX(), getY(), getRight(), getBottom(), background);

            if (isSelected) {
                graphics.fill(getX(), getBottom() - 2, getRight(), getBottom(), ACCENT);
            }

            Component message = getMessage();
            int color = this.active ? TEXT : MUTED;
            int textX = getX() + Math.max(2, (getWidth() - Minecraft.getInstance().font.width(message)) / 2);
            int textY = getY() + Math.max(1, (getHeight() - 9) / 2);
            graphics.text(Minecraft.getInstance().font, message, textX, textY, color, false);
        }
    }

    @Unique
    private static final class ConversationButton extends Button.Plain {
        private final BooleanSupplier selected;
        private PrivateConversation conversation;

        private ConversationButton(
                int x,
                int y,
                int width,
                int height,
                Button.OnPress onPress,
                BooleanSupplier selected
        ) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
            this.selected = selected;
        }

        private void setConversation(PrivateConversation conversation) {
            this.conversation = conversation;
            this.setMessage(Component.literal(conversation.name()));
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            if (conversation == null) {
                return;
            }

            Font font = Minecraft.getInstance().font;
            boolean isSelected = selected != null && selected.getAsBoolean();
            int background = isSelected
                    ? PANEL_BG_SELECTED
                    : (this.isHovered() ? 0xE0273039 : PANEL_BG_ALT);
            graphics.fill(getX(), getY(), getRight(), getBottom(), background);

            if (isSelected) {
                graphics.fill(getX(), getY(), getX() + 2, getBottom(), ACCENT);
            }

            int avatarX = getX() + 4;
            int avatarY = getY() + 5;
            opsuchtChat$renderFaceStatic(graphics, conversation.name(), avatarX, avatarY, 15);

            int textX = getX() + 23;
            int actionReserve = 31;
            int available = Math.max(18, getWidth() - 23 - actionReserve);
            String name = clamp(font, conversation.name(), available);
            graphics.text(font, name, textX, getY() + 3, TEXT, false);

            String preview = conversation.preview() == null ? "" : conversation.preview();
            int previewWidth = Math.max(16, getWidth() - 27);
            if (conversation.lastMessageAt() != null && !conversation.lastMessageAt().equals(Instant.EPOCH)) {
                String time = TIME_FORMAT.format(conversation.lastMessageAt());
                int timeWidth = font.width(time);
                previewWidth = Math.max(16, previewWidth - timeWidth - 3);
                graphics.text(font, time, getRight() - timeWidth - 3, getY() + 15, MUTED, false);
            }
            preview = clamp(font, preview, previewWidth);
            if (!preview.isBlank()) {
                graphics.text(font, preview, textX, getY() + 15, MUTED, false);
            }

            if (conversation.unread() > 0) {
                String count = conversation.unread() > 9 ? "9+" : Integer.toString(conversation.unread());
                int badgeWidth = Math.max(10, font.width(count) + 4);
                int badgeX = getRight() - badgeWidth - 2;
                int badgeY = getBottom() - 11;
                graphics.fill(badgeX, badgeY, getRight() - 2, getBottom() - 2, UNREAD);
                graphics.text(font, count, badgeX + 2, badgeY + 1, TEXT, false);
            }
        }

        private static String clamp(Font font, String value, int width) {
            if (value == null || value.isEmpty() || font.width(value) <= width) {
                return value == null ? "" : value;
            }
            String suffix = "...";
            int suffixWidth = font.width(suffix);
            return font.plainSubstrByWidth(value, Math.max(1, width - suffixWidth)) + suffix;
        }
    }

    @Unique
    private void opsuchtChat$renderFace(
            GuiGraphicsExtractor graphics,
            String player,
            int x,
            int y,
            int size
    ) {
        opsuchtChat$renderFaceStatic(graphics, player, x, y, size);
    }

    @Unique
    private static void opsuchtChat$renderFaceStatic(
            GuiGraphicsExtractor graphics,
            String player,
            int x,
            int y,
            int size
    ) {
        PlayerInfo info = OpsuchtChatMinecraft.playerInfo(player);
        if (info != null) {
            PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), x, y, size);
            return;
        }

        graphics.fill(x, y, x + size, y + size, 0xFF39434D);
        Font font = Minecraft.getInstance().font;
        String initial = firstLetter(player);
        int initialX = x + Math.max(2, (size - font.width(initial)) / 2);
        int initialY = y + Math.max(1, (size - 9) / 2);
        graphics.text(font, initial, initialX, initialY, TEXT, false);
    }

    @Unique
    private static void opsuchtChat$renderSelfFace(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int size
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            PlayerFaceExtractor.extractRenderState(graphics, minecraft.player.getSkin(), x, y, size);
            return;
        }
        graphics.fill(x, y, x + size, y + size, 0xFF225B73);
    }

    @Unique
    private record PrivateMessageHitbox(
            int left,
            int top,
            int right,
            int bottom,
            PrivateMessageEntry message
    ) {
        private boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    @Unique
    private record FeedHitbox(
            int left,
            int top,
            int right,
            int bottom,
            FeedAction action,
            String value
    ) {
        private boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    @Unique
    private enum FeedAction {
        PLAYER,
        COMMAND
    }

    @Unique
    private record FeedLayout(
            ChatViewMessage entry,
            List<FormattedCharSequence> lines,
            int height
    ) {
    }

    @Unique
    private record PrivateMessageLayout(
            PrivateMessageEntry entry,
            List<FormattedCharSequence> lines,
            int height
    ) {
    }
}
