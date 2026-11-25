package com.myriad.mixin;

import com.myriad.client.CombatChat;
import com.myriad.client.CombatChatLayout;
import com.myriad.client.CombatChatLines;
import com.myriad.client.CombatChatTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements CombatChatTime.Receiver {
    @Unique private static final String myriad$RENDER =
            "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow protected abstract void refreshTrimmedMessages();
    @Shadow protected abstract double getScale();
    @Shadow public abstract void addPlayerMessage(Component contents, MessageSignature signature, GuiMessageTag tag);
    @Shadow public abstract int getLinesPerPage();
    @Shadow public abstract void scrollChat(int dir);
    @Unique private CombatChatTime myriad$pendingTime;
    @Unique private CombatChatLayout myriad$layout;
    @Unique private int myriad$wrappedWidth = -1;
    @Unique private ZoneId myriad$wrappedZone;
    @Unique private boolean myriad$rendering;

    @Override
    public void myriad$addPlayerMessage(Component text, MessageSignature signature, GuiMessageTag tag, CombatChatTime time) {
        CombatChatTime previous = myriad$pendingTime;
        myriad$pendingTime = time;
        try {
            addPlayerMessage(text, signature, tag);
        } finally {
            myriad$pendingTime = previous;
        }
    }

    @Inject(method = "logChatMessage", at = @At("HEAD"))
    private void myriad$storeTime(GuiMessage message, CallbackInfo ci) {
        if (message.source() == GuiMessageSource.PLAYER) {
            CombatChatTime time = myriad$pendingTime == null ? CombatChatTime.from(null, Instant.now()) : myriad$pendingTime;
            ((CombatChatTime.Holder)(Object)message).myriad$setTime(time);
        }
    }

    @ModifyVariable(method = myriad$RENDER, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int myriad$moveVanillaBaseline(int screenHeight) {
        myriad$rendering = true;
        myriad$updateLayout();
        // Native drawing and clickable-text capture both subtract the same 40 px bottom inset.
        return myriad$layout == null ? screenHeight : myriad$layout.historyBottom() + 40;
    }

    @Inject(method = myriad$RENDER, at = @At("RETURN"))
    private void myriad$finishRender(CallbackInfo ci) {
        myriad$rendering = false;
    }

    @ModifyArg(method = myriad$RENDER, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;updatePose(Ljava/util/function/Consumer;)V"), index = 0)
    private Consumer<Matrix3x2f> myriad$moveVanillaColumn(Consumer<Matrix3x2f> nativeTransform) {
        if (myriad$layout == null) return nativeTransform;
        int x = myriad$layout.x();
        return pose -> {
            pose.translate(x, 0);
            nativeTransform.accept(pose);
        };
    }

    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
    private void myriad$fitWidth(CallbackInfoReturnable<Integer> cir) {
        if (myriad$layout != null) cir.setReturnValue(myriad$layout.messageWidth());
    }

    @Inject(method = "getLinesPerPage", at = @At("RETURN"), cancellable = true)
    private void myriad$fitPage(CallbackInfoReturnable<Integer> cir) {
        CombatChatLayout layout = myriad$rendering ? myriad$layout : CombatChat.current(minecraft);
        if (layout != null) cir.setReturnValue(layout.lines());
    }

    @Unique
    private void myriad$updateLayout() {
        CombatChatLayout previous = myriad$layout;
        myriad$layout = CombatChat.current(minecraft);
        int width = myriad$layout == null ? -1 : Mth.floor(myriad$layout.messageWidth() / getScale());
        ZoneId zone = width < 0 ? null : ZoneId.systemDefault();
        if (width == myriad$wrappedWidth && Objects.equals(zone, myriad$wrappedZone)) {
            if (chatScrollbarPos > 0 && !Objects.equals(previous, myriad$layout)) scrollChat(0);
            return;
        }
        myriad$wrappedWidth = width;
        myriad$wrappedZone = zone;
        GuiMessage anchor = chatScrollbarPos > 0 && chatScrollbarPos < trimmedMessages.size()
                ? trimmedMessages.get(chatScrollbarPos).parent() : null;
        chatScrollbarPos = 0;
        refreshTrimmedMessages();
        if (anchor == null) return;
        for (int i = 0; i < trimmedMessages.size(); i++) {
            if (trimmedMessages.get(i).parent() == anchor) {
                chatScrollbarPos = i;
                break;
            }
        }
        scrollChat(0);
    }

    @Redirect(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> myriad$wrapTimedMessage(GuiMessage message, Font font, int width) {
        CombatChatTime time = ((CombatChatTime.Holder)(Object)message).myriad$time();
        if (myriad$layout == null || time == null) return message.splitLines(font, width);
        return CombatChatLines.wrap(message, font, width, time, myriad$wrappedZone, minecraft.options.chatColors().get());
    }
}
