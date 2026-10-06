package de.kingschnulli.opsuchtchat.fabric;

import de.kingschnulli.opsuchtchat.minecraft.OpsuchtChatMinecraft;
import net.fabricmc.api.ClientModInitializer;

public final class OpsuchtChatFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OpsuchtChatMinecraft.bootstrap();
    }
}
