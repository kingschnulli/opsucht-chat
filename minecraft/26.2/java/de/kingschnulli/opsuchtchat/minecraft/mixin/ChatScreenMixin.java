package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.core.ChatCategory;
import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Unique
    private final Map<ChatCategory, Button> opsuchtChat$buttons = new EnumMap<>(ChatCategory.class);

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
        x = opsuchtChat$addButton(ChatCategory.PRIVATE, x, y, 54);
        x = opsuchtChat$addButton(ChatCategory.AUCTION, x, y, 70);
        x = opsuchtChat$addButton(ChatCategory.SERVER, x, y, 58);
        opsuchtChat$addButton(ChatCategory.ADVERTISING, x, y, 72);
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
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$handleLocalCommand(String message, boolean addToRecent, CallbackInfo ci) {
        if (OpsuchtChatMinecraft.handleLocalCommand(message)) {
            ci.cancel();
        }
    }

    @Unique
    private int opsuchtChat$addButton(ChatCategory category, int x, int y, int width) {
        Button button = Button.builder(
                        Component.literal(OpsuchtChatMinecraft.tabLabel(category)),
                        ignored -> OpsuchtChatMinecraft.select(category)
                )
                .bounds(x, y, width, 14)
                .build();
        this.addRenderableWidget(button);
        opsuchtChat$buttons.put(category, button);
        return x + width + 2;
    }
}
