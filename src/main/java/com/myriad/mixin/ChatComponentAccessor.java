package com.myriad.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> myriad$visibleChatLines();

    @Invoker("getLineHeight")
    int myriad$chatLineHeight();

    @Accessor("chatScrollbarPos")
    int myriad$chatScroll();
}
