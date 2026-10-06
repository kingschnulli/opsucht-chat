package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps Minecraft's message storage, wrapping and click/hover semantics, while
 * allowing Opsucht Chat to place the vanilla message viewport inside its own frame.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Unique
    private ActiveTextCollector.Parameters opsuchtChat$previousClickableParameters;
    @Unique
    private boolean opsuchtChat$renderTranslated;

    @Invoker("logChatMessage")
    protected abstract void opsuchtChat$logChatMessage(GuiMessage message);

    @Invoker("addMessageToDisplayQueue")
    protected abstract void opsuchtChat$addMessageToDisplayQueue(GuiMessage message);

    @Invoker("addMessageToQueue")
    protected abstract void opsuchtChat$addMessageToQueue(GuiMessage message);

    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$frameWidth(CallbackInfoReturnable<Integer> cir) {
        if (OpsuchtChatMinecraft.isChatFrameActive()) {
            cir.setReturnValue(OpsuchtChatMinecraft.chatContentWidthLogical());
        }
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void opsuchtChat$frameHeight(CallbackInfoReturnable<Integer> cir) {
        if (OpsuchtChatMinecraft.isChatFrameActive()) {
            cir.setReturnValue(OpsuchtChatMinecraft.chatContentHeightLogical());
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD")
    )
    private void opsuchtChat$pushFrameTransform(
            GuiGraphicsExtractor graphics,
            Font font,
            int ticks,
            int mouseX,
            int mouseY,
            ChatComponent.DisplayMode displayMode,
            boolean changeCursorOnInsertions,
            CallbackInfo ci
    ) {
        if (OpsuchtChatMinecraft.isChatFrameActive()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(OpsuchtChatMinecraft.chatRenderOffsetX(), 0.0F);
            opsuchtChat$renderTranslated = true;
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("RETURN")
    )
    private void opsuchtChat$popFrameTransform(
            GuiGraphicsExtractor graphics,
            Font font,
            int ticks,
            int mouseX,
            int mouseY,
            ChatComponent.DisplayMode displayMode,
            boolean changeCursorOnInsertions,
            CallbackInfo ci
    ) {
        if (opsuchtChat$renderTranslated) {
            graphics.pose().popMatrix();
            opsuchtChat$renderTranslated = false;
        }
    }

    @Inject(
            method = "captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("HEAD")
    )
    private void opsuchtChat$pushClickableTransform(
            ActiveTextCollector output,
            int screenHeight,
            int ticks,
            ChatComponent.DisplayMode displayMode,
            CallbackInfo ci
    ) {
        if (!OpsuchtChatMinecraft.isChatFrameActive()) {
            return;
        }

        opsuchtChat$previousClickableParameters = output.defaultParameters();
        Matrix3x2f pose = new Matrix3x2f(opsuchtChat$previousClickableParameters.pose());
        pose.translate(OpsuchtChatMinecraft.chatRenderOffsetX(), 0.0F);
        output.defaultParameters(opsuchtChat$previousClickableParameters.withPose(pose));
    }

    @Inject(
            method = "captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("RETURN")
    )
    private void opsuchtChat$popClickableTransform(
            ActiveTextCollector output,
            int screenHeight,
            int ticks,
            ChatComponent.DisplayMode displayMode,
            CallbackInfo ci
    ) {
        if (opsuchtChat$previousClickableParameters != null) {
            output.defaultParameters(opsuchtChat$previousClickableParameters);
            opsuchtChat$previousClickableParameters = null;
        }
    }

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
