package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    /**
     * Vanilla refreshes its own chat restriction predicate here. Re-install our
     * composed predicate afterwards so both vanilla restrictions and tab filtering
     * remain active.
     */
    @Inject(method = "refreshChatAbilities", at = @At("TAIL"))
    private void opsuchtChat$reinstallFilter(CallbackInfo ci) {
        OpsuchtChatMinecraft.installFilter();
    }
}
