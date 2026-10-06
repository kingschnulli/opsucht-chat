package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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
    private static final int TAB_GAP = 2;
    @Unique
    private static final int TAB_HEIGHT = 16;
    @Unique
    private static final int INPUT_CONTEXT_WIDTH = 84;
    @Unique
    private static final int PRIVATE_TAB_SLOTS = 6;

    @Unique
    private static final int PANEL_BG = 0xD014181F;
    @Unique
    private static final int PANEL_BG_ALT = 0xD81A2028;
    @Unique
    private static final int BORDER = 0xD064707C;
    @Unique
    private static final int ACCENT = 0xFF31A8E6;
    @Unique
    private static final int TEXT = 0xFFF3F6F8;
    @Unique
    private static final int MUTED = 0xFF9AA6B2;

    @Shadow
    protected EditBox input;

    @Unique
    private final Map<ChatCategory, FrameButton> opsuchtChat$buttons = new EnumMap<>(ChatCategory.class);

    @Unique
    private final FrameButton[] opsuchtChat$privateNameButtons = new FrameButton[PRIVATE_TAB_SLOTS];
    @Unique
    private final FrameButton[] opsuchtChat$privatePinButtons = new FrameButton[PRIVATE_TAB_SLOTS];
    @Unique
    private final FrameButton[] opsuchtChat$privateCloseButtons = new FrameButton[PRIVATE_TAB_SLOTS];
    @Unique
    private final String[] opsuchtChat$privatePartners = new String[PRIVATE_TAB_SLOTS];

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

    @Inject(method = "removed", at = @At("TAIL"))
    private void opsuchtChat$restoreHudWrapping(CallbackInfo ci) {
        OpsuchtChatMinecraft.installFilter();
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
            graphics.text(this.font, "Private Nachrichten", x + 7, top + 6, TEXT, false);

            String partner = OpsuchtChatMinecraft.activePrivatePartner();
            if (partner != null) {
                graphics.text(this.font, "PN · " + partner, sidebarRight + 9, top + 6, TEXT, false);
            }
        }

        if (this.input.getValue().isEmpty()) {
            graphics.text(this.font, "Nachricht schreiben …", x + 9, inputTop + 4, MUTED, false);
        }

        int contextLeft = right - INPUT_CONTEXT_WIDTH - 5;
        graphics.fill(contextLeft, inputTop, right - 5, bottom - 4, PANEL_BG_ALT);
        String context = OpsuchtChatMinecraft.inputContextLabel();
        int contextX = contextLeft + Math.max(4, (INPUT_CONTEXT_WIDTH - this.font.width(context)) / 2);
        graphics.text(this.font, context, contextX, inputTop + 4, TEXT, false);
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
    private void opsuchtChat$positionInput() {
        int x = OpsuchtChatMinecraft.frameX();
        int width = OpsuchtChatMinecraft.frameWidth();
        int inputWidth = Math.max(80, width - INPUT_CONTEXT_WIDTH - 20);
        this.input.setRectangle(inputWidth, 13, x + 8, this.height - 20);
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

        int x = OpsuchtChatMinecraft.frameX() + 4;
        int frameWidth = OpsuchtChatMinecraft.frameWidth();
        int count = ChatCategory.values().length;
        int usable = frameWidth - 8 - (count - 1) * TAB_GAP;
        int width = Math.max(42, usable / count);
        int y = this.height - 40;

        for (ChatCategory category : ChatCategory.values()) {
            FrameButton button = opsuchtChat$buttons.get(category);
            button.setRectangle(width, TAB_HEIGHT, x, y);
            button.setMessage(Component.literal(OpsuchtChatMinecraft.tabLabel(category)));
            x += width + TAB_GAP;
        }
    }

    @Unique
    private void opsuchtChat$addPrivateSidebarButtons() {
        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            final int slot = i;

            FrameButton name = new FrameButton(
                    0, 0, 60, 18, Component.empty(),
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
            name.visible = false;
            this.addRenderableWidget(name);
            opsuchtChat$privateNameButtons[i] = name;

            FrameButton pin = new FrameButton(
                    0, 0, 14, 18, Component.literal("☆"),
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
                    0, 0, 14, 18, Component.literal("×"),
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

        int x = OpsuchtChatMinecraft.frameX() + 4;
        int y = OpsuchtChatMinecraft.frameTop() + 20;
        int sidebarWidth = OpsuchtChatMinecraft.sidebarWidth();
        int availableSlots = Math.max(0, (OpsuchtChatMinecraft.messageBottom() - y - 4) / 20);
        int maxVisible = Math.min(PRIVATE_TAB_SLOTS, availableSlots);
        int nameWidth = Math.max(46, sidebarWidth - 38);

        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            FrameButton name = opsuchtChat$privateNameButtons[i];
            FrameButton pin = opsuchtChat$privatePinButtons[i];
            FrameButton close = opsuchtChat$privateCloseButtons[i];

            if (visible && i < conversations.size() && i < maxVisible) {
                PrivateConversation conversation = conversations.get(i);
                opsuchtChat$privatePartners[i] = conversation.name();

                name.setRectangle(nameWidth, 18, x, y + i * 20);
                name.setMessage(Component.literal(OpsuchtChatMinecraft.privateTabLabel(conversation)));

                pin.setRectangle(16, 18, x + nameWidth + 1, y + i * 20);
                pin.setMessage(Component.literal(conversation.pinned() ? "★" : "☆"));
                pin.setSelectedSupplier(conversation::pinned);

                close.setRectangle(16, 18, x + nameWidth + 18, y + i * 20);

                name.visible = true;
                pin.visible = true;
                close.visible = true;
            } else {
                opsuchtChat$privatePartners[i] = null;
                name.visible = false;
                pin.visible = false;
                close.visible = false;
            }
        }
    }

    @Unique
    private static final class FrameButton extends Button.Plain {
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
                    ? 0xE0244C66
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
}
