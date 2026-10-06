package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Minecraft's own chat history/rendering, but changes one important detail:
 * category filtering may hide a line from the current view without dropping it
 * from the underlying ALL history.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Invoker("logChatMessage")
    protected abstract void opsuchtChat$logChatMessage(GuiMessage message);

    @Invoker("addMessageToDisplayQueue")
    protected abstract void opsuchtChat$addMessageToDisplayQueue(GuiMessage message);

    @Invoker("addMessageToQueue")
    protected abstract void opsuchtChat$addMessageToQueue(GuiMessage message);

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$routeMessage(
            Component contents,
            MessageSignature signature,
            GuiMessageSource source,
            GuiMessageTag tag,
            CallbackInfo ci
    ) {
        if (!OpsuchtChatMinecraft.isActive()) {
            return;
        }

        Component decorated = OpsuchtChatMinecraft.decorateClickablePlayerName(contents, source);

        Minecraft minecraft = Minecraft.getInstance();
        GuiMessage message = new GuiMessage(
                minecraft.gui.hud.getGuiTicks(),
                decorated,
                signature,
                source,
                tag
        );

        if (!OpsuchtChatMinecraft.allowedByVanilla(message)) {
            ci.cancel();
            return;
        }

        OpsuchtChatMinecraft.observe(message);
        this.opsuchtChat$logChatMessage(message);

        if (OpsuchtChatMinecraft.isVisibleInSelectedTab(message)) {
            this.opsuchtChat$addMessageToDisplayQueue(message);
        }

        this.opsuchtChat$addMessageToQueue(message);
        ci.cancel();
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void opsuchtChat$clearState(boolean history, CallbackInfo ci) {
        OpsuchtChatMinecraft.onChatCleared();
    }
}
