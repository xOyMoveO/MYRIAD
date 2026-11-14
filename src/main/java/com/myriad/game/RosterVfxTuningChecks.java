package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.keheng.KehengVfx;
import com.myriad.character.skills.yike.YikeVfx;
import com.myriad.character.skills.yike.YikeVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Exact comparison against unscaled source faces; no gameplay hitbox or packet radius changes. */
final class RosterVfxTuningChecks {
    static void verify(GameTestHelper h) {
        float previous = 0;
        for (int i = 0; i <= 1000; i++) {
            float a = i / 1000f, b = RosterVfxTuning.opacity(a);
            h.assertTrue(b >= a - 1E-7 && b >= previous && b <= 1,
                    "Exposure increases visibility monotonically without losing transparent fade boundaries");
            if (i > 0 && i < 1000) h.assertTrue(b > a, "Every translucent stage is brighter");
            previous = b;
        }
        h.assertTrue(RosterVfxTuning.opacity(0) == 0 && RosterVfxTuning.opacity(1) == 1
                && RosterVfxTuning.opacity(Float.NaN) == 0, "Opacity endpoints and invalid input stay safe");
        for(float alpha:new float[]{.1f,.25f,.5f,.75f,.9f})
            h.assertTrue(RosterVfxTuning.opacity(alpha)>alpha*(1.4f-.4f*alpha),"New exposure is stronger than the previously installed visual curve");
        var source = new Quad(Vec3.ZERO, new Vec3(1,0,0), new Vec3(1,1,0), new Vec3(0,1,0),
                0x429ABD, .5f, KEHENG_HD_RING, true, .1f, .7f, .2f, .9f);
        var bright = RosterVfxTuning.brighten(List.of(source)).getFirst();
        h.assertTrue(bright.a().equals(source.a()) && bright.b().equals(source.b())
                && bright.c().equals(source.c()) && bright.d().equals(source.d())
                && bright.material() == source.material() && bright.reverseV() == source.reverseV()
                && bright.startU() == source.startU() && bright.endU() == source.endU()
                && bright.startV() == source.startV() && bright.endV() == source.endV(),
                "Brightness preserves exact geometry, UV, winding and material without adding layers");
        for (int shift : new int[]{16,8,0}) h.assertTrue(((bright.color() >> shift) & 255)
                >= ((source.color() >> shift) & 255), "Tint channels only brighten and remain bounded");
        int samples = 0;
        for (int theme : new int[]{11,12}) {
            var models = RosterProjectileModels.load(theme == 11 ? "keheng_projectiles_mesh.json" : "yike_projectiles_mesh.json");
            for (int signal : theme == 11 ? new int[]{0} : new int[]{YikeVfx.BASIC,0,YikeVfx.HEAVY})
                for (boolean low : new boolean[]{false,true})
                    for (Vec3 direction : List.of(new Vec3(0,0,1), new Vec3(0,1,0), new Vec3(0,-1,0), new Vec3(.3,-.8,.4).normalize())) {
                        Vec3 start = new Vec3(2,3,4), end = start.add(direction.scale(1.4)), tip = start.lerp(end,.7);
                        var p = new AbilityVfxPayload(1,-1,theme,2,AbilityVfxGeometry.FLIGHT,start,end,.36f,signal,3,100,false,9);
                        int material = theme == 11 ? KEHENG_PROJECTILE_MODEL : YIKE_PROJECTILE_MODEL;
                        String key = theme == 11 ? (low ? "needle_lod" : "needle") : YikeVfxGeometry.modelKey(signal,low);
                        double oldLength = theme == 11 ? .92 : signal == YikeVfx.BASIC ? .22 : signal == YikeVfx.HEAVY ? 1.2 : .62;
                        var original = new ArrayList<Quad>();
                        models.append(original,key,tip,direction,oldLength,material,theme == 11 ? .98f : 1);
                        var body = AbilityVfxGeometry.build(p,.7f,low).stream().filter(q -> q.material() == material).toList();
                        h.assertTrue(body.size() == original.size(), "Full and LOD use the same authored face count");
                        for (int i = 0; i < body.size(); i++) {
                            var q = body.get(i); var old = original.get(i);
                            Vec3[] actual = {q.a(),q.b(),q.c(),q.d()}, base = {old.a(),old.b(),old.c(),old.d()};
                            for (int j = 0; j < 4; j++) {
                                h.assertTrue(actual[j].distanceToSqr(tip.add(base[j].subtract(tip).scale(1.4))) < 1E-12,
                                        "Every actual projectile vertex is exactly 40% larger about the confirmed tip");
                                h.assertTrue(actual[j].subtract(tip).dot(direction) <= 1E-6,
                                        "Upsized bodies never protrude forward past the confirmed flight tip");
                                h.assertTrue((theme == 11 ? KehengVfx.bounds(p) : new net.minecraft.world.phys.AABB(start,end).inflate(2)).inflate(.01).contains(actual[j]),
                                        "Client frustum bounds contain the enlarged full/LOD model");
                            }
                            h.assertTrue(q.alpha() == RosterVfxTuning.opacity(old.alpha())
                                    && q.color() == RosterVfxTuning.tint(old.color()), "Production exposure is applied exactly once");
                        }
                        h.assertTrue(AbilityVfxGeometry.build(p,1,low).isEmpty(), "Upsized models keep the same one-sample lifetime");
                        samples++;
                    }
        }
        System.out.println("[MYRIAD-ROSTER-VFX-TUNING] PASS: monotone bounded exposure, unchanged geometry/UV, "
                + samples + " exact 1.4x full/LOD flight samples, fixed tips, frustum bounds and expiry");
    }
}
