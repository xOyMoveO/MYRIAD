package com.myriad.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Resolve the existing item model and submit its real layers through the 26.3 item pipeline. */
public final class ClientItemRenderer {
    private ClientItemRenderer() { }
    public static void submit(LivingEntity owner, ItemStack item, ItemDisplayContext display,
            PoseStack pose, SubmitNodeCollector collector, int light) {
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(state,item,display,owner);
        state.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);
    }
}
