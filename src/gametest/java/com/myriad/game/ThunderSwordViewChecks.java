package com.myriad.game;

import com.myriad.client.firstperson.*;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Server-side math checks for the prototype and for the rig still used by skill gestures. */
final class ThunderSwordViewChecks {
    static void verify(GameTestHelper h){
        verifyPrototype(h);
        h.assertTrue(ThunderSwordRig.allowed(true,"tianxing",true,true),"Own living Tianxing sword view is enabled");
        h.assertTrue(!ThunderSwordRig.allowed(false,"tianxing",true,true)&&!ThunderSwordRig.allowed(true,"yingxiao",true,true)
                &&!ThunderSwordRig.allowed(true,"tianxing",false,true)&&!ThunderSwordRig.allowed(true,"tianxing",true,false),"Mod/role/camera-life/item gates restore ordinary rendering");
        var motion=new ThunderSwordMotion();
        for(int cut=0;cut<3;cut++){
            double start=cut*20;motion.attack(start,20);
            h.assertTrue(motion.index()==cut,"Three accepted attacks cycle in order");
            for(int i=0;i<=120;i++)for(float equip:new float[]{0,.5f,1})for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
                var frame=motion.frame(start+motion.duration()*i/120);
                var wrist=ThunderSwordRig.wrist(frame.pose(),equip,left);
                var arm=ThunderSwordRig.hand(frame.pose(),equip,left).mul(ThunderSwordRig.arm(slim,left));
                var blade=new Matrix4f(wrist).mul(ThunderSwordRig.blade(frame,left));
                var palm=arm.transformPosition(new Vector3f((slim?.5f:1)/16*(left?1:-1),.5f,0));
                var end=ThunderSwordRig.forearmEnd(left);
                var sleeveAxis=arm.transformDirection(new Vector3f(0,1,0)).normalize();
                h.assertTrue(sleeveAxis.dot(new Vector3f(palm).sub(end).normalize())>.9999f,"Fist and sleeve stay aligned along the straight forearm axis");
                h.assertTrue(end.isFinite()&&end.y/-end.z<-Math.tan(Math.toRadians(55)),"Forearm continues off-screen even at wide FOV");
                h.assertTrue(palm.isFinite()&&palm.distance(blade.transformPosition(new Vector3f()))<1E-6,"Palm and handle stay attached in every cut/equip/skin/hand pose");
                h.assertTrue(wrist.determinant()>0&&blade.determinant()>0,"No reversed face winding or negative scale");
                if(frame.cuttingWeight()==1){
                    var shaft=blade.transformDirection(new Vector3f(0,1,0)).normalize();
                    var travel=ThunderSwordRig.travel(frame,left);
                    var transverse=new Vector3f(travel).sub(new Vector3f(shaft).mul(travel.dot(shaft))).normalize();
                    var edge=blade.transformDirection(new Vector3f(1,0,0)).normalize();
                    var normal=blade.transformDirection(new Vector3f(0,0,1)).normalize();
                    h.assertTrue(edge.dot(transverse)>.999,"Actual sharpened edge leads the cut, not broad sword face: "+edge.dot(transverse));
                    h.assertTrue(Math.abs(normal.dot(transverse))<.002,"Blade plane contains travel direction; no flat slap");
                }
            }
            var from=tip(motion.sample(motion.tickAt(.22)));var to=tip(motion.sample(motion.tickAt(.62)));
            float dx=to.x/-to.z-from.x/-from.z,dy=to.y/-to.z-from.y/-from.z;
            h.assertTrue(cut==0?dx>.5&&dy<-.35:cut==1?dx<-.5&&dy<-.35:dx<-.5&&Math.abs(dy)<.04,
                    "Projected tip follows diagonal/diagonal/horizontal: cut="+cut+" dx="+dx+" dy="+dy);
        }
        var idle=ThunderSwordRig.wrist(ThunderSwordMotion.IDLE,0,false);
        var blade=new Matrix4f(idle).mul(ThunderSwordRig.blade(false));
        h.assertTrue(blade.transformDirection(new Vector3f(0,1,0)).normalize().y>.78,"Resting blade rises diagonally above the guard");
        for(boolean left:new boolean[]{false,true}){
            var b=ThunderSwordRig.wrist(ThunderSwordMotion.IDLE,0,left).mul(ThunderSwordRig.blade(left));
            h.assertTrue(Math.abs(b.transformDirection(new Vector3f(0,0,1)).normalize().dot(b.getTranslation(new Vector3f()).normalize()))>.66,"Sword face is readable from the resting camera on each side");
        }
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            h.assertTrue(ThunderSwordRig.wrist(ThunderSwordMotion.IDLE,invalid,false).isFinite(),"Invalid equip interpolation is safe");
        var before=motion.sample(41);motion.attack(41,20);
        h.assertTrue(motion.index()==0&&motion.sample(41).equals(before),"Fast next attack blends from current hand pose");
        motion.attack(200,20);h.assertTrue(motion.index()==0,"Long pause restarts first cut");
        motion.attack(10,20);h.assertTrue(motion.index()==0,"World tick rollback restarts first cut");
        motion.attack(Double.NaN,Double.NaN);h.assertTrue(motion.index()==0,"Invalid clock cannot poison animation");
        h.assertTrue(motion.sample(Double.POSITIVE_INFINITY).equals(ThunderSwordMotion.IDLE),"Invalid sample returns idle");
        motion.reset();h.assertTrue(motion.index()==-1&&motion.sample(12).equals(ThunderSwordMotion.IDLE),"Lifecycle reset drops combo state");
        for(double recovery:new double[]{1,20,100,Double.NaN}){
            motion.attack(0,recovery);h.assertTrue(motion.duration()>=6&&motion.duration()<=72,"Attack speed bounded cosmetic duration");
            h.assertTrue(motion.sample(motion.duration()).equals(ThunderSwordMotion.IDLE),"No lingering pose after completion");motion.reset();
        }
        System.out.println("[MYRIAD-THUNDER-FIRSTPERSON] PASS: legacy rig attachment and motion compatibility");
    }

    private static void verifyPrototype(GameTestHelper h) {
        var motion = new TianxingSwordPrototype();
        var idle = motion.sword(0, false);
        var grip = idle.getTranslation(new Vector3f());
        var tip = bladeTip(idle);
        double depth = Math.toDegrees(Math.asin(-new Vector3f(tip).sub(grip).normalize().z));
        h.assertTrue(depth >= 25 && depth <= 35, "Idle blade depth outside +25 to +35 degrees: " + depth);
        h.assertTrue(grip.x > 0 && grip.y < 0 && grip.z < 0 && tip.z < grip.z,
                "Right-hand idle grip is below centre and the tip points into the scene");
        for (int cut = 0; cut < 3; cut++) {
            double start = cut * 7;
            var entry = motion.sword(start, false);
            motion.attack(start, 7);
            h.assertTrue(motion.cut() == cut, "Submitted attacks must advance in authored order");
            h.assertTrue(motion.sword(start, false).equals(entry, 1e-4f),
                    "New swing must inherit the pose displayed at interruption");
            double begin = motion.sweepStart();
            double end = motion.sweepEnd();
            h.assertTrue(begin >= start && end > begin, "Sweep interval must follow input");
            var first = projectedTip(motion.sword(begin, false));
            var last = projectedTip(motion.sword(end, false));
            var delta = last.sub(first, new Vector3f());
            h.assertTrue(first.x * last.x < 0, "Each cut crosses the screen centre line: " + cut);
            boolean direction = switch (cut) {
                case 0 -> delta.x < 0 && delta.y < 0;
                case 1 -> delta.x > 0 && delta.y < 0;
                default -> delta.x < 0 && Math.abs(delta.y) < Math.abs(delta.x);
            };
            h.assertTrue(direction, "Projected sweep direction for cut " + cut + ": " + delta);
            for (int frame = 0; frame <= 48; frame++) {
                double tick = begin + (end - begin) * frame / 48;
                var right = motion.sword(tick, false);
                var left = motion.sword(tick, true);
                h.assertTrue(right.isFinite() && left.isFinite() && right.determinant() > 0 && left.determinant() > 0,
                        "Both hands must have finite, positive-handed transforms");
                assertMirror(h, right.getTranslation(new Vector3f()), left.getTranslation(new Vector3f()));
                assertMirror(h, bladeTip(right), bladeTip(left));
                h.assertTrue(right.equals(motion.sword(tick, false), 1e-6f),
                        "Sampling either hand must leave the current frame unchanged");
            }
        }
        motion.reset();
        h.assertTrue(motion.sword(0, false).equals(idle, 1e-5f), "Reset must discard the last swing");
        // Equip begins on the first visible frame of a changed weapon, advances only toward
        // rest, and can never resume after an attack (even for a long item-swap recovery).
        motion.equip(100, 1);
        h.assertTrue(Math.abs(motion.sword(100, false).m31() - (idle.m31() - .48f)) < 1e-5f,
                "Actual equipment change inherits the drawn item's lowering");
        h.assertTrue(Math.abs(motion.sword(101, false).m31() - (idle.m31() - .288f)) < 1e-5f,
                "Equipment rise follows the vanilla .4 per tick rate");
        motion.attack(101.5, 40);
        var unequipped = new TianxingSwordPrototype();
        unequipped.attack(101.5, 40);
        for (double tick = motion.sweepEnd() + 9; tick <= 146; tick += .25)
            h.assertTrue(motion.sword(tick, false).equals(unequipped.sword(tick, false), 1e-5f),
                    "Equipment lowering must not resume during the authored recovery");
        motion.reset();
        motion.equip(150, .5f);
        h.assertTrue(motion.sword(150, false).m31() < idle.m31() - .2f
                && motion.sword(152, false).equals(idle, 1e-5f), "Next real equip still raises the weapon");
        verifyFollowThrough(h, idle);
        System.out.println("[MYRIAD-TIANXING-PROTOTYPE] PASS: idle depth " + depth
                + " degrees, three projected sweeps, interrupt continuity, bilateral mirroring and repeat sampling");
    }

    private static void verifyFollowThrough(GameTestHelper h, Matrix4f idle) {
        var motion = new TianxingSwordPrototype();
        double cadence = 20 / .7; // Base attack interval of the actual sword.
        for (int cut = 0; cut < 3; cut++) {
            double start = cut * cadence;
            var entry = motion.sword(start, false);
            motion.attack(start, cadence);
            h.assertTrue(motion.cut() == cut && motion.sword(start, false).equals(entry, 1e-4f),
                    "The actual cooldown must preserve chain order and entry pose");
            var from = motion.sword(motion.sweepStart(), false);
            var to = motion.sword(motion.sweepEnd(), false);
            if (cut == 1) {
                h.assertTrue(from.m32() < -.5f, "Backhand must begin outside the near-camera grip pose");
            }
            if (cut > 0) {
                assertGuardAndHalfBladeVisible(h, from);
                assertGuardAndHalfBladeVisible(h, to);
            }
            if (cut == 2) {
                var delta = projectedTip(to).sub(projectedTip(from));
                double tilt = Math.toDegrees(Math.atan(Math.abs(delta.y / delta.x)));
                h.assertTrue(tilt >= 10 && tilt <= 15, "Projected sweep inclination: " + tilt);
                // In the recorded camera, sample the moment the tip crosses the centre line.
                // A foreshortened blade here used to disappear into a short silver streak.
                var crossing = from;
                for (int i = 1; i <= 64; i++) {
                    var t = motion.sweepStart() + (motion.sweepEnd() - motion.sweepStart()) * i / 64.0;
                    var pose = motion.sword(t, false);
                    if (screenPoint(pose, 1.40625f).x <= 0) {
                        crossing = pose;
                        break;
                    }
                }
                h.assertTrue(screenPoint(crossing, 1.40625f).x <= 0
                                && screenPoint(crossing, .171875f).distance(screenPoint(crossing, 1.40625f)) > .18f,
                        "Third cut should show a readable blade while crossing the target");
            }
            for (double t = motion.sweepEnd(); t < motion.settledAt(); t += .1) {
                var returning = motion.sword(t, false);
                var root = screenPoint(returning, .171875f);
                var tip = screenPoint(returning, 1.40625f);
                var segment = tip.sub(root, new Vector3f());
                float nearest = Math.clamp(-root.dot(segment) / segment.lengthSquared(), 0, 1);
                // In this fixture the third cut's return must also clear the target's face.
                float clearance = cut == 2 ? .10f : .01f;
                h.assertTrue(root.fma(nearest, segment).length() > clearance,
                        "Recovery brings the blade across the reticle: cut=" + cut + " tick=" + t);
            }
            h.assertTrue(motion.sword(motion.settledAt(), false).equals(idle, 1e-5f),
                    "A single attack must return to ready when the chain is not continued");
            double retDur = motion.settledAt() - motion.returnStart();
            for (int i = 20; i <= 80; i += 5) {
                double t0 = motion.returnStart() + retDur * (i - 1) / 100.0;
                double t1 = motion.returnStart() + retDur * (i + 1) / 100.0;
                float step = screenPoint(motion.sword(t1, false), 1.40625f)
                        .distance(screenPoint(motion.sword(t0, false), 1.40625f));
                h.assertTrue(step > .005f, "Recovery must not pause mid-arc: cut=" + cut + " pct=" + i + " step=" + step);
            }
            if (cut < 2) h.assertTrue(!motion.sword(start + cadence, false).equals(idle, 1e-3f),
                    "A ready follow-up must keep its authored preparation through normal cooldown");
        }
    }

    /** Centre-origin screen coordinates in units of viewport height under the 70-degree hand FOV. */
    private static Vector3f screenPoint(Matrix4f pose, float height) {
        var point = pose.transformPosition(new Vector3f(0, height, 0));
        float denominator = (float) (-2 * point.z * Math.tan(Math.toRadians(35)));
        return new Vector3f(point.x / denominator, point.y / denominator, 0);
    }

    private static void assertGuardAndHalfBladeVisible(GameTestHelper h, Matrix4f pose) {
        for (float y : new float[]{.15234f, (.171875f + 1.40625f) * .5f}) {
            var point = screenPoint(pose, y);
            h.assertTrue(Math.abs(point.x) < 8f / 9 && Math.abs(point.y) < .5f,
                    "Guard and proximal half-blade should stay visible in a 16:9 viewport: " + point);
        }
    }

    private static void assertMirror(GameTestHelper h, Vector3f right, Vector3f left) {
        h.assertTrue(right.z < 0 && left.distance(-right.x, right.y, right.z) < 1e-4f,
                "Mirrored camera-space sample: right=" + right + ", left=" + left);
    }

    private static Vector3f bladeTip(Matrix4f sword) {
        return sword.transformPosition(new Vector3f(0, 1.40625f, 0));
    }

    private static Vector3f projectedTip(Matrix4f sword) {
        var tip = bladeTip(sword);
        return tip.div(-tip.z);
    }
    private static Vector3f tip(ThunderSwordMotion.Pose p){
        return ThunderSwordRig.wrist(p,0,false).mul(ThunderSwordRig.blade(false)).transformPosition(new Vector3f(0,1.40625f,0));
    }
}
