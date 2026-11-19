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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class CherryRiftMonsterRenderer
        extends EntityRenderer<CherryRiftMonsterEntity, CherryRiftMonsterRenderer.State> {
    private final ItemModelResolver resolver;
    private final Map<MonsterType, ItemStack> models = new EnumMap<>(MonsterType.class);
    private final Map<MonsterType, CherryRiftAnimation> rigs = new EnumMap<>(MonsterType.class);

    public CherryRiftMonsterRenderer(EntityRendererProvider.Context context) {
        super(context);
        resolver = context.getItemModelResolver();
        for (MonsterType type : MonsterType.values()) {
            if (type.cherryModel() == null) continue;
            CherryRiftAnimation rig = CherryRiftAnimation.load(context.getResourceManager(), modelName(type));
            if (rig != null) rigs.put(type, rig);
        }
    }

    private static String modelName(MonsterType type) {
        return type.getId().substring("cherry_".length());
    }

    public static final class State extends EntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        ItemStackRenderState[] parts = new ItemStackRenderState[0];
        CherryRiftAnimation rig;
        CherryRiftAnimation.Clip clip;
        float clipTick;
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
        state.rig = rigs.get(type);
        state.clip = null;
        if (state.rig != null && entity.isAlive()) {
            float attackProgress = entity.attackAnimationProgress(partialTick);
            if (attackProgress >= 0F) {
                state.clip = state.rig.attack;
                state.clipTick = attackProgress * state.clip.duration();
            } else if (entity.walkAnimation.speed(partialTick) > .02F) {
                state.clip = state.rig.move;
                // Vanilla's walk clock integrates the actual displacement, including slowed movement.
                float reference = (float)Math.min(1, type.getMoveSpeed() / 3);
                state.clipTick = (entity.walkAnimation.position(partialTick) / reference) % state.clip.duration();
            }
        }
        if (state.clip == null) {
            // Renderer construction runs during reload, before item components are bound.
            ItemStack stack = models.computeIfAbsent(type, variant -> CherryRiftAnimation.paperModel(
                    Identifier.fromNamespaceAndPath(Myriad.MODID, "cherry_rift/" + modelName(variant))));
            resolver.updateForNonLiving(state.item, stack, ItemDisplayContext.FIXED, entity);
        } else {
            state.parts = state.rig.prepare(resolver, entity, state.parts);
        }
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
        int overlay = OverlayTexture.pack(0, state.hurt);
        if (state.clip == null) {
            state.item.submit(pose, collector, state.lightCoords, overlay, state.outlineColor);
        } else {
            state.rig.submit(state.clip, state.clipTick, state.parts, pose, collector,
                    state.lightCoords, overlay, state.outlineColor);
        }
        pose.popPose();
    }
}
