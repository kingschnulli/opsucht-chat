package de.kingschnulli.opsuchtchat.minecraft.mixin;

import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Inject(method = "setItem", at = @At("TAIL"))
    private void opsuchtChat$captureContainerUpdate(
            int slotId,
            int stateId,
            ItemStack stack,
            CallbackInfo ci
    ) {
        OpsuchtChatMinecraft.observeContainer((AbstractContainerMenu)(Object)this);
    }
}
