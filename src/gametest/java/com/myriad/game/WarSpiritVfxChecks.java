package com.myriad.game;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.character.ability.AbilityVisualLifetime;
import com.myriad.character.ability.WarSpiritModels;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

final class WarSpiritVfxChecks {
    private WarSpiritVfxChecks() {}

    static void verify(GameTestHelper h) {
        for(int theme:new int[]{8,9})for(Vec3 heading:List.of(new Vec3(.8,.3,1),new Vec3(0,1.2,0),new Vec3(-1,-.4,0))) {
            int variant=theme==8?5:2,material=theme==8?WAR_HD_WAVE_MODEL:SPIRIT_HD_CRANE_MODEL;
            var flight=new AbilityVfxPayload(1,-1,theme,variant,FLIGHT,Vec3.ZERO,heading,.22f,0,3,100,false,5);
            var first=build(flight,0,false);
            var low=build(flight,0,true);
            long faces=first.stream().filter(q->q.material()==material).count();
            h.assertTrue(faces==(theme==8?36:39)*6&&low.stream().filter(q->q.material()==material).count()==faces,
                    "Flying body uses every exported cuboid face at both detail levels");
            h.assertTrue(first.stream().filter(q->q.material()!=material).map(Quad::material).distinct().count()>1,
                    "Solid models have independent animated 2D trim");
            var a=first.stream().filter(q->q.material()==material).findFirst().orElseThrow().a();
            var b=build(flight,1,false).stream().filter(q->q.material()==material).findFirst().orElseThrow().a();
            h.assertTrue(b.subtract(a).distanceToSqr(heading)<.0001,"Main body tracks the full 3D segment");
            h.assertTrue(build(flight,3,false).isEmpty(),"Expired projectiles have no mesh");
            var timeline=new AbilityVisualLifetime();
            timeline.end(new AbilityVfxPayload(1,-1,theme,variant,CANCEL,heading,heading,0,2,1,101,false,5),101);
            h.assertTrue(timeline.finished(flight,101),"End event removes the model even before the visual lifetime");
        }
        var base=new AbilityVfxPayload(1,-1,9,4,SEAL,Vec3.ZERO,new Vec3(0,0,1),4,360,80,100,false);
        h.assertTrue(WarSpiritModels.owns(base)&&build(base,8,false).stream().filter(q->q.material()==SPIRIT_HD_BEACON_MODEL).count()==102,
                "Active beacon owns a fixed 17-part model");
        var preview=new AbilityVfxPayload(1,-1,9,4,RETICLE,Vec3.ZERO,new Vec3(0,0,1),4,360,2,100,true);
        h.assertTrue(!WarSpiritModels.owns(preview)&&build(preview,1,false).stream().noneMatch(q->WarSpiritModels.material(q.material())),
                "Point preview cannot spawn the bell model");
        var cut=new AbilityVfxPayload(1,-1,8,2,ARC,new Vec3(0,.65,0),new Vec3(0,.65,1),6,100,8,100,false);
        var early=build(cut,0,false);var late=build(cut,2,false);
        h.assertTrue(early.stream().anyMatch(q->q.material()==WAR_HD_EDGE)
                &&early.getFirst().startU()!=late.getFirst().startU(),"Cut layers select different atlas frames over time");
        System.out.println("[MYRIAD-WAR-SPIRIT-VFX] PASS: full 3D flight, animated layers, actual stop lifecycle, placement preview and fixed bell");
    }
}
