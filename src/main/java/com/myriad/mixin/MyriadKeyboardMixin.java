package com.myriad.mixin;

import com.myriad.MyriadClient;
import com.myriad.client.platform.KeyInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MyriadKeyboardMixin {
    @Inject(method="keyPress",at=@At("TAIL"))
    private void myriad$skillKeys(long window,int action,KeyEvent event,CallbackInfo ci) {
        if(window==Minecraft.getInstance().getWindow().handle()) MyriadClient.onKeyInput(new KeyInput(action,event));
    }
}
