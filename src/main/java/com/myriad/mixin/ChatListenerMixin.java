package com.myriad.mixin;

import com.mojang.authlib.GameProfile;
import com.myriad.client.CombatChatTime;
import java.time.Instant;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
    @Redirect(method = "showMessageToPlayer", require = 2, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ChatComponent;addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"))
    private void myriad$timestampPlayerMessage(ChatComponent chat, Component text, MessageSignature signature, GuiMessageTag tag,
                                               ChatType.Bound bound, PlayerChatMessage message, Component decorated,
                                               GameProfile sender, boolean onlyShowSecure, Instant received) {
        Instant sent = signature != null && !message.hasExpiredClient(received) ? message.timeStamp() : null;
        ((CombatChatTime.Receiver)chat).myriad$addPlayerMessage(text, signature, tag, CombatChatTime.from(sent, received));
    }

    @Redirect(method = "lambda$handleDisguisedChatMessage$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ChatComponent;addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"))
    private void myriad$timestampDisguisedMessage(ChatComponent chat, Component text, MessageSignature signature, GuiMessageTag tag,
                                                  ChatType.Bound bound, Component message, Instant received) {
        ((CombatChatTime.Receiver)chat).myriad$addPlayerMessage(text, signature, tag, CombatChatTime.from(null, received));
    }
}
