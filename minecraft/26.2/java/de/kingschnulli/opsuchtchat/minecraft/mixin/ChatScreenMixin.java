package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import de.kingschnulli.opsuchtchat.core.PrivateMessageDirection;
import de.kingschnulli.opsuchtchat.core.PrivateMessageEntry;
import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
    private static final int INPUT_CONTEXT_WIDTH = 80;
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
            graphics.text(this.font, "Private Nachrichten", x + 6, top + 5, TEXT, false);

            String partner = OpsuchtChatMinecraft.activePrivatePartner();
            if (partner != null) {
                int headerX = sidebarRight + 7;
                opsuchtChat$renderFace(graphics, partner, headerX, top + 3, 14);
                graphics.text(this.font, "PN · " + partner, headerX + 19, top + 5, TEXT, false);

                PlayerInfo info = OpsuchtChatMinecraft.playerInfo(partner);
                if (info != null) {
                    graphics.fill(headerX + 12, top + 14, headerX + 15, top + 17, 0xFF36D45A);
                }
            } else {
                graphics.text(this.font, "Unterhaltung auswählen", sidebarRight + 8, top + 5, MUTED, false);
            }
        }

        if (this.input.getValue().isEmpty()) {
            graphics.text(this.font, "Nachricht schreiben …", x + 8, inputTop + 4, MUTED, false);
        }

        int contextLeft = right - INPUT_CONTEXT_WIDTH - 4;
        graphics.fill(contextLeft, inputTop, right - 4, bottom - 4, PANEL_BG_ALT);
        String context = OpsuchtChatMinecraft.inputContextLabel();
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
        if (OpsuchtChatMinecraft.isChatFrameActive()
                && OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE) {
            opsuchtChat$renderPrivateTranscript(graphics, font);
            return;
        }

        chat.extractRenderState(graphics, font, ticks, mouseX, mouseY, displayMode, changeCursorOnInsertions);
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
        if (OpsuchtChatMinecraft.isChatFrameActive()
                && OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE) {
            return;
        }
        chat.captureClickableText(collector, screenHeight, ticks, displayMode);
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
    private void opsuchtChat$toggleImportantMessage(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()
                || OpsuchtChatMinecraft.activeCategory() != ChatCategory.PRIVATE
                || event.button() != 1) {
            return;
        }

        int mouseX = (int)event.x();
        int mouseY = (int)event.y();
        for (PrivateMessageHitbox hitbox : opsuchtChat$messageHitboxes) {
            if (hitbox.contains(mouseX, mouseY)) {
                boolean important = !OpsuchtChatMinecraft.isImportant(hitbox.message());
                OpsuchtChatMinecraft.setImportant(hitbox.message(), important);
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$openPrivateFromPlayerName(
            Style clicked,
            boolean allowInsertions,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (OpsuchtChatMinecraft.handlePlayerNameClick(clicked)) {
            this.setInitialFocus(this.input);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$handleLocalCommand(String message, boolean addToRecent, CallbackInfo ci) {
        if (OpsuchtChatMinecraft.handleLocalCommand(message)
                || OpsuchtChatMinecraft.handleActivePrivateInput(message, addToRecent)) {
            ci.cancel();
        }
    }

    @Unique
    private void opsuchtChat$renderPrivateTranscript(GuiGraphicsExtractor graphics, Font font) {
        opsuchtChat$messageHitboxes.clear();
        String partner = OpsuchtChatMinecraft.activePrivatePartner();
        int x = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.sidebarWidth() + 8;
        int right = OpsuchtChatMinecraft.frameX() + OpsuchtChatMinecraft.frameWidth() - 6;
        int top = OpsuchtChatMinecraft.frameTop() + 20;
        int bottom = OpsuchtChatMinecraft.messageBottom() - 4;
        int textWidth = Math.max(50, right - x - 26);

        if (partner == null) {
            graphics.text(font, "Wähle links eine Unterhaltung aus.", x, top + 8, MUTED, false);
            return;
        }

        List<PrivateMessageEntry> messages = OpsuchtChatMinecraft.privateMessages(partner);
        if (messages.isEmpty()) {
            graphics.text(font, "Noch keine lokal gespeicherten Nachrichten.", x, top + 8, MUTED, false);
            return;
        }

        List<PrivateMessageLayout> visible = new ArrayList<>();
        int available = Math.max(20, bottom - top);
        int used = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            PrivateMessageEntry entry = messages.get(i);
            List<FormattedCharSequence> lines = font.split(Component.literal(entry.body()), textWidth);
            int height = 13 + Math.max(1, lines.size()) * 9 + 4;

            if (!visible.isEmpty() && used + height > available) {
                break;
            }

            visible.add(0, new PrivateMessageLayout(entry, lines, height));
            used += height;
        }

        int y = top;
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
            int timeX = Math.min(right - font.width(time), x + 22 + font.width(sender));
            graphics.text(font, time, timeX, y, MUTED, false);

            boolean important = OpsuchtChatMinecraft.isImportant(entry);
            if (important) {
                graphics.text(font, "★", right - font.width("★"), y, INCOMING, false);
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
    }

    @Unique
    private static String firstLetter(String value) {
        if (value == null || value.isBlank()) {
            return "?";
        }
        return value.substring(0, 1).toUpperCase();
    }

    @Unique
    private void opsuchtChat$positionInput() {
        int x = OpsuchtChatMinecraft.frameX();
        int width = OpsuchtChatMinecraft.frameWidth();
        int inputWidth = Math.max(60, width - INPUT_CONTEXT_WIDTH - 18);
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
                    ignored -> OpsuchtChatMinecraft.select(category),
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

        int required = 0;
        int[] widths = new int[count];
        int index = 0;
        for (ChatCategory category : ChatCategory.values()) {
            String label = OpsuchtChatMinecraft.tabLabel(category);
            widths[index] = Math.max(26, this.font.width(label) + 7);
            required += widths[index];
            index++;
        }

        int extraPerTab = Math.max(0, (usable - required) / count);
        index = 0;
        for (ChatCategory category : ChatCategory.values()) {
            FrameButton button = opsuchtChat$buttons.get(category);
            int width = widths[index++] + extraPerTab;
            if (x + width > OpsuchtChatMinecraft.frameX() + frameWidth - 3) {
                width = Math.max(24, OpsuchtChatMinecraft.frameX() + frameWidth - 3 - x);
            }
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
                            OpsuchtChatMinecraft.selectPrivatePartner(partner);
                        }
                    },
                    () -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        return partner != null && OpsuchtChatMinecraft.isPrivatePartnerSelected(partner);
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
    private void opsuchtChat$refreshPrivateSidebar() {
        boolean visible = OpsuchtChatMinecraft.activeCategory() == ChatCategory.PRIVATE;
        List<PrivateConversation> conversations = OpsuchtChatMinecraft.recentPrivateConversations();

        int x = OpsuchtChatMinecraft.frameX() + 3;
        int y = OpsuchtChatMinecraft.frameTop() + 18;
        int sidebarWidth = OpsuchtChatMinecraft.sidebarWidth();
        int availableSlots = Math.max(0, (OpsuchtChatMinecraft.messageBottom() - y - 2) / CONVERSATION_ROW_HEIGHT);
        int maxVisible = Math.min(PRIVATE_TAB_SLOTS, availableSlots);
        int rowWidth = Math.max(62, sidebarWidth - 6);

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
            int available = Math.max(20, getWidth() - 23 - actionReserve);
            String name = clamp(font, conversation.name(), available);
            graphics.text(font, name, textX, getY() + 3, TEXT, false);

            String preview = conversation.preview() == null ? "" : conversation.preview();
            int previewWidth = Math.max(18, getWidth() - 27);
            if (conversation.lastMessageAt() != null
                    && !conversation.lastMessageAt().equals(java.time.Instant.EPOCH)) {
                String time = TIME_FORMAT.format(conversation.lastMessageAt());
                int timeWidth = font.width(time);
                previewWidth = Math.max(18, previewWidth - timeWidth - 3);
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
    private record PrivateMessageLayout(
            PrivateMessageEntry entry,
            List<FormattedCharSequence> lines,
            int height
    ) {
    }
}
