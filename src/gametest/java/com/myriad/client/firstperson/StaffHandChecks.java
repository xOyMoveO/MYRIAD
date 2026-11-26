package com.myriad.client.firstperson;

import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector3f;
import java.util.HashSet;
import static com.myriad.character.ability.StaffHandCues.*;

public final class StaffHandChecks {
    private StaffHandChecks() {}

    public static void verify(GameTestHelper h) {
        for (boolean frost : new boolean[] {true, false}) {
            var m = new StaffHandMotion(frost);
            var poses = new HashSet<StaffHandMotion.Pose>();
            var idle = m.pose(0);
            for (int skill = 2; skill <= 7; skill++) {
                m.reset();
                m.cue(skill, CAST, 0, 12, 0, 12);
                h.assertTrue(m.pose(0).equals(idle), "Starts continuously from the current pose");
                poses.add(m.pose(1.5));
                for (double age = 0; age <= 12; age += .25) {
                    var pose = m.pose(age);
                    for (boolean mirror : new boolean[] {false, true}) {
                        for (boolean slim : new boolean[] {false, true}) {
                            var wrist = StaffHandRig.arm(pose, mirror, false, slim, .21f);
                            float x = (slim ? .5f : 1f) / 16 * (mirror ? -1 : 1);
                            var actual = wrist.transformPosition(new Vector3f(-x, .5f, 0));
                            var item = StaffHandRig.item(pose, frost, mirror, .21f).getTranslation(new Vector3f());
                            h.assertTrue(actual.distance(item) < 1e-5, "The item's origin stays on the palm");
                            h.assertTrue(item.z < -.9f && item.y < -.4f, "Grip stays below the center of view");
                        }
                    }
                }
                h.assertTrue(m.pose(12).equals(idle), "Release returns to idle");
            }
            h.assertTrue(poses.size() == 6, "Distinct active poses");
            for (int i = 0; i < 4; i++) {
                m.cue(0, BASIC, 16 + i * 12, 9, 0, 12);
                h.assertTrue(m.combo() == i % 3, "Three confirmed launches rotate the combo");
            }
            m.cue(0, BASIC, 120, 9, 0, 12);
            h.assertTrue(m.combo() == 0, "Pause restarts combo");
            m.cue(4, AIM, 133, 7, 0, 12);
            var ready = m.pose(136);
            h.assertTrue(!ready.equals(idle), "Aim has its own pose");
            m.cue(4, STOP, 136, 7, 0, 12);
            h.assertTrue(m.pose(136).equals(ready) && m.pose(140).equals(idle), "Cancel settles without release");
            m.cue(4, AIM, 141, 7, 0, 12);
            h.assertTrue(m.pose(148).equals(idle), "Lost refresh cannot hold pose indefinitely");
        }
    }
}
