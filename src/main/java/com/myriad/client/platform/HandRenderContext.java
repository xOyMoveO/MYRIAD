package com.myriad.client.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** The actual 26.3 per-hand submit context; custom views submit into Minecraft's collector. */
public final class HandRenderContext {
    private final InteractionHand hand;
    private final ItemStack item;
    private final PoseStack pose;
    private final SubmitNodeCollector collector;
    private final int light;
    private final float partialTick, equipProgress, swingProgress;
    private boolean canceled;
    public HandRenderContext(InteractionHand hand, ItemStack item, PoseStack pose,
            SubmitNodeCollector collector, int light, float partialTick, float equipProgress, float swingProgress) {
        this.hand=hand; this.item=item; this.pose=pose; this.collector=collector;
        this.light=light; this.partialTick=partialTick; this.equipProgress=equipProgress; this.swingProgress=swingProgress;
    }
    public InteractionHand getHand() { return hand; }
    public ItemStack getItemStack() { return item; }
    public PoseStack getPoseStack() { return pose; }
    public SubmitNodeCollector getSubmitNodeCollector() { return collector; }
    public int getPackedLight() { return light; }
    public float getPartialTick() { return partialTick; }
    public float getEquipProgress() { return equipProgress; }
    public float getSwingProgress() { return swingProgress; }
    public boolean isCanceled() { return canceled; }
    public void setCanceled(boolean canceled) { this.canceled=canceled; }
}
