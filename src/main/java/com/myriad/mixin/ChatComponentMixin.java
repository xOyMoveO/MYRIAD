package com.myriad.mixin;

import com.myriad.client.CombatHudOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    // Vanilla uses this page size for the drawing, hover/click capture, and scrolling.
    @Inject(method = "getLinesPerPage", at = @At("RETURN"), cancellable = true)
    private void myriad$fitChatBesideCombatHud(CallbackInfoReturnable<Integer> cir) {
        var chat = (ChatComponentAccessor)this;
        int original = cir.getReturnValueI();
        int limited = CombatHudOverlay.focusedChatLineLimit(original,
                chat.myriad$visibleChatLines().size(), chat.myriad$chatLineHeight());
        if (limited != original) cir.setReturnValue(limited);
    }
}
