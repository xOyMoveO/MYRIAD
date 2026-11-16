package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.client.firstperson.NightDaggerMotion;
import com.myriad.client.firstperson.NightDaggerRig;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Vector3f;

/** Cosmetic math can run on a dedicated server without loading the client renderer. */
final class NightDaggerViewChecks {
    private NightDaggerViewChecks() { }
    static void verify(GameTestHelper h) {
        var motion=new NightDaggerMotion();
        h.assertTrue(motion.sample(0).equals(NightDaggerMotion.IDLE),"Always-visible idle starts at rest");
        double next=0;
        for (int combo=0;combo<6;combo++) {
            motion.attack(next,20);
            h.assertTrue(motion.index()==combo%3,"Forward stab, overhead hammer, forward-grip slash, repeat");
            for (int i=0;i<=200;i++) {
                var pose=motion.sample(next+motion.duration()*i/200);
                for (boolean left:new boolean[]{false,true}) for (boolean slim:new boolean[]{false,true}) {
                    var wrist=NightDaggerRig.wrist(pose,left);
                    var arm=new org.joml.Matrix4f(wrist).mul(NightDaggerRig.arm(slim,left));
                    var blade=new org.joml.Matrix4f(wrist).mul(NightDaggerRig.blade(left,pose.grip()));
                    // Anatomical palm centre is eight pixels from shoulder, not the model pivot.
                    var palm=arm.transformPosition(new Vector3f((slim?.5f:1f)/16*(left?1:-1),.5f,0));
                    var grip=blade.transformPosition(new Vector3f());
                    h.assertTrue(palm.isFinite() && grip.distance(palm)<1E-6,
                            "Both arm widths/hands keep the handle at the palm on every frame");
                    h.assertTrue(wrist.determinant()>0 && blade.determinant()>0,"No negative scaling/cull inversion");
                }
                var r=NightDaggerRig.wrist(pose,false).transformPosition(new Vector3f());
                var l=NightDaggerRig.wrist(pose,true).transformPosition(new Vector3f());
                h.assertTrue(Math.abs(r.x+l.x)<1E-6 && Math.abs(r.y-l.y)<1E-6 && Math.abs(r.z-l.z)<1E-6,
                        "Left-handed wrist path mirrors the right-handed path");
            }
            h.assertTrue(motion.sample(next+motion.duration()).equals(NightDaggerMotion.IDLE),"Smooth return to persistent idle");
            next+=20;
        }
        motion.attack(next+80,20);
        h.assertTrue(motion.index()==0,"A pause restarts with forward stab");
        motion.attack(next+82,20);
        var before=motion.sample(next+83);
        motion.attack(next+83,10);
        h.assertTrue(motion.sample(next+83).equals(before),"Fast follow-up preserves the current pose");
        motion.reset();
        h.assertTrue(motion.index()==-1 && motion.sample(next+84).equals(NightDaggerMotion.IDLE),"Lifecycle reset clears the chain");
        for (double speed:new double[]{1,8,20,60,Double.NaN})
            h.assertTrue(NightDaggerMotion.durationFor(speed)>=3.5 && NightDaggerMotion.durationFor(speed)<=6,
                    "Cosmetic recovery remains bounded and never changes gameplay attack speed");
        motion.attack(0,20);
        h.assertTrue(motion.sample(motion.duration()*.40).z()<-1.05,"First attack visibly stabs forward");
        motion.attack(20,20);
        var raised=motion.sample(20+motion.duration()*.30);
        var struck=motion.sample(20+motion.duration()*.56);
        h.assertTrue(raised.y()>.30 && struck.y()<-.30 && raised.y()-struck.y()>.60,
                "Second attack lifts above the hand then drives down, not a lateral slash");
        h.assertTrue(raised.grip()==0 && struck.grip()==0,"Overhead attack retains reverse grip");
        motion.attack(40,20);
        h.assertTrue(motion.sample(40+motion.duration()*.36).grip()==180 && motion.sample(40+motion.duration()*.62).grip()==180
                        && motion.sample(40+motion.duration()*.62).x()<-.1,
                "Third attack completes the forward-grip turn BEFORE the slash");
        var reverse=NightDaggerRig.blade(false,0).transformDirection(new Vector3f(0,1,0)).normalize();
        var forward=NightDaggerRig.blade(false,180).transformDirection(new Vector3f(0,1,0)).normalize();
        h.assertTrue(reverse.dot(forward)<-.999,"Forward grip reverses the blade axis, not just its wide face");
        h.assertTrue(NightDaggerMotion.IDLE.x()>.5 && NightDaggerMotion.IDLE.y()<-.25
                        && NightDaggerRig.VIEW_SCALE<=.75,
                "Idle moves down/right and the whole view model is smaller");
        try {
            var loader=NightDaggerViewChecks.class.getClassLoader();
            try(var in=loader.getResourceAsStream("assets/myriad/items/yingxiao_shadow_dagger.json")) {
                h.assertTrue(in!=null,"First-person-only dagger model definition is packaged");
            }
            try(var in=loader.getResourceAsStream("assets/myriad/models/item/yingxiao_shadow_dagger.json")) {
                var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                h.assertTrue(json.getAsJsonArray("elements").size()==321,"Approved detailed dagger geometry is packaged");
                var display=json.getAsJsonObject("display");
                for(String hand:new String[]{"thirdperson_righthand","thirdperson_lefthand"}) {
                    var held=display.getAsJsonObject(hand);
                    var rotation=held.getAsJsonArray("rotation");
                    h.assertTrue(rotation.get(0).getAsInt()==-90 && rotation.get(1).getAsInt()==90
                                    && rotation.get(2).getAsInt()==-90,
                            "Actual weapon retains the player's approved reverse grip in both third-person hands");
                    var offset=held.getAsJsonArray("translation");
                    h.assertTrue(Math.abs(offset.get(1).getAsDouble()+1.577)<1E-6
                                    && Math.abs(offset.get(2).getAsDouble()-1.439)<1E-6,
                            "Actual weapon retains the approved palm offsets");
                    var scale=held.getAsJsonArray("scale");
                    for(int axis=0;axis<3;axis++)
                        h.assertTrue(Math.abs(scale.get(axis).getAsDouble()-1.5)<1E-6,
                                "Only third-person dagger display is enlarged to 150%, in both hands");
                }
                for(String context:new String[]{"gui","ground","fixed"})
                    h.assertTrue(display.has(context),"Dagger also defines inventory, dropped-item and frame display");
            }
            try(var in=loader.getResourceAsStream("assets/myriad/items/yingci.json")) {
                var json=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                h.assertTrue(json.getAsJsonObject("model").get("type").getAsString().equals("minecraft:model")
                                && json.getAsJsonObject("model").get("model").getAsString().equals("myriad:item/yingxiao_shadow_dagger"),
                        "Actual Yingci item uses the new dagger in third-person and inventory, not just a first-person copy");
            }
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
        System.out.println("[MYRIAD-NIGHT-FIRSTPERSON] PASS: 3-step chain, smooth reset/interruption, 4824 hand/grip poses, left/slim, no mirrored cull inversion, scoped assets");
    }
}
