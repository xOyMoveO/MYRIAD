package com.myriad.game;

import com.myriad.client.firstperson.*;
import com.myriad.character.skills.tianxing.rework.*;
import com.myriad.network.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;
import static com.myriad.network.ThunderSkillViewPayload.*;

final class ThunderSkillViewChecks {
    static void verify(GameTestHelper h){
        var signatures=new HashSet<String>();int count=0;
        for(int action=0;action<=8;action++){
            var m=new ThunderSkillMotion();h.assertTrue(m.accept(new ThunderSkillViewPayload(action,14,100,1),100,0,ThunderSwordMotion.IDLE),"Confirmed thunder hand cue");
            var peak=m.sample(106);signatures.add(peak.main().toString()+peak.off()+peak.showBlade());
            h.assertTrue(peak.showBlade()==(action!=GUARD),"Only mudra temporarily stows the blade");
            for(int i=0;i<120;i++)for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
                double time=100+14*i/120.0;var f=m.sample(time);var sword=ThunderSkillRig.sword(f,left);
                var arm=ThunderSkillRig.main(f,left).mul(ThunderSwordRig.arm(slim,left));
                var palm=arm.transformPosition(new Vector3f((slim?.5f:1f)/16*(left?1:-1),.5f,0));
                h.assertTrue(sword.determinant()>0&&palm.distance(sword.transformPosition(new Vector3f()))<1E-6,"Every skill: same palm and real sword grip, no inverted model");
                if(action==SLASH_FIRE&&f.progress()>.25&&f.progress()<.67){
                    var motion=new ThunderSwordMotion.Frame(f.main(),f.before(),f.after(),1,0);
                    var shaft=sword.transformDirection(new Vector3f(0,1,0)).normalize();var travel=ThunderSwordRig.travel(motion,left);
                    var transverse=new Vector3f(travel).sub(new Vector3f(shaft).mul(travel.dot(shaft))).normalize();
                    h.assertTrue(sword.transformDirection(new Vector3f(1,0,0)).normalize().dot(transverse)>.999,"Slash leads with sharp edge, not the sword's flat face");
                }
                if(f.showOff())h.assertTrue(ThunderSkillRig.off(f,left).determinant()>0,"Offhand anatomical transform remains proper");
                for(var q:ThunderSkillHandVfx.camera(f,time,left))for(var at:List.of(q.a(),q.b(),q.c(),q.d()))
                    h.assertTrue(Double.isFinite(at.x)&&Double.isFinite(at.y)&&at.z<-.05,"Local lightning stays in front of camera");
                h.assertTrue(ThunderSkillHandVfx.camera(f,time,left).size()<=48&&ThunderSkillHandVfx.blade(f,time).size()<=48,"Bounded local quad budgets");
                if(action==GROUND&&f.progress()>=.27&&f.progress()<=.74){
                    var second=ThunderSkillRig.off(f,left).transformPosition(new Vector3f());
                    h.assertTrue(second.distance(sword.transformPosition(new Vector3f(0,-.28f,0)))<1E-6,"Both palms actually clasp the handle during plunge");
                }
                if(action==JUDGMENT_READY&&f.progress()>=.18){
                    var off=ThunderSkillRig.off(f,left).transformPosition(new Vector3f());
                    h.assertTrue(off.distance(sword.transformPosition(new Vector3f(-.075f,(float)ThunderSkillRig.wipeHeight(f.progress()),0)))<1E-6,"Left palm follows actual blade from bottom to top");
                }
                count++;
            }
            h.assertTrue(!m.active(114)&&!m.sample(114).showOff(),"Timeout restores idle and offhand");
        }
        h.assertTrue(signatures.size()>=8,"Different skills/stages have genuinely different gestures");
        var ground=new ThunderSkillMotion();ground.accept(new ThunderSkillViewPayload(GROUND,14,100,1),100,0,ThunderSwordMotion.IDLE);
        var axis=ThunderSkillRig.sword(ground.sample(109),false).transformDirection(new Vector3f(0,1,0)).normalize();
        h.assertTrue(axis.y<-.95,"Ground stab points the actual blade down");
        h.assertTrue(ThunderSkillRig.wipeHeight(.95)>ThunderSkillRig.wipeHeight(.2)+.8,"Blade wipe progresses upward");
        var m=new ThunderSkillMotion();m.accept(new ThunderSkillViewPayload(SLASH_READY,10,100,3),100,0,ThunderSwordMotion.IDLE);
        h.assertTrue(m.sample(109).main().x()<-.3&&m.sample(109).main().y()>=0,"Slash charges at upper-left");
        h.assertTrue(m.accept(new ThunderSkillViewPayload(SLASH_FIRE,10,110,3),110,0,ThunderSwordMotion.IDLE),"Authoritative release follows completed windup");
        h.assertTrue(!m.accept(new ThunderSkillViewPayload(SLASH_READY,10,100,3),111,0,ThunderSwordMotion.IDLE),"Late ready cannot overwrite release");
        m.accept(new ThunderSkillViewPayload(CANCEL,0,111,3),111,0,ThunderSwordMotion.IDLE);
        h.assertTrue(!m.active(111)&&!m.accept(new ThunderSkillViewPayload(SLASH_FIRE,10,110,3),111,0,ThunderSwordMotion.IDLE),"Cancel token cannot revive");
        h.assertTrue(!m.accept(new ThunderSkillViewPayload(GROUND,14,100,2),111,0,ThunderSwordMotion.IDLE),"Old cast rejected");
        m.reset();h.assertTrue(!m.accept(new ThunderSkillViewPayload(GROUND,14,100,1),120,20,ThunderSwordMotion.IDLE),"Expired network cue rejected");
        h.assertTrue(!new ThunderSkillViewPayload(11,8,100,1).valid()&&!new ThunderSkillViewPayload(1,90,100,1).valid(),"Invalid cue rejected");
        var aim=ThunderSkillMotion.aim(8);h.assertTrue(aim.showOff()&&!ThunderSkillHandVfx.camera(aim,108,false).isEmpty(),"Synced aiming has left-hand thunder");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{var cue=new ThunderSkillViewPayload(JUDGMENT_READY,12,100,9);ThunderSkillViewPayload.CODEC.encode(buf,cue);h.assertTrue(cue.equals(ThunderSkillViewPayload.CODEC.decode(buf)),"Thunder cue wire roundtrip");}finally{buf.release();}
        Vec3 forward=new Vec3(0,0,1);
        h.assertTrue(ThunderJudgmentSkill.inside(new Vec3(8*Math.sin(Math.PI/3),0,4),forward)
                &&!ThunderJudgmentSkill.inside(new Vec3(0,0,8.01),forward)&&!ThunderJudgmentSkill.inside(new Vec3(0,0,-1),forward)
                &&!ThunderJudgmentSkill.inside(new Vec3(5*Math.sin(Math.toRadians(61)),0,5*Math.cos(Math.toRadians(61))),forward),"Exact 8-block / 120-degree sector boundaries");
        var warning=new AbilityVfxPayload(1,-1,1,7,com.myriad.character.ability.AbilityVfxGeometry.CHARGE,Vec3.ZERO,forward,8,120,4,100,false);
        h.assertTrue(ThunderVfxGeometry.build(warning,1,false).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).allMatch(p->Math.abs(p.y)<.001),"Judgment telegraph is a ground fan, not an early attack");
        System.out.println("[MYRIAD-THUNDER-SKILL-VIEW] PASS: "+count+" actual grip/skin/mirror poses, physical second grip/wipe, distinct cues, aiming, wire/stale/cancel, exact fan and warning");
    }
}
