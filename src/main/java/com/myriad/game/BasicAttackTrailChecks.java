package com.myriad.game;

import com.myriad.client.firstperson.*;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;

/** Pure camera-geometry/timeline checks, no physical-client renderer is loaded by the server. */
final class BasicAttackTrailChecks {
    static void verify(GameTestHelper h){
        var night=new NightDaggerMotion();var thunder=new ThunderSwordMotion();var flame=new FlameDaoMotion();
        h.assertTrue(NightBasicTrail.mesh(night,0,false).isEmpty()&&ThunderBasicTrail.mesh(thunder,0,false).isEmpty()
                &&FlameBasicTrail.mesh(flame,0,false).isEmpty(),"No idle attack glow");
        int meshes=0;
        for(int cut=0;cut<3;cut++){
            double start=cut*22;night.attack(start,20);thunder.attack(start,20);flame.attack(start,20);
            h.assertTrue(Math.abs(night.duration()-5.4)<1E-6&&Math.abs(thunder.duration()-6)<1E-6&&Math.abs(flame.duration()-6.4)<1E-6,
                    "All three base-speed cosmetic attacks are much faster; no gameplay timer changed");
            for(int role=0;role<3;role++){
                double duration=role==0?night.duration():role==1?thunder.duration():flame.duration();
                var materials=role==0?Set.of(0,50,52):role==1?Set.of(0,57,58):Set.of(0,64,68);int visible=0;
                float peakTipAlpha=0;
                for(int step=0;step<=100;step++)for(boolean left:new boolean[]{false,true}){
                    double at=start+duration*step/100;
                    var quads=role==0?NightBasicTrail.mesh(night,at,left):role==1?ThunderBasicTrail.mesh(thunder,at,left):FlameBasicTrail.mesh(flame,at,left);
                    h.assertTrue(quads.size()<=AttackTrailMesh.MAX_QUADS,"Strict local trail budget");
                    if(!quads.isEmpty())visible++;
                    for(var q:quads)if(q.material()==0){
                        peakTipAlpha=Math.max(peakTipAlpha,q.alpha());
                        h.assertTrue(q.color()==(role==0?0xF5F7FF:role==1?0xB5F5FF:0xFFD078),"Bright tip retains silver/cyan/gold role tint");
                    }
                    validate(h,quads,materials);meshes++;
                    var repeated=role==0?NightBasicTrail.mesh(night,at,left):role==1?ThunderBasicTrail.mesh(thunder,at,left):FlameBasicTrail.mesh(flame,at,left);
                    h.assertTrue(quads.equals(repeated),"Same timestamp yields same trail, independent of render FPS/history");
                    if(step==0||step==100)h.assertTrue(quads.isEmpty(),"No trail at attack entry or after completion");
                }
                h.assertTrue(visible>30,"Every one of the nine attacks has readable swept geometry, including the stab");
                h.assertTrue(peakTipAlpha>=.79f&&peakTipAlpha<=.81f,"Every attack has a strong bright core, without increasing width or lifetime");
            }
        }
        // Equip animation moves the current weapon and every swept slice by the same amount.
        double at=44+thunder.duration()*.42;
        var a=ThunderBasicTrail.mesh(thunder,at,false,0);var b=ThunderBasicTrail.mesh(thunder,at,false,1);
        h.assertTrue(!a.isEmpty()&&a.size()==b.size(),"Equip test is inside the real cutting phase");
        for(int i=0;i<a.size();i++)h.assertTrue(Math.abs(a.get(i).a().y-b.get(i).a().y-.48)<1E-5,"Equip progress cannot detach thunder trail");
        at=44+flame.duration()*.42;a=FlameBasicTrail.mesh(flame,at,false,0);b=FlameBasicTrail.mesh(flame,at,false,1);
        h.assertTrue(!a.isEmpty()&&a.size()==b.size(),"Flame equip phase has geometry");
        for(int i=0;i<a.size();i++)h.assertTrue(Math.abs(a.get(i).a().y-b.get(i).a().y-.48)<1E-5,"Equip progress cannot detach flame trail");
        night.reset();thunder.reset();flame.reset();
        h.assertTrue(NightBasicTrail.mesh(night,45,false).isEmpty()&&ThunderBasicTrail.mesh(thunder,45,false).isEmpty()
                &&FlameBasicTrail.mesh(flame,45,false).isEmpty(),"Reset interrupts all glow immediately");
        h.assertTrue(NightBasicTrail.mesh(night,Double.NaN,false).isEmpty()&&ThunderBasicTrail.mesh(thunder,Double.POSITIVE_INFINITY,false).isEmpty()
                &&FlameBasicTrail.mesh(flame,Double.NaN,false).isEmpty(),"Invalid clock cannot create a trail");
        System.out.println("[MYRIAD-BASIC-TRAIL] PASS: "+meshes+" actual timeline/hand meshes, all nine attacks visible, themed materials/UV/budget, no idle/end/reset glow, deterministic FPS-independent samples and equip attachment");
    }
    private static void validate(GameTestHelper h,List<Quad> quads,Set<Integer> materials){
        for(var q:quads){
            h.assertTrue(materials.contains(q.material())&&q.alpha()>0&&q.alpha()<=.83,"Role-specific material and bounded brighter translucent strength");
            h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startU()<q.endU()&&q.startV()>=0&&q.endV()<=1&&q.startV()<q.endV(),"Flipbook subcell UV is safe");
            for(var p:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&p.z<-.055,"Trail finite and beyond near plane");
            h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()>1E-14,"No zero-area emitted quads");
        }
    }
}
