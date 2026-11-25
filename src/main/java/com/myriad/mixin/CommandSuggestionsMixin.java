package com.myriad.mixin;

import com.myriad.client.CombatChatLayout;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin implements CombatChatLayout.SuggestionAnchor {
    @Shadow @Final @Mutable private int suggestionLineLimit;
    @Shadow private int commandUsageWidth;
    @Unique private CombatChatLayout myriad$anchor;

    @Override
    public boolean myriad$setAnchor(CombatChatLayout layout) {
        CombatChatLayout previous = myriad$anchor;
        myriad$anchor = layout;
        suggestionLineLimit = layout == null ? 10 : layout.suggestionLineLimit();
        if (previous == null || layout == null) return previous != layout;
        return previous.inputTop() != layout.inputTop() || previous.top() != layout.top()
                || previous.x() != layout.x() || previous.width() != layout.width();
    }

    @Redirect(method = {"showSuggestions", "extractUsage"}, at = @At(value = "FIELD", target =
            "Lnet/minecraft/client/gui/screens/Screen;height:I"))
    private int myriad$anchorAboveInput(Screen screen) {
        // Both vanilla bottom-anchored boxes finish 15 pixels from their reference screen height.
        return myriad$anchor == null ? screen.height : myriad$anchor.inputTop() + 11;
    }

    @Redirect(method = "showSuggestions", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"))
    private int myriad$limitPopupWidth(int first, int second) {
        int width = Math.max(first, second);
        return myriad$anchor == null ? width : Math.min(width, myriad$anchor.width() - 2);
    }

    @Inject(method = "recomputeUsageBoxWidth", at = @At("RETURN"))
    private void myriad$limitUsageWidth(CallbackInfo ci) {
        if (myriad$anchor != null) commandUsageWidth = Math.min(commandUsageWidth, myriad$anchor.width() - 2);
    }

    @Redirect(method = {"showSuggestions", "updateUsageInfo"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/util/Mth;clamp(III)I"))
    private int myriad$anchorPopupHorizontally(int wanted, int minimum, int maximum) {
        if (myriad$anchor == null) return Mth.clamp(wanted, minimum, maximum);
        int left = myriad$anchor.x() + 1;
        return Math.clamp(wanted, left, Math.max(left, maximum + CombatChatLayout.INPUT_PADDING - 1));
    }
}
