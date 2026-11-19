package com.myriad.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.myriad.Myriad;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;

/** Hierarchical transforms for the two authored clips. Each piece uses the ordinary item model baker. */
final class CherryRiftAnimation {
    private static final Gson GSON = new Gson();
    private record Definition(BoneSource[] bones, Clip move, Clip attack) {}
    private record BoneSource(int parent, float[] pivot, String item) {}
    private record Bone(float[] pivot, Identifier model, List<Integer> children) {}
    private record Key(float tick, float[] value) {}

    record Clip(int duration, Channels[] tracks) {}

    private record Channels(Key[] rotation, Key[] position, Key[] scale) {
        void apply(PoseStack pose, float[] pivot, float time, Quaternionf quaternion) {
            if (rotation == null && position == null && scale == null) return;
            pose.translate(pivot[0] + sample(position, time, 0, 0),
                    pivot[1] + sample(position, time, 1, 0), pivot[2] + sample(position, time, 2, 0));
            if (rotation != null) {
                // Blockbench Generic uses intrinsic ZYX. Sample Euler values before composing
                // so a 360-degree wheel cycle does not collapse into the shortest arc.
                pose.rotate(quaternion.rotationZYX(
                        sample(rotation, time, 2, 0) * Mth.DEG_TO_RAD,
                        sample(rotation, time, 1, 0) * Mth.DEG_TO_RAD,
                        sample(rotation, time, 0, 0) * Mth.DEG_TO_RAD));
            }
            if (scale != null) {
                pose.scale(sample(scale, time, 0, 1), sample(scale, time, 1, 1),
                        sample(scale, time, 2, 1));
            }
            pose.translate(-pivot[0], -pivot[1], -pivot[2]);
        }
    }

    private final Bone[] bones;
    private final ItemStack[] items;
    final Clip move;
    final Clip attack;

    private CherryRiftAnimation(ResourceManager resources, Definition definition) {
        BoneSource[] source = definition.bones();
        if (source.length == 0 || definition.move().tracks().length != source.length
                || definition.attack().tracks().length != source.length) {
            throw new IllegalArgumentException("Rig and clip track counts differ");
        }
        move = definition.move();
        attack = definition.attack();
        bones = new Bone[source.length];
        items = new ItemStack[source.length];
        for (int i = 0; i < bones.length; i++) {
            BoneSource bone = source[i];
            Identifier model = bone.item() == null ? null : Identifier.parse(bone.item());
            if (model != null && resources.getResource(model.withPath("items/" + model.getPath() + ".json")).isEmpty()) {
                throw new IllegalArgumentException("Missing rig item " + model);
            }
            bones[i] = new Bone(bone.pivot(), model, new ArrayList<>());
            if (i != 0) bones[bone.parent()].children().add(i);
        }
    }

    static ItemStack paperModel(Identifier model) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, model);
        return stack;
    }

    static CherryRiftAnimation load(ResourceManager resources, String modelName) {
        Identifier id = Identifier.fromNamespaceAndPath(Myriad.MODID, "cherry_rift_animation/" + modelName + ".json");
        try (var reader = resources.getResourceOrThrow(id).openAsReader()) {
            return new CherryRiftAnimation(resources, GSON.fromJson(reader, Definition.class));
        } catch (IOException | RuntimeException error) {
            Myriad.LOGGER.warn("Could not load Cherry Rift rig {}. Using its static model.", modelName, error);
            return null;
        }
    }

    ItemStackRenderState[] prepare(ItemModelResolver resolver, Entity entity, ItemStackRenderState[] parts) {
        if (parts.length != bones.length) {
            parts = new ItemStackRenderState[bones.length];
            for (int i = 0; i < parts.length; i++) parts[i] = new ItemStackRenderState();
        }
        for (int i = 0; i < bones.length; i++) {
            Identifier model = bones[i].model();
            if (model == null) continue;
            if (items[i] == null) items[i] = paperModel(model);
            resolver.updateForNonLiving(parts[i], items[i], ItemDisplayContext.FIXED, entity);
        }
        return parts;
    }

    void submit(Clip clip, float time, ItemStackRenderState[] parts, PoseStack pose, SubmitNodeCollector collector,
                int light, int overlay, int outline) {
        submitBone(0, clip, time, parts, pose, collector, light, overlay, outline, new Quaternionf());
    }

    private void submitBone(int index, Clip clip, float time, ItemStackRenderState[] parts, PoseStack pose,
                            SubmitNodeCollector collector, int light, int overlay, int outline,
                            Quaternionf quaternion) {
        Bone bone = bones[index];
        pose.pushPose();
        try {
            clip.tracks()[index].apply(pose, bone.pivot(), time, quaternion);
            if (bone.model() != null) parts[index].submit(pose, collector, light, overlay, outline);
            for (int child : bone.children()) {
                submitBone(child, clip, time, parts, pose, collector, light, overlay, outline, quaternion);
            }
        } finally {
            pose.popPose();
        }
    }

    private static float sample(Key[] keys, float time, int axis, float defaultValue) {
        if (keys == null || keys.length == 0) return defaultValue;
        for (int i = 1; i < keys.length; i++) {
            Key next = keys[i];
            if (time <= next.tick()) {
                Key previous = keys[i - 1];
                float alpha = Mth.clamp((time - previous.tick()) / (next.tick() - previous.tick()), 0F, 1F);
                return Mth.lerp(alpha, previous.value()[axis], next.value()[axis]);
            }
        }
        return keys[keys.length - 1].value()[axis];
    }
}
