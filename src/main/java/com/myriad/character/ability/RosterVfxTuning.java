package com.myriad.character.ability;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import java.util.List;

/** Visual-only exposure for Keheng/Yike, applied once after their stage envelopes. */
public final class RosterVfxTuning {
    public static final double PROJECTILE_SCALE = 1.4;
    private RosterVfxTuning() {}

    public static float opacity(float alpha) {
        if (!Float.isFinite(alpha) || alpha <= 0) return 0;
        if (alpha >= 1) return 1;
        // Monotone, bounded and zero-preserving: fading layers do not become opaque slabs.
        return (float)(1 - Math.pow(1 - alpha, 2.4));
    }

    public static int tint(int rgb) {
        int result = 0;
        for (int shift : new int[]{16, 8, 0})
            result |= Math.min(255, Math.round(((rgb >> shift) & 255) * 1.18f)) << shift;
        return result;
    }

    public static List<Quad> brighten(List<Quad> mesh) {
        return mesh.stream().map(q -> new Quad(q.a(), q.b(), q.c(), q.d(), tint(q.color()),
                opacity(q.alpha()), q.material(), q.reverseV(), q.startU(), q.endU(), q.startV(), q.endV())).toList();
    }
}
