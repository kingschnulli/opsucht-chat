package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import de.kingschnulli.opsuchtchat.core.PrivateMessageDirection;
import de.kingschnulli.opsuchtchat.core.PrivateMessageEntry;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionSessionSnapshot;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerEventKind;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import de.kingschnulli.opsuchtchat.core.server.PlayerActionMode;
import de.kingschnulli.opsuchtchat.core.server.PlayerActionSpec;
import de.kingschnulli.opsuchtchat.core.server.ServerCommandMode;
import de.kingschnulli.opsuchtchat.core.server.ServerCommandSpec;
import de.kingschnulli.opsuchtchat.core.server.ServerHubPage;
import de.kingschnulli.opsuchtchat.core.social.PlayerIdentity;
import de.kingschnulli.opsuchtchat.minecraft.ChatViewMessage;
import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import de.kingschnulli.opsuchtchat.minecraft.ServerClickAction;
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
import net.minecraft.network.chat.ClickEvent;
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
    private final List<ServerClickActionHitbox> opsuchtChat$serverClickActionHitboxes = new ArrayList<>();
    @Unique
    private final List<InteractiveTextLine> opsuchtChat$interactiveLines = new ArrayList<>();

    @Unique
    private FrameButton opsuchtChat$importantButton;
    @Unique
    private final List<FrameButton> opsuchtChat$playerPrimaryActionButtons = new ArrayList<>();
    @Unique
    private final List<FrameButton> opsuchtChat$playerOverflowActionButtons = new ArrayList<>();
    @Unique
    private FrameButton opsuchtChat$playerMoreActionButton;
    @Unique
    private boolean opsuchtChat$playerActionsOpen;
    @Unique
    private final List<FrameButton> opsuchtChat$serverPageButtons = new ArrayList<>();
    @Unique
    private final List<ServerCommandHitbox> opsuchtChat$serverCommandHitboxes = new ArrayList<>();
    @Unique
    private EditBox opsuchtChat$serverSearchInput;
    @Unique
    private String opsuchtChat$serverPageId = "messages";
    @Unique
    private int opsuchtChat$serverCommandScroll;
    @Unique
    private boolean opsuchtChat$importantOnly;
    @Unique
    private boolean opsuchtChat$payMode;
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

        this.input.setCanLoseFocus(true);
        opsuchtChat$positionInput();
        opsuchtChat$addMainTabs();
        opsuchtChat$addPrivateSidebarButtons();
        opsuchtChat$addImportantButton();
        opsuchtChat$addPrivateHeaderActions();
        opsuchtChat$addServerWorkspaceWidgets();
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
        opsuchtChat$refreshPrivateHeaderActions();
        opsuchtChat$refreshServerWorkspaceWidgets();

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

                int actionsLeft = right - 101;
                int nameX = headerX + 19;
                int nameWidth = Math.max(24, actionsLeft - nameX - 4);

                PlayerIdentity identity = OpsuchtChatMinecraft.playerIdentity(partner);
                String headerName = identity == null ? partner : identity.canonicalName();
                String visibleName = opsuchtChat$clamp(this.font, headerName, nameWidth);
                graphics.text(this.font, visibleName, nameX, top + 5, TEXT, false);

                if (identity != null && identity.online()) {
                    graphics.fill(headerX + 12, top + 14, headerX + 15, top + 17, GREEN);
                }
            } else {
                graphics.text(this.font, "Unterhaltung auswählen", sidebarRight + 7, top + 5, MUTED, false);
            }
        }

        if (OpsuchtChatMinecraft.activeCategory() == ChatCategory.SERVER) {
            int sidebar = OpsuchtChatMinecraft.serverSidebarWidth();
            int sidebarRight = x + sidebar;
            graphics.fill(x + 1, top + 1, sidebarRight, messageBottom, PANEL_BG_ALT);
            graphics.fill(sidebarRight, top + 1, sidebarRight + 1, messageBottom, BORDER);

            if (!"messages".equals(opsuchtChat$serverPageId)) {
                int searchX = sidebarRight + 7;
                int searchY = top + 5;
                int searchRight = right - 6;
                graphics.fill(searchX, searchY, searchRight, searchY + 15, 0xE0181E25);
                graphics.fill(searchX, searchY, searchRight, searchY + 1, BORDER);
                graphics.fill(searchX, searchY + 14, searchRight, searchY + 15, BORDER);

                if (opsuchtChat$serverSearchInput != null && opsuchtChat$serverSearchInput.getValue().isEmpty()) {
                    graphics.text(this.font, "Befehl suchen ...", searchX + 4, searchY + 3, MUTED, false);
                }
            }
        }

        String payPartner = opsuchtChat$payMode ? OpsuchtChatMinecraft.activePrivatePartner() : null;
        if (payPartner != null) {
            String prefix = OpsuchtChatMinecraft.paymentPrefix(payPartner);
            graphics.text(this.font, prefix, x + 7, inputTop + 4, GOLD, false);
            if (this.input.getValue().isEmpty()) {
                graphics.text(this.font, "Betrag", this.input.getX(), inputTop + 4, MUTED, false);
            }
        } else if (this.input.getValue().isEmpty()) {
            graphics.text(this.font, "Nachricht schreiben …", x + 7, inputTop + 4, MUTED, false);
        }

        int contextWidth = opsuchtChat$contextWidth();
        int contextLeft = right - contextWidth - 4;
        graphics.fill(contextLeft, inputTop, right - 4, bottom - 4, PANEL_BG_ALT);
        String context = opsuchtChat$payMode
                ? "PAY"
                : (opsuchtChat$importantOnly && OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE
                        ? "★ WICHTIG"
                        : OpsuchtChatMinecraft.inputContextLabel());
        int contextX = contextLeft + Math.max(3, (contextWidth - this.font.width(context)) / 2);
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
        } else if (OpsuchtChatMinecraft.activeCategory() == ChatCategory.SERVER) {
            opsuchtChat$renderServerWorkspace(graphics, font);
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

        if (event.button() == 0
                && OpsuchtChatMinecraft.activeCategory() == ChatCategory.SERVER
                && !"messages".equals(opsuchtChat$serverPageId)) {
            for (ServerCommandHitbox hitbox : opsuchtChat$serverCommandHitboxes) {
                if (!hitbox.contains(mouseX, mouseY)) {
                    continue;
                }

                if (hitbox.favoriteToggle()) {
                    OpsuchtChatMinecraft.toggleServerCommandFavorite(hitbox.command().id());
                    cir.setReturnValue(true);
                    return;
                }

                if (hitbox.command().mode() == ServerCommandMode.PREFILL) {
                    this.input.setValue(OpsuchtChatMinecraft.prefillServerCommand(hitbox.command()));
                    this.input.moveCursorToEnd(false);
                    this.setInitialFocus(this.input);
                } else {
                    OpsuchtChatMinecraft.executeServerCommand(hitbox.command());
                    this.setInitialFocus(this.input);
                }

                cir.setReturnValue(true);
                return;
            }
        }

        if (event.button() == 0) {
            for (ServerClickActionHitbox hitbox : opsuchtChat$serverClickActionHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    defaultHandleGameClickEvent(hitbox.action().clickEvent(), this.minecraft, this);
                    cir.setReturnValue(true);
                    return;
                }
            }

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

            Style clicked = opsuchtChat$interactiveStyleAt(mouseX, mouseY);
            if (clicked != null) {
                if (this.minecraft.hasShiftDown() && clicked.getInsertion() != null) {
                    this.input.insertText(clicked.getInsertion());
                    this.setInitialFocus(this.input);
                    cir.setReturnValue(true);
                    return;
                }

                ClickEvent clickEvent = clicked.getClickEvent();
                if (clickEvent != null) {
                    defaultHandleGameClickEvent(clickEvent, this.minecraft, this);
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

        if (OpsuchtChatMinecraft.activeCategory() == ChatCategory.SERVER
                && !"messages".equals(opsuchtChat$serverPageId)) {
            String query = opsuchtChat$serverSearchInput == null ? "" : opsuchtChat$serverSearchInput.getValue();
            int count = OpsuchtChatMinecraft.serverCommandsForPage(opsuchtChat$serverPageId, query).size();
            int rows = (count + 1) / 2;
            int max = Math.max(0, rows - 1);
            int delta = scrollY > 0 ? 2 : scrollY < 0 ? -2 : 0;
            opsuchtChat$serverCommandScroll = Math.max(0, Math.min(max, opsuchtChat$serverCommandScroll + delta));
            cir.setReturnValue(true);
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
        if (OpsuchtChatMinecraft.handleLocalCommand(message)) {
            opsuchtChat$payMode = false;
            opsuchtChat$feedScroll = 0;
            ci.cancel();
            return;
        }

        String partner = OpsuchtChatMinecraft.activePrivatePartner();
        if (opsuchtChat$payMode
                && partner != null
                && OpsuchtChatMinecraft.handlePaymentInput(partner, message, addToRecent)) {
            opsuchtChat$payMode = false;
            this.input.setValue("");
            opsuchtChat$feedScroll = 0;
            ci.cancel();
            return;
        }

        if (OpsuchtChatMinecraft.handleActivePrivateInput(message, addToRecent)) {
            opsuchtChat$feedScroll = 0;
            ci.cancel();
        }
    }

    @Unique
    private void opsuchtChat$renderStructuredFeed(GuiGraphicsExtractor graphics, Font font) {
        opsuchtChat$feedHitboxes.clear();
        opsuchtChat$serverClickActionHitboxes.clear();
        opsuchtChat$messageHitboxes.clear();
        opsuchtChat$interactiveLines.clear();

        List<ChatViewMessage> messages = OpsuchtChatMinecraft.visibleFeedMessages();
        int x = OpsuchtChatMinecraft.frameX()
                + (OpsuchtChatMinecraft.activeCategory() == ChatCategory.SERVER
                        ? OpsuchtChatMinecraft.serverSidebarWidth() + 7
                        : 6);
        int right = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 6;
        int top = OpsuchtChatMinecraft.frameTop() + 4;
        int bottom = OpsuchtChatMinecraft.messageBottom() - 4;

        if (OpsuchtChatMinecraft.activeCategory() == ChatCategory.AUCTION) {
            AuctionSessionSnapshot auction = OpsuchtChatMinecraft.auctionSession();
            if (auction.seller() != null || auction.item() != null || auction.currentAmount() != null) {
                opsuchtChat$renderAuctionSummary(graphics, font, auction, x, right, top);
                top += 31;
            }
        }

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
            List<FormattedCharSequence> lines = font.split(
                    OpsuchtChatMinecraft.serverBodyComponent(entry),
                    Math.max(40, width - 12)
            );
            boolean hasActions = !OpsuchtChatMinecraft.serverClickActions(entry).isEmpty();
            return new FeedLayout(entry, lines, 15 + Math.max(1, lines.size()) * 9 + 5 + (hasActions ? 18 : 0));
        }

        if (entry.classification().category() == ChatCategory.AUCTION && entry.auctionEvent() != null) {
            List<FormattedCharSequence> lines = font.split(
                    OpsuchtChatMinecraft.publicBodyComponent(entry),
                    Math.max(40, width - 12)
            );
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
                    OpsuchtChatMinecraft.publicBodyComponent(entry),
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
            opsuchtChat$renderInteractiveLine(graphics, line, x, lineY);
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
            opsuchtChat$renderInteractiveLine(graphics, line, headerX, lineY);
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
        List<ServerClickAction> clickActions = OpsuchtChatMinecraft.serverClickActions(entry);
        int cardHeight = 13 + Math.max(1, lines.size()) * 9 + 3 + (clickActions.isEmpty() ? 0 : 18);

        graphics.fill(x, y, right, y + cardHeight, CARD_BG);
        graphics.fill(x, y, x + 2, y + cardHeight, accent);

        String label = serverLabel(event.kind());
        graphics.text(font, label, x + 6, y + 3, accent, false);
        graphics.text(font, event.title(), x + 8 + font.width(label), y + 3, TEXT, false);

        String time = TIME_FORMAT.format(entry.receivedAt());
        graphics.text(font, time, right - font.width(time) - 4, y + 3, MUTED, false);

        int lineY = y + 14;
        for (FormattedCharSequence line : lines) {
            opsuchtChat$renderInteractiveLine(graphics, line, x + 7, lineY);
            lineY += 9;
        }

        if (!clickActions.isEmpty()) {
            int actionX = x + 7;
            int actionY = lineY + 1;

            for (ServerClickAction action : clickActions) {
                int actionWidth = Math.min(
                        Math.max(36, font.width(action.label()) + 10),
                        Math.max(36, right - actionX - 5)
                );
                graphics.fill(actionX, actionY, actionX + actionWidth, actionY + 14, 0xE0253946);
                graphics.fill(actionX, actionY + 13, actionX + actionWidth, actionY + 14, accent);
                int labelX = actionX + Math.max(4, (actionWidth - font.width(action.label())) / 2);
                graphics.text(font, action.label(), labelX, actionY + 3, TEXT, false);
                opsuchtChat$serverClickActionHitboxes.add(new ServerClickActionHitbox(
                        actionX,
                        actionY,
                        actionX + actionWidth,
                        actionY + 14,
                        action
                ));

                actionX += actionWidth + 4;
                if (actionX >= right - 36) {
                    break;
                }
            }
        } else if (event.action() != null && !event.action().isBlank()) {
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
    private void opsuchtChat$renderAuctionSummary(
            GuiGraphicsExtractor graphics,
            Font font,
            AuctionSessionSnapshot auction,
            int x,
            int right,
            int y
    ) {
        int accent = auction.phase() == AuctionEventKind.SOLD ? GREEN : GOLD;
        graphics.fill(x, y, right, y + 27, 0xDC171E25);
        graphics.fill(x, y, x + 2, y + 27, accent);

        String title = auction.item() == null || auction.item().isBlank() ? "Laufende Auktion" : auction.item();
        graphics.text(font, opsuchtChat$clamp(font, title, Math.max(40, right - x - 90)), x + 7, y + 4, TEXT, false);

        String status = auction.phase() == AuctionEventKind.SOLD
                ? "VERKAUFT"
                : auctionLabel(auction.phase());
        graphics.text(font, status, right - font.width(status) - 5, y + 4, accent, false);

        String amount = auction.currentAmount() == null ? "—" : auction.currentAmount();
        String bidder = auction.currentBidder() == null ? "noch kein Gebot" : auction.currentBidder();
        String detail = amount + " · " + bidder;
        graphics.text(font, opsuchtChat$clamp(font, detail, Math.max(40, right - x - 14)), x + 7, y + 15, MUTED, false);
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
            opsuchtChat$renderInteractiveLine(graphics, line, x + 7, lineY);
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
        opsuchtChat$interactiveLines.clear();

        int x = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.sidebarWidth() + 7;
        int right = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 6;
        int top = OpsuchtChatMinecraft.frameTop() + 20;
        int bottom = OpsuchtChatMinecraft.messageBottom() - 4;
        int textWidth = Math.max(36, right - x - 24);

        List<PrivateMessageEntry> messages = opsuchtChat$privateSource();
        if (messages.isEmpty()) {
            String empty = opsuchtChat$importantOnly
                    ? "Noch keine wichtigen Nachrichten."
                    : "Noch keine Nachrichten.";
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
    private void opsuchtChat$renderInteractiveLine(
            GuiGraphicsExtractor graphics,
            FormattedCharSequence line,
            int x,
            int y
    ) {
        graphics.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR).accept(x, y, line);
        opsuchtChat$interactiveLines.add(new InteractiveTextLine(x, y, line));
    }

    @Unique
    private Style opsuchtChat$interactiveStyleAt(int mouseX, int mouseY) {
        if (opsuchtChat$interactiveLines.isEmpty()) {
            return null;
        }

        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(this.font, mouseX, mouseY)
                        .includeInsertions(this.minecraft.hasShiftDown());

        for (InteractiveTextLine line : opsuchtChat$interactiveLines) {
            finder.accept(line.x(), line.y(), line.text());
        }

        return finder.result();
    }

    @Unique
    private static String opsuchtChat$clamp(Font font, String value, int width) {
        if (value == null || value.isEmpty() || font.width(value) <= width) {
            return value == null ? "" : value;
        }

        String suffix = "...";
        return font.plainSubstrByWidth(value, Math.max(1, width - font.width(suffix))) + suffix;
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
        int contextWidth = opsuchtChat$contextWidth();
        int inputX = x + 7;

        String partner = opsuchtChat$payMode ? OpsuchtChatMinecraft.activePrivatePartner() : null;
        if (partner != null) {
            inputX += this.font.width(OpsuchtChatMinecraft.paymentPrefix(partner));
        }

        int rightEdge = x + width - contextWidth - 9;
        int inputWidth = Math.max(34, rightEdge - inputX);

        // ChatScreen's EditBox is borderless: its text baseline equals Y.
        // Align it exactly with the placeholder/prefix baseline.
        this.input.setRectangle(inputWidth, 12, inputX, this.height - 17);
    }

    @Unique
    private int opsuchtChat$contextWidth() {
        return opsuchtChat$payMode ? 38 : INPUT_CONTEXT_WIDTH;
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
                        opsuchtChat$payMode = false;
                        opsuchtChat$playerActionsOpen = false;
                        opsuchtChat$feedScroll = 0;
                        if (category != ChatCategory.SERVER) {
                            opsuchtChat$serverCommandScroll = 0;
                        }
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
    private void opsuchtChat$addServerWorkspaceWidgets() {
        opsuchtChat$serverPageButtons.clear();

        List<ServerHubPage> pages = OpsuchtChatMinecraft.serverHubPages();
        opsuchtChat$serverPageButtons.add(opsuchtChat$addServerPageButton("messages", "Meldungen"));
        for (ServerHubPage page : pages) {
            opsuchtChat$serverPageButtons.add(opsuchtChat$addServerPageButton(page.id(), page.label()));
        }

        opsuchtChat$serverSearchInput = new EditBox(
                this.font,
                0,
                0,
                100,
                13,
                Component.literal("Server-Befehle suchen")
        );
        opsuchtChat$serverSearchInput.setMaxLength(64);
        opsuchtChat$serverSearchInput.setBordered(false);
        opsuchtChat$serverSearchInput.setCanLoseFocus(true);
        opsuchtChat$serverSearchInput.setResponder(ignored -> opsuchtChat$serverCommandScroll = 0);
        opsuchtChat$serverSearchInput.visible = false;
        this.addRenderableWidget(opsuchtChat$serverSearchInput);
    }

    @Unique
    private FrameButton opsuchtChat$addServerPageButton(String id, String label) {
        FrameButton button = new FrameButton(
                0, 0, 70, 18, Component.literal(label),
                ignored -> {
                    opsuchtChat$serverPageId = id;
                    opsuchtChat$serverCommandScroll = 0;
                    opsuchtChat$serverCommandHitboxes.clear();
                    if (opsuchtChat$serverSearchInput != null) {
                        opsuchtChat$serverSearchInput.setValue("");
                    }
                    this.setInitialFocus(this.input);
                },
                () -> id.equals(opsuchtChat$serverPageId)
        );
        button.visible = false;
        this.addRenderableWidget(button);
        return button;
    }

    @Unique
    private void opsuchtChat$refreshServerWorkspaceWidgets() {
        boolean visible = OpsuchtChatMinecraft.activeCategory() == ChatCategory.SERVER;
        int x = OpsuchtChatMinecraft.frameX() + 3;
        int y = OpsuchtChatMinecraft.frameTop() + 4;
        int width = Math.max(72, OpsuchtChatMinecraft.serverSidebarWidth() - 6);

        for (int i = 0; i < opsuchtChat$serverPageButtons.size(); i++) {
            FrameButton button = opsuchtChat$serverPageButtons.get(i);
            button.visible = visible;
            if (visible) {
                button.setRectangle(width, 18, x, y + i * 19);
            }
        }

        if (opsuchtChat$serverSearchInput != null) {
            boolean searchVisible = visible && !"messages".equals(opsuchtChat$serverPageId);
            opsuchtChat$serverSearchInput.visible = searchVisible;

            if (searchVisible) {
                int searchX = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.serverSidebarWidth() + 11;
                int searchY = OpsuchtChatMinecraft.frameTop() + 8;
                int searchRight = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 10;
                opsuchtChat$serverSearchInput.setRectangle(
                        Math.max(60, searchRight - searchX),
                        12,
                        searchX,
                        searchY
                );
            } else if (this.getFocused() == opsuchtChat$serverSearchInput) {
                this.setInitialFocus(this.input);
            }
        }
    }

    @Unique
    private void opsuchtChat$renderServerWorkspace(GuiGraphicsExtractor graphics, Font font) {
        opsuchtChat$serverCommandHitboxes.clear();

        if ("messages".equals(opsuchtChat$serverPageId)) {
            opsuchtChat$renderStructuredFeed(graphics, font);
            return;
        }

        int left = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.serverSidebarWidth() + 7;
        int right = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 6;
        int top = OpsuchtChatMinecraft.frameTop() + 25;
        int bottom = OpsuchtChatMinecraft.messageBottom() - 4;

        List<ServerCommandSpec> favorites = OpsuchtChatMinecraft.favoriteServerCommands();
        int y = top;

        if (!favorites.isEmpty()) {
            graphics.text(font, "★ Favoriten", left, y, ACCENT, false);
            y += 12;

            int chipX = left;
            int shown = 0;
            for (ServerCommandSpec command : favorites) {
                if (shown >= 4) {
                    break;
                }

                int chipWidth = Math.min(58, Math.max(34, font.width(command.label()) + 12));
                if (chipX + chipWidth > right) {
                    break;
                }

                graphics.fill(chipX, y, chipX + chipWidth, y + 15, 0xD0212B34);
                graphics.text(font, command.label(), chipX + 4, y + 3, TEXT, false);
                opsuchtChat$serverCommandHitboxes.add(new ServerCommandHitbox(
                        chipX, y, chipX + chipWidth, y + 15, command, false
                ));
                chipX += chipWidth + 3;
                shown++;
            }
            y += 21;
        }

        String query = opsuchtChat$serverSearchInput == null ? "" : opsuchtChat$serverSearchInput.getValue();
        String pageTitle = query == null || query.isBlank()
                ? opsuchtChat$currentServerPageLabel()
                : "Suche";
        graphics.text(font, pageTitle, left, y, ACCENT, false);
        y += 13;

        List<ServerCommandSpec> commands =
                OpsuchtChatMinecraft.serverCommandsForPage(opsuchtChat$serverPageId, query);

        if (commands.isEmpty()) {
            graphics.text(font, "Keine Befehle gefunden.", left, y + 4, MUTED, false);
            return;
        }

        int gap = 4;
        int columnWidth = Math.max(72, (right - left - gap) / 2);
        int rowHeight = 20;
        int visibleRows = Math.max(1, (bottom - y) / rowHeight);
        int startRow = Math.min(opsuchtChat$serverCommandScroll, Math.max(0, (commands.size() + 1) / 2 - 1));
        int startIndex = startRow * 2;

        for (int row = 0; row < visibleRows; row++) {
            for (int col = 0; col < 2; col++) {
                int index = startIndex + row * 2 + col;
                if (index >= commands.size()) {
                    continue;
                }

                ServerCommandSpec command = commands.get(index);
                int rowX = left + col * (columnWidth + gap);
                int rowY = y + row * rowHeight;
                int rowRight = rowX + columnWidth;

                graphics.fill(rowX, rowY, rowRight, rowY + 17, 0xC81A222B);
                graphics.fill(rowX, rowY, rowRight, rowY + 1, 0xAA425261);

                int commandX = rowX + 4;
                graphics.text(font, command.label(), commandX, rowY + 4, TEXT, false);

                String description = opsuchtChat$clamp(
                        font,
                        command.description(),
                        Math.max(20, columnWidth - font.width(command.label()) - 25)
                );
                int descriptionX = Math.min(rowRight - 18, commandX + font.width(command.label()) + 7);
                graphics.text(font, description, descriptionX, rowY + 4, MUTED, false);

                boolean favorite = OpsuchtChatMinecraft.isServerCommandFavorite(command.id());
                String star = favorite ? "★" : "☆";
                int starX = rowRight - font.width(star) - 4;
                graphics.text(font, star, starX, rowY + 4, favorite ? GOLD : MUTED, false);

                opsuchtChat$serverCommandHitboxes.add(new ServerCommandHitbox(
                        rowX, rowY, starX - 2, rowY + 17, command, false
                ));
                opsuchtChat$serverCommandHitboxes.add(new ServerCommandHitbox(
                        starX - 2, rowY, rowRight, rowY + 17, command, true
                ));
            }
        }

        if (startRow > 0) {
            graphics.text(font, "↑", right - font.width("↑"), y, MUTED, false);
        }
        if (startIndex + visibleRows * 2 < commands.size()) {
            graphics.text(font, "↓", right - font.width("↓"), bottom - 9, MUTED, false);
        }
    }

    @Unique
    private String opsuchtChat$currentServerPageLabel() {
        for (ServerHubPage page : OpsuchtChatMinecraft.serverHubPages()) {
            if (page.id().equals(opsuchtChat$serverPageId)) {
                return page.label();
            }
        }
        return "Server";
    }

    @Unique
    private void opsuchtChat$addPrivateHeaderActions() {
        opsuchtChat$playerPrimaryActionButtons.clear();
        for (int i = 0; i < 2; i++) {
            final int slot = i;
            FrameButton button = new FrameButton(
                    0, 0, 38, 14, Component.empty(),
                    ignored -> {
                        String partner = OpsuchtChatMinecraft.activePrivatePartner();
                        List<PlayerActionSpec> actions = partner == null
                                ? List.of()
                                : OpsuchtChatMinecraft.playerActions(partner).stream()
                                        .filter(PlayerActionSpec::primary)
                                        .toList();
                        if (slot < actions.size()) {
                            opsuchtChat$handlePlayerAction(actions.get(slot));
                        }
                    },
                    () -> false
            );
            button.visible = false;
            this.addRenderableWidget(button);
            opsuchtChat$playerPrimaryActionButtons.add(button);
        }

        opsuchtChat$playerMoreActionButton = new FrameButton(
                0, 0, 20, 14, Component.literal("..."),
                ignored -> {
                    opsuchtChat$playerActionsOpen = !opsuchtChat$playerActionsOpen;
                    this.setInitialFocus(this.input);
                },
                () -> opsuchtChat$playerActionsOpen
        );
        opsuchtChat$playerMoreActionButton.visible = false;
        this.addRenderableWidget(opsuchtChat$playerMoreActionButton);

        for (int i = 0; i < 4; i++) {
            final int slot = i;
            FrameButton button = new FrameButton(
                    0, 0, 72, 16, Component.empty(),
                    ignored -> {
                        String partner = OpsuchtChatMinecraft.activePrivatePartner();
                        List<PlayerActionSpec> actions = partner == null
                                ? List.of()
                                : OpsuchtChatMinecraft.playerActions(partner).stream()
                                        .filter(action -> !action.primary())
                                        .toList();
                        if (slot < actions.size()) {
                            opsuchtChat$handlePlayerAction(actions.get(slot));
                            opsuchtChat$playerActionsOpen = false;
                        }
                    },
                    () -> false
            );
            button.visible = false;
            this.addRenderableWidget(button);
            opsuchtChat$playerOverflowActionButtons.add(button);
        }
    }

    @Unique
    private void opsuchtChat$refreshPrivateHeaderActions() {
        boolean visible = OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE
                && !opsuchtChat$importantOnly
                && OpsuchtChatMinecraft.activePrivatePartner() != null;

        String partner = OpsuchtChatMinecraft.activePrivatePartner();
        List<PlayerActionSpec> actions = visible
                ? OpsuchtChatMinecraft.playerActions(partner)
                : List.of();
        List<PlayerActionSpec> primary = actions.stream().filter(PlayerActionSpec::primary).limit(2).toList();
        List<PlayerActionSpec> overflow = actions.stream().filter(action -> !action.primary()).limit(4).toList();

        int right = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 4;
        int y = OpsuchtChatMinecraft.frameTop() + 2;
        int cursor = right;

        if (opsuchtChat$playerMoreActionButton != null) {
            boolean showMore = visible && !overflow.isEmpty();
            opsuchtChat$playerMoreActionButton.visible = showMore;
            if (showMore) {
                cursor -= 20;
                opsuchtChat$playerMoreActionButton.setRectangle(20, 14, cursor, y);
                cursor -= 2;
            } else {
                opsuchtChat$playerActionsOpen = false;
            }
        }

        for (int i = opsuchtChat$playerPrimaryActionButtons.size() - 1; i >= 0; i--) {
            FrameButton button = opsuchtChat$playerPrimaryActionButtons.get(i);
            if (visible && i < primary.size()) {
                PlayerActionSpec action = primary.get(i);
                int width = Math.max(28, Math.min(48, this.font.width(action.label()) + 10));
                cursor -= width;
                button.setRectangle(width, 14, cursor, y);
                button.setMessage(Component.literal(action.label()));
                button.setSelectedSupplier(() ->
                        action.mode() == PlayerActionMode.PAY_AMOUNT && opsuchtChat$payMode
                );
                button.visible = true;
                cursor -= 2;
            } else {
                button.visible = false;
            }
        }

        int menuX = right - 74;
        int menuY = y + 16;
        for (int i = 0; i < opsuchtChat$playerOverflowActionButtons.size(); i++) {
            FrameButton button = opsuchtChat$playerOverflowActionButtons.get(i);
            if (visible && opsuchtChat$playerActionsOpen && i < overflow.size()) {
                PlayerActionSpec action = overflow.get(i);
                button.setRectangle(72, 16, menuX, menuY + i * 17);
                button.setMessage(Component.literal(action.label()));
                button.visible = true;
            } else {
                button.visible = false;
            }
        }

        if (!visible) {
            opsuchtChat$payMode = false;
            opsuchtChat$playerActionsOpen = false;
        }
    }

    @Unique
    private void opsuchtChat$handlePlayerAction(PlayerActionSpec action) {
        if (action == null) {
            return;
        }

        switch (action.mode()) {
            case PAY_AMOUNT -> {
                opsuchtChat$payMode = true;
                this.input.setValue("");
                this.setInitialFocus(this.input);
                opsuchtChat$positionInput();
            }
            case PREFILL -> {
                opsuchtChat$payMode = false;
                this.input.setValue(OpsuchtChatMinecraft.prefillPlayerAction(action));
                this.input.moveCursorToEnd(false);
                this.setInitialFocus(this.input);
            }
            case RUN -> {
                opsuchtChat$payMode = false;
                OpsuchtChatMinecraft.executePlayerAction(action);
                this.setInitialFocus(this.input);
            }
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
                            opsuchtChat$payMode = false;
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
                            opsuchtChat$payMode = false;
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
                    opsuchtChat$playerActionsOpen = false;
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

                int rowClickWidth = Math.max(28, rowWidth - 32);
                row.setRectangle(rowClickWidth, CONVERSATION_ROW_HEIGHT - 1, x, y + i * CONVERSATION_ROW_HEIGHT);
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
            int available = Math.max(18, getWidth() - 25);
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
        net.minecraft.world.entity.player.PlayerSkin skin = OpsuchtChatMinecraft.playerSkin(player);
        if (skin != null) {
            PlayerFaceExtractor.extractRenderState(graphics, skin, x, y, size);
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
    private record InteractiveTextLine(
            int x,
            int y,
            FormattedCharSequence text
    ) {
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
    private record ServerClickActionHitbox(
            int left,
            int top,
            int right,
            int bottom,
            ServerClickAction action
    ) {
        private boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    @Unique
    private record ServerCommandHitbox(
            int left,
            int top,
            int right,
            int bottom,
            ServerCommandSpec command,
            boolean favoriteToggle
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
