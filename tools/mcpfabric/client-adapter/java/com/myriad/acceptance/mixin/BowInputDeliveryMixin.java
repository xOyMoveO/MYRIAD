package com.myriad.acceptance.mixin;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.myriad.acceptance.MyriadInputBridge;
import com.myriad.client.LuoxiaInput;
import com.myriad.client.platform.KeyInput;
import com.myriad.network.LuoxiaBowInputPayload;
import com.myriad.network.Packets;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Singleplayer acceptance only. Keep vanilla key-up, omit one callback and delay bow messages by 500 ms. */
@Mixin(value = LuoxiaInput.class, remap = false)
public abstract class BowInputDeliveryMixin {
    @Shadow private static int gesture;
    @Unique private static final boolean myriad$enabled = Boolean.getBoolean("myriad.acceptance.delayedBow");
    @Unique private static boolean myriad$omitted;
    @Unique private static final Queue<Runnable> myriad$send = new ArrayDeque<>();
    @Unique private static final Queue<Long> myriad$due = new ArrayDeque<>();

    @Inject(method = "binding", at = @At("HEAD"), cancellable = true)
    private static void myriad$omitRelease(Minecraft mc, KeyInput input, CallbackInfo ci) {
        if (!myriad$enabled || myriad$omitted || !mc.hasSingleplayerServer() || mc.gui.screen() != null
                || input.getAction() != InputConstants.RELEASE || !input.matches(mc.options.keyUse)) return;
        myriad$omitted = true;
        MyriadInputBridge.observePresentation("omitted_bow_release", new JsonObject());
        ci.cancel();
    }

    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private static void myriad$delay(int action, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (!myriad$enabled || !mc.hasSingleplayerServer()) return;
        var connection = mc.getConnection();
        var payload = new LuoxiaBowInputPayload(action, gesture);
        myriad$due.add(System.nanoTime() + 500_000_000L);
        myriad$send.add(() -> {
            if (connection == mc.getConnection()) Packets.sendToServer(payload);
        });
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private static void myriad$deliver(Minecraft mc, CallbackInfo ci) {
        while (!myriad$due.isEmpty() && System.nanoTime() >= myriad$due.element()) {
            myriad$due.remove();
            myriad$send.remove().run();
        }
    }
}
