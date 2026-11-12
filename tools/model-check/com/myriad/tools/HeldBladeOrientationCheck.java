package com.myriad.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.List;
import net.minecraft.client.resources.model.cuboid.*;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

/** Real vanilla transform: a quarter turn around the shaft, not world Euler Y. */
public final class HeldBladeOrientationCheck {
    private static void require(boolean ok, String text) {
        if (!ok) throw new IllegalStateException(text);
    }

    private static CuboidModel model(Path path) throws Exception {
        try (var reader = Files.newBufferedReader(path)) { return CuboidModel.fromStream(reader); }
    }

    private static Vector3f axis(PoseStack.Pose pose, float x, float y, float z) {
        return pose.pose().transformDirection(new Vector3f(x, y, z)).normalize();
    }

    private static void check(Path before, Path after, boolean screenshotGrip) throws Exception {
        var oldModel = model(before);
        var newModel = model(after);
        for (var context : List.of(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
            boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            var a = new PoseStack.Pose();
            var b = new PoseStack.Pose();
            var oldTransform = oldModel.transforms().getTransform(context);
            var newTransform = newModel.transforms().getTransform(context);
            oldTransform.apply(left, a);
            newTransform.apply(left, b);
            if (screenshotGrip && (context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                require(axis(b, 0, 1, 0).distance(new Vector3f(0, -1, 0)) < 2E-6,
                        "Night dagger uses the user's explicit screenshot grip");
                require(Math.abs(axis(b, 0, 0, 1).x) > .999F, "Night dagger broad face is sideways");
                require(Math.abs(newTransform.translation().y() + 1.577F / 16) < 1E-6,
                        "Night dagger palm offset is the player's -1.577 pixels");
                continue;
            }
            require(axis(a, 0, 1, 0).distance(axis(b, 0, 1, 0)) < 2E-6,
                    "Shaft direction unchanged: " + after + " / " + context);
            require(axis(a, 1, 0, 0).distance(axis(b, 0, 0, 1)) < 2E-6,
                    "Broad face now points sideways, not forwards: " + context);
            require(axis(a, 0, 0, -1).distance(axis(b, 1, 0, 0)) < 2E-6,
                    "Same cutting edge points forwards on both physical hands: " + context);
            var oldGrip = a.pose().transformPosition(new Vector3f(.5F, .5F, .5F));
            var newGrip = b.pose().transformPosition(new Vector3f(.5F, .5F, .5F));
            require(oldGrip.distance(newGrip) < 2E-6, "Handle stays at palm pivot");
            require(oldTransform.scale().equals(newTransform.scale()), "Keep weapon size unchanged");
            require(oldTransform.translation().equals(newTransform.translation()), "Keep grip offset unchanged");
        }
        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED)) {
            var a = oldModel.transforms().getTransform(context);
            var b = newModel.transforms().getTransform(context);
            require(a.rotation().equals(b.rotation()) && a.translation().equals(b.translation()) && a.scale().equals(b.scale()),
                    "Non-hand context unchanged: " + context);
        }
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(args[0]), task = Path.of(args[1]);
        check(task.resolve("backup/lingyuan_item_before.json"),
                project.resolve("src/main/resources/assets/myriad/models/item/lingyuan_sword.json"), false);
        check(task.resolve("backup/night_item_before.json"),
                Path.of("E:/mod/role_assets/yingxiao/20261106/reverse_grip/assets/myriad/models/item/yingxiao_shadow_dagger.json"), true);
        System.out.println("[MYRIAD-HELD-EDGE] PASS: actual MC26.2 transforms; Lingyuan and night first-person local shaft twist; night third-person exact player screenshot grip; both hands; non-hand contexts unchanged");
    }
}
