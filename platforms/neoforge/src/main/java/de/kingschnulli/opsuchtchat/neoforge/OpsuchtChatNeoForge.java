package de.kingschnulli.opsuchtchat.neoforge;

import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = OpsuchtChatNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class OpsuchtChatNeoForge {
    public static final String MOD_ID = "opsucht_chat";

    public OpsuchtChatNeoForge() {
        OpsuchtChatMinecraft.bootstrap();
    }
}
