package de.kingschnulli.opsuchtchat.minecraft;

import de.kingschnulli.opsuchtchat.core.Classification;
import de.kingschnulli.opsuchtchat.core.presentation.AuctionFeedEvent;
import de.kingschnulli.opsuchtchat.core.presentation.PublicChatLine;
import de.kingschnulli.opsuchtchat.core.presentation.ServerFeedEvent;
import java.time.Instant;
import net.minecraft.client.multiplayer.chat.GuiMessage;

public record ChatViewMessage(
        Instant receivedAt,
        GuiMessage message,
        Classification classification,
        PublicChatLine publicChat,
        ServerFeedEvent serverEvent,
        AuctionFeedEvent auctionEvent
) {
}
