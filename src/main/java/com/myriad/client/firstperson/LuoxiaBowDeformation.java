package com.myriad.client.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import static com.myriad.client.firstperson.LuoxiaBowPrototype.*;

/** First-person prototype. Interpolate corresponding faces of the four authored bow shapes. */
final class LuoxiaBowDeformation {
    private static final Identifier[] IDS = {
            model("guixia_bow"), model("guixia_bow_pulling_0"),
            model("guixia_bow_pulling_1"), model("guixia_bow_pulling_2")
    };
    private static final ItemModel[] models = new ItemModel[DRAW.length];
    private static final ItemQuads[] shapes = new ItemQuads[DRAW.length];
    private static final int[] NO_TINT = {};
    private static int[] servingCorners;
    private static ItemQuads lastShape;
    private static float lastDraw, lastOffset;

    private LuoxiaBowDeformation() {}

    private static Identifier model(String name) {
        return Identifier.fromNamespaceAndPath("myriad", name + "_held");
    }

    // Capture the same ItemQuads passed by the vanilla item model to its render layer.
    private static final class Capture extends ItemStackRenderState {
        ItemQuads quads;

        @Override public LayerRenderState newLayer() {
            return new LayerRenderState() {
                @Override public void setQuads(ItemQuads value) { quads = value; }
            };
        }
    }

    private static void prepare(LivingEntity player) {
        var mc = Minecraft.getInstance();
        boolean changed = false;
        for (int i = 0; i < IDS.length; i++) {
            var model = mc.getModelManager().getItemModel(IDS[i]);
            if (models[i] == model) continue;
            models[i] = model;
            changed = true;
            var capture = new Capture();
            if (model instanceof CuboidItemModelWrapper)
                model.update(capture, player.getMainHandItem(), mc.getItemModelResolver(), ItemDisplayContext.NONE,
                        (ClientLevel) player.level(), player, 0);
            shapes[i] = capture.quads;
        }
        if (!changed) return;
        lastShape = null;
        servingCorners = null;
        if (shapes[0] == null) return;
        int count = shapes[0].all().size();
        for (var shape : shapes) if (shape == null || shape.all().size() != count) return;

        // Both string ends and the serving meet here. Other vertices, including limb tips, stay fixed during vibration.
        int[] corners = new int[count];
        boolean found = false;
        for (int face = 0; face < count; face++) {
            var quad = shapes[0].all().get(face);
            for (int k = 0; k < 4; k++) {
                var v = quad.position(k);
                if (Math.abs(v.x() - .5f - SERVING[0]) < .01f && Math.abs(v.y() - .5f) < .02f
                        && Math.abs(v.z() - .5f) < .01f) {
                    corners[face] |= 1 << k;
                    found = true;
                }
            }
        }
        if (found) servingCorners = corners;
    }

    private static long uv(long a, long b, float t) {
        if (a == b) return a;
        return UVPair.pack(lerp(UVPair.unpackU(a), UVPair.unpackU(b), t),
                lerp(UVPair.unpackV(a), UVPair.unpackV(b), t));
    }

    private static float lerp(float a, float b, float t) {
        return Math.fma(t, b - a, a);
    }

    private static Vector3fc corner(BakedQuad a, BakedQuad b, int k, float t, int mask, float offset) {
        var from = a.position(k);
        var to = b.position(k);
        float shift = (mask & (1 << k)) == 0 ? 0 : offset;
        if (shift == 0 && (from == to || t == 0)) return from;
        if (shift == 0 && t == 1) return to;
        return new Vector3f(lerp(from.x(), to.x(), t) + shift, lerp(from.y(), to.y(), t), lerp(from.z(), to.z(), t));
    }

    private static ItemQuads sample(float draw, float offset) {
        int stage = span(draw);
        float t = fraction(draw, stage);
        var from = shapes[stage];
        var to = shapes[stage + 1];
        if (offset == 0 && t == 0) return from;
        if (offset == 0 && t == 1) return to;
        if (draw == lastDraw && offset == lastOffset && lastShape != null) return lastShape;
        var faces = new ArrayList<BakedQuad>(from.all().size());
        for (int i = 0; i < from.all().size(); i++) {
            var a = from.all().get(i);
            var b = to.all().get(i);
            int mask = servingCorners[i];
            if ((offset == 0 || mask == 0) && (a == b || t == 0 || t == 1)) {
                faces.add(t == 1 ? b : a);
                continue;
            }
            var material = t < .5f ? a : b;
            faces.add(new BakedQuad(corner(a,b,0,t,mask,offset), corner(a,b,1,t,mask,offset),
                    corner(a,b,2,t,mask,offset), corner(a,b,3,t,mask,offset),
                    uv(a.packedUV0(),b.packedUV0(),t), uv(a.packedUV1(),b.packedUV1(),t),
                    uv(a.packedUV2(),b.packedUV2(),t), uv(a.packedUV3(),b.packedUV3(),t),
                    material.direction(),material.materialInfo()));
        }
        lastDraw = draw;
        lastOffset = offset;
        lastShape = ItemQuads.split(faces);
        return lastShape;
    }

    static void submit(LivingEntity player, float draw, float stringOffset, PoseStack pose, SubmitNodeCollector collector, int light) {
        prepare(player);
        if (servingCorners == null) {
            // A resource pack can replace a keyframe with a different topology or a non-cuboid item.
            int i = span(draw);
            var mc = Minecraft.getInstance();
            var state = new ItemStackRenderState();
            models[i + (fraction(draw,i) >= .5f ? 1 : 0)].update(state, player.getMainHandItem(), mc.getItemModelResolver(),
                    ItemDisplayContext.NONE, (ClientLevel) player.level(), player, 0);
            state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            return;
        }
        pose.pushPose();
        try {
            // ItemTransform.NONE centers baked corner coordinates on the true grip.
            pose.translate(-.5f, -.5f, -.5f);
            var foil = player.getMainHandItem().hasFoil() ? ItemStackRenderState.FoilType.STANDARD : ItemStackRenderState.FoilType.NONE;
            collector.submitItem(pose, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, 0,
                    NO_TINT, sample(draw, stringOffset), foil);
        } finally {
            pose.popPose();
        }
    }
}
