package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Unique
    private static final int PRIVATE_TAB_SLOTS = 6;
    @Unique
    private static final int PRIVATE_NAME_WIDTH = 74;
    @Unique
    private static final int PRIVATE_ACTION_WIDTH = 16;
    @Unique
    private static final int PRIVATE_SLOT_WIDTH = PRIVATE_NAME_WIDTH + PRIVATE_ACTION_WIDTH * 2 + 4;

    @Shadow
    protected EditBox input;

    @Unique
    private final Map<ChatCategory, Button> opsuchtChat$buttons = new EnumMap<>(ChatCategory.class);

    @Unique
    private final Button[] opsuchtChat$privateNameButtons = new Button[PRIVATE_TAB_SLOTS];
    @Unique
    private final Button[] opsuchtChat$privatePinButtons = new Button[PRIVATE_TAB_SLOTS];
    @Unique
    private final Button[] opsuchtChat$privateCloseButtons = new Button[PRIVATE_TAB_SLOTS];
    @Unique
    private final String[] opsuchtChat$privatePartners = new String[PRIVATE_TAB_SLOTS];

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void opsuchtChat$addTabs(CallbackInfo ci) {
        OpsuchtChatMinecraft.installFilter();
        opsuchtChat$buttons.clear();
        if (!OpsuchtChatMinecraft.isActive()) {
            return;
        }

        int x = 2;
        int y = this.height - 31;
        x = opsuchtChat$addButton(ChatCategory.ALL, x, y, 42);
        x = opsuchtChat$addButton(ChatCategory.MESSAGE, x, y, 46);
        x = opsuchtChat$addButton(ChatCategory.PRIVATE, x, y, 54);
        x = opsuchtChat$addButton(ChatCategory.AUCTION, x, y, 70);
        x = opsuchtChat$addButton(ChatCategory.SERVER, x, y, 58);
        opsuchtChat$addButton(ChatCategory.ADVERTISING, x, y, 72);

        opsuchtChat$addPrivateButtons();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void opsuchtChat$refreshTabs(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        for (Map.Entry<ChatCategory, Button> entry : opsuchtChat$buttons.entrySet()) {
            entry.getValue().setMessage(Component.literal(OpsuchtChatMinecraft.tabLabel(entry.getKey())));
        }

        opsuchtChat$refreshPrivateButtons();
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
    private int opsuchtChat$addButton(ChatCategory category, int x, int y, int width) {
        NoFocusButton button = new NoFocusButton(
                x,
                y,
                width,
                14,
                Component.literal(OpsuchtChatMinecraft.tabLabel(category)),
                ignored -> OpsuchtChatMinecraft.select(category)
        );
        button.setOverrideRenderHighlightedSprite(() -> OpsuchtChatMinecraft.isCategorySelected(category));
        this.addRenderableWidget(button);
        opsuchtChat$buttons.put(category, button);
        return x + width + 2;
    }

    @Unique
    private void opsuchtChat$addPrivateButtons() {
        int y = this.height - 47;

        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            final int slot = i;
            int x = 2 + i * PRIVATE_SLOT_WIDTH;

            NoFocusButton nameButton = new NoFocusButton(
                    x,
                    y,
                    PRIVATE_NAME_WIDTH,
                    14,
                    Component.empty(),
                    ignored -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        if (partner != null) {
                            OpsuchtChatMinecraft.selectPrivatePartner(partner);
                        }
                    }
            );
            nameButton.visible = false;
            nameButton.setOverrideRenderHighlightedSprite(() -> {
                String partner = opsuchtChat$privatePartners[slot];
                return partner != null && OpsuchtChatMinecraft.isPrivatePartnerSelected(partner);
            });
            this.addRenderableWidget(nameButton);
            opsuchtChat$privateNameButtons[i] = nameButton;

            NoFocusButton pinButton = new NoFocusButton(
                    x + PRIVATE_NAME_WIDTH + 1,
                    y,
                    PRIVATE_ACTION_WIDTH,
                    14,
                    Component.literal("☆"),
                    ignored -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        if (partner != null) {
                            OpsuchtChatMinecraft.togglePrivatePinned(partner);
                        }
                    }
            );
            pinButton.visible = false;
            this.addRenderableWidget(pinButton);
            opsuchtChat$privatePinButtons[i] = pinButton;

            NoFocusButton closeButton = new NoFocusButton(
                    x + PRIVATE_NAME_WIDTH + PRIVATE_ACTION_WIDTH + 2,
                    y,
                    PRIVATE_ACTION_WIDTH,
                    14,
                    Component.literal("×"),
                    ignored -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        if (partner != null) {
                            OpsuchtChatMinecraft.closePrivatePartner(partner);
                        }
                    }
            );
            closeButton.visible = false;
            this.addRenderableWidget(closeButton);
            opsuchtChat$privateCloseButtons[i] = closeButton;
        }
    }

    @Unique
    private void opsuchtChat$refreshPrivateButtons() {
        List<PrivateConversation> conversations = OpsuchtChatMinecraft.recentPrivateConversations();
        int maxVisible = Math.min(PRIVATE_TAB_SLOTS, Math.max(1, (this.width - 4) / PRIVATE_SLOT_WIDTH));

        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            Button nameButton = opsuchtChat$privateNameButtons[i];
            Button pinButton = opsuchtChat$privatePinButtons[i];
            Button closeButton = opsuchtChat$privateCloseButtons[i];
            if (nameButton == null || pinButton == null || closeButton == null) {
                continue;
            }

            if (i < conversations.size() && i < maxVisible) {
                PrivateConversation conversation = conversations.get(i);
                opsuchtChat$privatePartners[i] = conversation.name();

                nameButton.setMessage(Component.literal(OpsuchtChatMinecraft.privateTabLabel(conversation)));
                pinButton.setMessage(Component.literal(conversation.pinned() ? "★" : "☆"));
                pinButton.setOverrideRenderHighlightedSprite(conversation::pinned);

                nameButton.visible = true;
                pinButton.visible = true;
                closeButton.visible = true;
            } else {
                opsuchtChat$privatePartners[i] = null;
                nameButton.visible = false;
                pinButton.visible = false;
                closeButton.visible = false;
            }
        }
    }

    /**
     * Chat tabs are controls, not text-entry targets. Vanilla normally moves focus
     * to a clicked Button, which makes the chat input stop receiving keystrokes.
     */
    @Unique
    private static final class NoFocusButton extends Button.Plain {
        private NoFocusButton(
                int x,
                int y,
                int width,
                int height,
                Component message,
                Button.OnPress onPress
        ) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }
    }
}
