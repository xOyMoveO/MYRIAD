package com.myriad.game;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.skills.fenjue.rework.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Specifically checks ghosts caused by renewed warning rings and two simultaneous flipbook silhouettes. */
final class FlameGhostChecks {
    private static AbilityVfxPayload p(int owner,int kind,float radius,float angle,int life,long time){
        return new AbilityVfxPayload(owner,-1,2,4,kind,new Vec3(0,.06,0),new Vec3(0,.06,1),radius,angle,life,time,false);
    }
    static void verify(GameTestHelper h){
        var first=p(1,SEAL,3.5f,359,5,100);var refresh=p(1,SEAL,3.5f,359,5,103);
        h.assertTrue(FlameVisualMerge.replaces(refresh,first)&&!FlameVisualMerge.replaces(first,refresh),"Renewed warnings replace the old rim, never the reverse");
        h.assertTrue(!FlameVisualMerge.replaces(p(1,SEAL,2,359,4,103),first),"Growing warning fill and outer boundary are not confused");
        var active=p(1,SEAL,3.5f,360,80,112);
        h.assertTrue(FlameVisualMerge.replaces(active,first)&&FlameVisualMerge.obsolete(p(1,SEAL,3.5f,359,5,112),active),"Active cage replaces old warnings and rejects a same-tick late warning");
        h.assertTrue(FlameVisualMerge.replaces(p(1,SEAL,3.5f,360,80,116),active),"Identical same-owner recast fields do not stack visual copies");
        h.assertTrue(!FlameVisualMerge.replaces(p(2,SEAL,3.5f,360,80,116),active),"Another caster's field is not deleted");
        h.assertTrue(FlameVisualMerge.replaces(p(1,CHARGE,6,0,4,103),p(1,CHARGE,6,0,4,100)),"Charge renewals do not leave two displaced copies");
        for(double time:new double[]{0,.3,1.5,3.8,5.1,99.8,199.9,200.2}){
            var out=new ArrayList<Quad>();FlameMajorVfx.fire(out,Vec3.ZERO,new Vec3(1,0,0),.5,1,time,.9,32);
            h.assertTrue(out.size()==2,"Exactly one frame silhouette, front and back; no simultaneous neighbouring-frame ghost");
            h.assertTrue(out.get(0).startU()==out.get(1).startU()&&out.get(0).startV()==out.get(1).startV(),"Both sides use the same native frame");
            h.assertTrue(out.getFirst().alpha()>.99f,"Requested stronger solid flame image, not two diluted half-opacity silhouettes");
        }
        h.assertTrue(FlameMajorVfx.sceneLight(0,true)==0xF000F0&&FlameMajorVfx.sceneLight(0,false)==0xF000F0,"Both ground and flame remain visibly emitting at night");
        System.out.println("[MYRIAD-FLAME-GHOST] PASS: real warning/field/charge renewal cases, late warning rejection, same-caster merge with enemy independence, one native frame per plane, brighter emissive image");
    }
}
