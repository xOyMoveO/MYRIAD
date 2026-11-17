package com.myriad.game;

import com.myriad.client.firstperson.*;
import com.myriad.network.NightSkillViewPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.myriad.network.NightSkillViewPayload.*;

final class NightSkillViewChecks {
    static void verify(GameTestHelper h){
        var motion=new NightSkillMotion();int count=0;
        var signatures=new java.util.HashSet<String>();
        for(int action=THROW;action<=STRIKE;action++){
            motion.reset();var cue=new NightSkillViewPayload(action,action==READY?16:14,100,1);
            h.assertTrue(motion.accept(cue,100,0,NightDaggerMotion.IDLE),"Confirmed skill cue accepted");
            var peak=motion.sample(100+cue.ticks()*.4);
            signatures.add(peak.main().toString()+peak.showOff()+peak.showBlade());
            h.assertTrue(peak.showOff()==(action==MERGE||action==SHIELD),"Only merge and shield use both hands");
            h.assertTrue(peak.showBlade()!=(action==MERGE||action==THROW),"Throw/merge temporarily hide the held dagger, without changing the real item");
            for(int i=0;i<160;i++){
                double time=100+cue.ticks()*i/160.0;var frame=motion.sample(time);
                for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
                    var wrist=NightDaggerRig.wrist(frame.main(),left);
                    var arm=new Matrix4f(wrist).mul(NightDaggerRig.arm(slim,left));
                    var blade=new Matrix4f(wrist).mul(NightDaggerRig.blade(left,frame.main().grip()));
                    var palm=arm.transformPosition(new Vector3f((slim?.5f:1f)/16*(left?1:-1),.5f,0));
                    h.assertTrue(palm.isFinite()&&palm.distance(blade.transformPosition(new Vector3f()))<1E-6,"Every skill preserves the same palm/grip on both hands and skin widths");
                    h.assertTrue(blade.determinant()>0&&wrist.determinant()>0,"Skill poses never mirror/invert the weapon mesh");
                    if(frame.showOff())h.assertTrue(NightDaggerRig.wrist(frame.off(),!left).determinant()>0,"Offhand uses its own anatomical mesh transform");
                    for(var q:NightSkillHandVfx.camera(frame,time,left))for(var v:java.util.List.of(q.a(),q.b(),q.c(),q.d()))
                        h.assertTrue(v.z<-.05&&Double.isFinite(v.x)&&Double.isFinite(v.y),"Small local hand VFX stay in front of the camera, not on the screen surface");
                    count++;
                }
            }
            h.assertTrue(!motion.active(100+cue.ticks())&&!motion.sample(100+cue.ticks()).showOff(),"Skill timeout restores persistent idle and offhand rendering");
        }
        h.assertTrue(signatures.size()==7,"Each skill/stage has a distinct gesture");
        motion.reset();motion.accept(new NightSkillViewPayload(DASH,10,100,5),100,0,NightDaggerMotion.IDLE);
        var forward=motion.sample(103);
        var axis=NightDaggerRig.wrist(forward.main(),false).mul(NightDaggerRig.blade(false,forward.main().grip())).transformDirection(new Vector3f(0,1,0)).normalize();
        h.assertTrue(axis.z<-.8&&axis.y>.25&&forward.main().grip()==180,"Dash knife is genuinely forward-grip, slightly raised to stay visible above the hand, and points away from the camera");
        h.assertTrue(!NightSkillHandVfx.blade(forward,103).isEmpty(),"Moving blade receives local animated stream layers");
        motion.reset();motion.accept(new NightSkillViewPayload(MERGE,14,100,6),100,0,NightDaggerMotion.IDLE);
        var merge=motion.sample(106);
        h.assertTrue(Math.abs(merge.main().x()+merge.off().x()-.19)<.001&&!NightSkillHandVfx.camera(merge,106,false).isEmpty(),"Palms meet at lower centre with a layered shadow seal");
        motion.reset();motion.accept(new NightSkillViewPayload(SHIELD,14,100,7),100,0,NightDaggerMotion.IDLE);
        var tap=motion.sample(106);
        var tip=NightDaggerRig.wrist(tap.main(),false).mul(NightDaggerRig.blade(false,tap.main().grip())).transformPosition(new Vector3f(.06375f,.5925f,0));
        var off=NightDaggerRig.wrist(tap.off(),true).transformPosition(new Vector3f());
        h.assertTrue(tip.distance(off)<.1,"Shield dagger tip makes a small contact at the offhand, rather than swinging through the screen");
        motion.reset();motion.accept(new NightSkillViewPayload(READY,16,100,8),100,0,NightDaggerMotion.IDLE);
        h.assertTrue(motion.sample(110).main().grip()==180,"Ultimate completes grip turn during the windup");
        motion.accept(new NightSkillViewPayload(STRIKE,10,116,8),116,0,NightDaggerMotion.IDLE);
        h.assertTrue(motion.sample(119).main().z()<-1.4,"Arrival explicitly triggers the forward stab");
        h.assertTrue(!motion.accept(new NightSkillViewPayload(READY,16,100,8),119,0,NightDaggerMotion.IDLE),"Late windup cannot overwrite arrival");
        h.assertTrue(!motion.accept(new NightSkillViewPayload(THROW,9,100,7),119,0,NightDaggerMotion.IDLE),"Older skill token cannot replace newer skill");
        motion.accept(new NightSkillViewPayload(CANCEL,0,119,8),119,0,NightDaggerMotion.IDLE);
        h.assertTrue(!motion.active(119)&&!motion.accept(new NightSkillViewPayload(STRIKE,10,116,8),119,0,NightDaggerMotion.IDLE),"Interrupted token cannot revive a stale stab");
        h.assertTrue(!new NightSkillViewPayload(90,8,100,1).valid()&&!new NightSkillViewPayload(THROW,80,100,1).valid(),"Malformed cues are rejected");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{var cue=new NightSkillViewPayload(STRIKE,10,116,9);NightSkillViewPayload.CODEC.encode(buf,cue);
            h.assertTrue(cue.equals(NightSkillViewPayload.CODEC.decode(buf)),"Owner-only skill cue wire roundtrip");}finally{buf.release();}
        System.out.println("[MYRIAD-NIGHT-SKILL-VIEW] PASS: seven distinct tracks, "+count+" palm/grip poses, both arms/skins, forward knife, clasp/tap/arrival, local layers, stale/cancel/timeout/wire");
    }
}
