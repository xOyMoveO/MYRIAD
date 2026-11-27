package com.myriad.client.firstperson;

import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

import static com.myriad.character.ability.AbilityVfxMaterials.*;
import static com.myriad.character.skills.xuanchen.rework.InkCastAnimation.*;

public final class InkHandChecks {
    private InkHandChecks() {}

    public static void verify(GameTestHelper h) {
        checkGrip(h, InkHandMotion.IDLE);
        var motion = new InkHandMotion();
        var casts = new HashSet<InkHandMotion.Pose>();
        for (int skill = 2; skill <= 7; skill++) {
            motion.reset();
            motion.cue(skill, RELEASE, 1, 15, 0, 20);
            var pose = motion.pose(2.5);
            h.assertTrue(casts.add(pose), "Every Ink skill uses its own first-person hand pose");
            h.assertTrue(!materials(h, motion, 2.5).isEmpty(), "Every cast has a local treatment");
            h.assertTrue(motion.pose(13).equals(InkHandMotion.IDLE), "Every first-person cast returns to idle");
            checkGrip(h, pose);
        }
        motion.reset();
        motion.cue(3, SELF, 1, 15, 0, 20);
        h.assertTrue(motion.pose(2.35).palm().z > -1.05, "The self-applied shield is pressed inward");
        for (int skill : new int[]{4, 5, 7}) {
            motion.reset();
            motion.cue(skill, AIM, 1, REFRESH_LIFE, 0, 20);
            var aimed = motion.pose(4);
            motion.cue(skill, AIM, 4, REFRESH_LIFE, 0, 20);
            h.assertTrue(aimed.equals(motion.pose(4)), "A targeting refresh preserves the current hand pose");
            motion.cue(skill, STOP, 5, 8, 0, 20);
            h.assertTrue(motion.pose(9).equals(InkHandMotion.IDLE), "Cancel returns from each distinct targeting pose");
        }
        for (int phase : new int[]{AIM, WINDUP}) {
            motion.reset();
            int skill = phase == AIM ? 4 : 6;
            motion.cue(skill, phase, 0, REFRESH_LIFE, .8, 20);
            var held = motion.pose(REFRESH_LIFE - .001);
            h.assertTrue(held.equals(motion.pose(REFRESH_LIFE))
                    && materials(h, motion, REFRESH_LIFE).isEmpty()
                    && motion.pose(REFRESH_LIFE + 4).equals(InkHandMotion.IDLE),
                    "Expired targeting and gathering poses relax from the final visible pose without a release");
            var returning = motion.pose(REFRESH_LIFE + 1);
            motion.cue(2, RELEASE, REFRESH_LIFE + 1, 15, 0, 20);
            h.assertTrue(returning.equals(motion.pose(REFRESH_LIFE + 1)), "A new cast starts at the current return pose");
        }

        motion.reset();
        motion.cue(6, WINDUP, 0, REFRESH_LIFE, 0, 20);
        motion.cue(6, WINDUP, 5, REFRESH_LIFE, .35, 20);
        motion.cue(6, WINDUP, 10, REFRESH_LIFE, .7, 20);
        h.assertTrue(motion.progress(12) > .65 && motion.pose(12).palm().x > -.33,
                "The array hands gather with reported progress");
        h.assertTrue(materials(h, motion, 12).containsAll(Set.of(INK_HD_BRUSH, INK_HD_GILT)), "Gathering reveals layered ink");
        var gathered = motion.pose(16);
        motion.cue(6, RELEASE, 16, 15, 0, 20);
        var open = motion.pose(17.4);
        h.assertTrue(open.grip().x - open.palm().x > gathered.grip().x - gathered.palm().x + .65,
                "The server opens the hands on release after the charge");
        motion.reset();
        motion.cue(6, WINDUP, 0, REFRESH_LIFE, 0, 20);
        motion.cue(6, STOP, 5, REFRESH_LIFE, 0, 20);
        h.assertTrue(!motion.active(9) && materials(h, motion, 6).isEmpty(), "Interruption settles and removes the gathering ink");

        motion.reset();
        motion.cue(5, RELEASE, 1, 15, 0, 20);
        h.assertTrue(materials(h, motion, 12.75).isEmpty(), "No activation ink before the field forms");
        motion.cue(5, ACTIVATE, 13, 6, 0, 20);
        h.assertTrue(materials(h, motion, 13.5).contains(INK_HD_GILT), "The later field cue produces the gold edge");
        motion.cue(7, RELEASE, 20, 15, 0, 20);
        motion.cue(7, ACTIVATE, 34, 6, 0, 20);
        h.assertTrue(materials(h, motion, 34.5).containsAll(Set.of(INK_HD_BRUSH, INK_HD_GILT)), "The later prison cue crosses the lock");
        motion.reset();

        motion.prepare(0, 1, 20);
        h.assertTrue(motion.combo() == -1 && materials(h, motion, 2.5).isEmpty(), "An unconfirmed click never emits ink or advances the combo");
        var basic = new HashSet<InkHandMotion.Pose>();
        for (int shot = 0; shot < 3; shot++) {
            int time = 10 + 20 * shot;
            motion.prepare(0, time - 1, 20);
            motion.cue(0, BASIC, time, 10, 0, 20);
            h.assertTrue(motion.combo() == shot && basic.add(motion.pose(time + 1.5)), "Three actual launches advance distinct gestures");
            int material = shot == 1 ? INK_HD_FIBERS : INK_HD_PAPER;
            h.assertTrue(materials(h, motion, time + 1.5).containsAll(Set.of(INK_HD_BRUSH, material)), "Every launch has the right view effect");
        }
        motion.cue(0, BASIC, 160, 10, 0, 20);
        h.assertTrue(motion.combo() == 0 && materials(h, motion, 170).isEmpty(), "Idle resets the next launch and no hand effects linger");
        System.out.println("[MYRIAD-INK-HAND] PASS: six casts, both shield directions, three right-click launch poses, synced holds and clears");
    }

    private static void checkGrip(GameTestHelper h, InkHandMotion.Pose pose) {
        for (boolean slim : new boolean[]{false, true}) {
            checkPalm(h, pose, slim, 0);
            checkPalm(h, pose, slim, .8f);
        }
    }

    private static void checkPalm(GameTestHelper h, InkHandMotion.Pose pose, boolean slim, float equip) {
        var palm = InkHandRig.arm(pose, false, slim, equip)
                .transformPosition(new Vector3f(slim ? -.5f / 16 : -1f / 16, 8f / 16, 0));
        var handle = InkHandRig.flag(pose, equip).getTranslation(new Vector3f());
        h.assertTrue(palm.distance(handle) < 1e-5, "The wrapped grip remains at the palm on both skin meshes");
    }

    private static Set<Integer> materials(GameTestHelper h, InkHandMotion motion, double at) {
        var quads = InkHandEffects.geometry(motion, at, 0);
        h.assertTrue(quads.size() < 16, "Ink hand mesh stays within a low draw budget");
        var materials = new HashSet<Integer>();
        for (var q : quads) {
            h.assertTrue(q.a().z < -.2 && q.d().z < -.2 && q.alpha() > 0 && q.alpha() <= 1,
                    "Visible lower-screen layers are finite and bounded");
            materials.add(q.material());
        }
        return materials;
    }
}
