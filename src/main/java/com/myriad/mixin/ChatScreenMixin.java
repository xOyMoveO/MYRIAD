package com.myriad.mixin;

import com.myriad.client.CombatChat;
import com.myriad.client.CombatChatLayout;
import com.myriad.client.CombatHudLayout.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;
    @Shadow private CommandSuggestions commandSuggestions;
    @Unique private CombatChatLayout myriad$layout;

    @Inject(method = "init()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/CommandSuggestions;updateCommandInfo()V"))
    private void myriad$placeChatInputAtInit(CallbackInfo ci) {
        myriad$layout = null;
        myriad$placeInput();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void myriad$followHud(CallbackInfo ci) {
        if (myriad$placeInput()) commandSuggestions.updateCommandInfo();
    }

    @Unique
    private boolean myriad$placeInput() {
        CombatChatLayout previous = myriad$layout;
        myriad$layout = CombatChat.current(Minecraft.getInstance());
        if (previous == null && myriad$layout == null) return false;
        var screen = (ChatScreen)(Object)this;
        var box = myriad$layout != null ? myriad$layout.editBox()
                : new Bounds(4, screen.height - 12, screen.width - 4, 12);
        boolean moved = input.getX() != box.x() || input.getY() != box.y() || input.getWidth() != box.width();
        if (moved) {
            input.setRectangle(box.width(), box.height(), box.x(), box.y());
            input.moveCursorTo(input.getCursorPosition(), true);
        }
        return ((CombatChatLayout.SuggestionAnchor)commandSuggestions).myriad$setAnchor(myriad$layout) || moved;
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void myriad$drawCombatInput(GuiGraphicsExtractor gui, int left, int top, int right, int bottom, int color) {
        if (myriad$layout == null) gui.fill(left, top, right, bottom, color);
        else CombatChat.drawInput(gui, myriad$layout, input.isFocused());
    }

    @Redirect(method = "keyPressed", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"))
    private int myriad$ensurePageMovesOnOneLine(ChatComponent chat) {
        int lines = chat.getLinesPerPage();
        return myriad$layout == null ? lines : Math.max(2, lines);
    }

    @ModifyConstant(method = "mouseScrolled", constant = @Constant(doubleValue = 7.0))
    private double myriad$scaleScrollToVisibleHistory(double original) {
        return myriad$layout == null ? original : Math.min(original, myriad$layout.lines());
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/CommandSuggestions;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private void myriad$clipSuggestions(CommandSuggestions suggestions, GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        if (myriad$layout == null) {
            suggestions.extractRenderState(gui, mouseX, mouseY);
            return;
        }
        var layout = myriad$layout;
        int popupBottom = layout.inputTop() - 4;
        gui.enableScissor(layout.x(), layout.top(), layout.x() + layout.width(), popupBottom);
        try {
            suggestions.extractRenderState(gui, mouseX, mouseY);
        } finally {
            gui.disableScissor();
        }
    }
}
