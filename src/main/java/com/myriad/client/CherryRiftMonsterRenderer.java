package com.myriad.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.myriad.Myriad;
import com.myriad.game.CherryRiftMonsterEntity;
import com.myriad.game.MonsterType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CherryRiftMonsterRenderer
        extends EntityRenderer<CherryRiftMonsterEntity, CherryRiftMonsterRenderer.State> {
    private final ItemModelResolver resolver;
    private final Map<MonsterType, ItemStack> models = new EnumMap<>(MonsterType.class);

    public CherryRiftMonsterRenderer(EntityRendererProvider.Context context) {
        super(context);
        resolver = context.getItemModelResolver();
        for (MonsterType type : MonsterType.values()) {
            if (type.cherryModel() == null) continue;
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Myriad.MODID,
                    "cherry_rift/" + type.getId().substring("cherry_".length())));
            models.put(type, stack);
        }
    }

    public static final class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        MonsterType.CherryModel model;
        float yaw;
        boolean hurt;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CherryRiftMonsterEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        MonsterType type = entity.variant();
        state.model = type.cherryModel();
        state.yaw = entity.getYRot(partialTick);
        state.hurt = entity.hurtTime > 0 || entity.deathTime > 0;
        resolver.updateForNonLiving(state.item, models.get(type), ItemDisplayContext.FIXED, entity);
    }

    @Override
    protected float getShadowRadius(State state) {
        return state.boundingBoxWidth * .33F;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.isInvisible) return;
        super.submit(state, pose, collector, camera);
        pose.pushPose();
        pose.translate(0F, state.model.lift(), 0F);
        pose.rotate(Axis.YP, (180F - state.yaw) * Mth.DEG_TO_RAD);
        float scale = state.model.scale();
        pose.scale(scale, scale, scale);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.pack(0, state.hurt), state.outlineColor);
        pose.popPose();
    }
}
