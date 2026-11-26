package com.myriad.client.firstperson;

import com.myriad.network.LuoxiaBowPosePayload;
import com.myriad.network.LuoxiaBowPosePayload.Phase;

/** Shared bow-local keyframes. Distance is in model units and roll is in degrees. */
public record LuoxiaBowMotion(int skill, float pull, float recoil, float roll, float crouch,
                              boolean loaded, int arrows, String mesh) {
    public static LuoxiaBowMotion sample(LuoxiaBowPosePayload p, float recoil, boolean step) {
        int skill = p == null ? -1 : p.skill();
        boolean loaded = p != null && (p.phase() == Phase.DRAW || p.phase() == Phase.WINDUP
                || p.phase() == Phase.LOCK || p.phase() == Phase.RELOAD);
        float progress = loaded ? Math.clamp(p.work() / p.maximum(), 0, 1) : 0;
        boolean steady = skill == 1 && progress >= 1;
        String mesh = switch (skill) {
            case 0 -> "quick";
            case 1 -> steady ? "full_draw" : "basic";
            case 2 -> "dusk_mark";
            case 4 -> "feather_burst";
            case 5 -> "last_sun";
            case 6 -> "thousand_feathers";
            case 7 -> "sunset_line";
            default -> "basic";
        };
        float shoulder = switch (skill) {
            case 0 -> 8; case 1 -> -11; case 2 -> 52; case 4 -> 29;
            case 5 -> -19; case 6 -> 37; case 7 -> -5; default -> 13;
        };
        float brace = step ? 1 : skill == 7 ? .85f : skill == 1 || skill == 5 ? progress * .6f : 0;
        // Heavy shots kick harder. The third light shot resolves with a smaller final rebound.
        float kick = recoil * (skill == 5 || skill == 7 ? 1.25f : skill == 4 ?
                (1.15f - .17f * (p == null ? 0 : p.work())) : .8f);
        return new LuoxiaBowMotion(skill, progress, kick, loaded || recoil > 0 ? shoulder : p != null && p.phase()==Phase.CANCEL ? shoulder * .35f : 24,
                brace, loaded, loaded ? skill == 6 ? 3 : 1 : 0, mesh);
    }

    public int stage() { return !loaded ? 0 : pull < .30f ? 1 : pull < .73f ? 2 : 3; }

    // Same limb-tip and constant string-length construction as generate_luoxia_bow.py.
    private static float serving(float draw) {
        float tipY = 13.66f - draw * 1.70f;
        return 6.78f + draw * 1.60f + (float)Math.sqrt(Math.max(0, 13.66f * 13.66f - tipY * tipY));
    }
    public float nock() { return serving(loaded ? .13f + .87f * pull : 0) / 16; }
    public float bowWidth() {
        float bakedDraw = switch (stage()) { case 1 -> .13f; case 2 -> .52f; case 3 -> 1; default -> 0; };
        return nock() * 16 / serving(bakedDraw);
    }
}
