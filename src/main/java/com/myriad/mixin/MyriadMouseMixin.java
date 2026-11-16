package com.myriad.mixin;

import com.myriad.MyriadClient;
import com.myriad.client.platform.KeyInput;
import com.myriad.client.platform.MouseInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MyriadMouseMixin {
    @Inject(method="onButton",at=@At("HEAD"),cancellable=true)
    private void myriad$placementClick(long window,MouseButtonInfo button,int action,CallbackInfo ci) {
        if(window!=Minecraft.getInstance().getWindow().handle()) return;
        MouseInput event=new MouseInput(button.button(),action);
        MyriadClient.onMouseButton(event);
        if(event.isCanceled())ci.cancel();
    }
    @Inject(method="onButton",at=@At("TAIL"))
    private void myriad$mouseBoundSkill(long window,MouseButtonInfo button,int action,CallbackInfo ci) {
        if(window==Minecraft.getInstance().getWindow().handle()) MyriadClient.onKeyInput(new KeyInput(action,button.button()));
    }
}
