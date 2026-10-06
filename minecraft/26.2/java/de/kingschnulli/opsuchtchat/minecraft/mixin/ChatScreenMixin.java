package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.core.PrivateConversation;
import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Unique
    private static final int PRIVATE_TAB_SLOTS = 4;

    @Unique
    private final Map<ChatCategory, Button> opsuchtChat$buttons = new EnumMap<>(ChatCategory.class);

    @Unique
    private final Button[] opsuchtChat$privateButtons = new Button[PRIVATE_TAB_SLOTS];

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
        int x = 2;
        int y = this.height - 47;

        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            final int slot = i;
            NoFocusButton button = new NoFocusButton(
                    x,
                    y,
                    82,
                    14,
                    Component.empty(),
                    ignored -> {
                        String partner = opsuchtChat$privatePartners[slot];
                        if (partner != null) {
                            OpsuchtChatMinecraft.selectPrivatePartner(partner);
                        }
                    }
            );
            button.visible = false;
            button.setOverrideRenderHighlightedSprite(() -> {
                String partner = opsuchtChat$privatePartners[slot];
                return partner != null && OpsuchtChatMinecraft.isPrivatePartnerSelected(partner);
            });
            this.addRenderableWidget(button);
            opsuchtChat$privateButtons[i] = button;
            x += 84;
        }
    }

    @Unique
    private void opsuchtChat$refreshPrivateButtons() {
        List<PrivateConversation> conversations = OpsuchtChatMinecraft.recentPrivateConversations();

        for (int i = 0; i < PRIVATE_TAB_SLOTS; i++) {
            Button button = opsuchtChat$privateButtons[i];
            if (button == null) {
                continue;
            }

            if (i < conversations.size()) {
                PrivateConversation conversation = conversations.get(i);
                opsuchtChat$privatePartners[i] = conversation.name();
                button.setMessage(Component.literal(OpsuchtChatMinecraft.privateTabLabel(conversation)));
                button.visible = true;
            } else {
                opsuchtChat$privatePartners[i] = null;
                button.visible = false;
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
