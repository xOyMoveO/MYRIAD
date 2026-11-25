package com.myriad.mixin;

import com.myriad.client.CombatChatTime;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin implements CombatChatTime.Holder {
    @Unique private CombatChatTime myriad$time;

    @Override
    public CombatChatTime myriad$time() {
        return myriad$time;
    }

    @Override
    public void myriad$setTime(CombatChatTime time) {
        myriad$time = time;
    }
}
