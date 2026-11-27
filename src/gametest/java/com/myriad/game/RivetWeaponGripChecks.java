package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.skills.rivet.RivetWeaponMotion;
import com.myriad.character.skills.rivet.RivetWeaponRig;
import com.myriad.client.firstperson.RivetHammerContact;
import com.myriad.client.firstperson.RivetHammerPrototype;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Checks the actual renderer's palm/item matrices, not a separately invented intended pose. */
final class RivetWeaponGripChecks {
    private static AbilityVfxPayload cue(int variant,int kind,float stage,int life){
        return new AbilityVfxPayload(1,kind==SHIELD?1:-1,13,variant,kind,Vec3.ZERO,new Vec3(0,0,1),.7f,stage,life,100,false);
    }
    private static List<Vector3f> itemVertices(){
        try(var in=RivetWeaponGripChecks.class.getResourceAsStream("/assets/myriad/models/item/rivet_hammer.json")){
            if(in==null)throw new IllegalStateException("Missing actual hammer item model");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            var out=new ArrayList<Vector3f>();
            for(var element:root.getAsJsonArray("elements")){
                var part=element.getAsJsonObject();
                if(part.has("rotation"))throw new IllegalStateException("Grip projection checker must support the actual model rotation before accepting it");
                var from=part.getAsJsonArray("from");var to=part.getAsJsonArray("to");
                for(int i=0;i<8;i++)out.add(new Vector3f((i&1)==0?from.get(0).getAsFloat():to.get(0).getAsFloat(),
                        (i&2)==0?from.get(1).getAsFloat():to.get(1).getAsFloat(),(i&4)==0?from.get(2).getAsFloat():to.get(2).getAsFloat()));
            }
            return out;
        }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
    }
    static void verifyContactOwnership() {
        var motion = new RivetWeaponMotion();
        var contact = new com.myriad.client.firstperson.RivetHammerContact();
        contact.start(380, 384.1, 386);
        var receipt = new AbilityVfxPayload(1, -1, 13, 0, BURST, Vec3.ZERO, Vec3.ZERO,
                0, 6, 3, 381, true);
        if (!com.myriad.character.skills.rivet.RivetVfx.basicContact(receipt))
            throw new AssertionError("Expected an owner-only BASIC_CONTACT receipt");
        contact.receive(receipt.started(), 381.1);
        var burst = new AbilityVfxPayload(1, -1, 13, 0, BURST, Vec3.ZERO, new Vec3(0, 0, 1),
                .55f, 0, 7, 381, false);
        if (motion.accept(burst, 381.1) || !motion.sample(381.12, 0, false).equals(RivetWeaponMotion.IDLE))
            throw new AssertionError("The same-tick generic impact burst cannot take ownership of the hammer");
        if (Math.abs(contact.impactAge(384.35) - .25) > 1e-8)
            throw new AssertionError("The confirmed strike must reach its contact window");

        contact.start(400, 404.1, 406);
        contact.receive(401, 401.1);
        var skill = new AbilityVfxPayload(1, -1, 13, 2, CHARGE, Vec3.ZERO, new Vec3(0, 0, 1),
                3, .8f, 3, 401, false);
        if (!motion.accept(skill, 401.1))
            throw new AssertionError("The hammer skill windup must take ownership of the weapon");
        contact.cancel();
        if (contact.impactAge(404.35) != -1)
            throw new AssertionError("A superseded strike cannot trigger a delayed hitstop");
    }

    static void verify(GameTestHelper h){
        RivetContactChecks.verify();
        verifyContactOwnership();
        var poses=new ArrayList<RivetWeaponMotion.Pose>();var idle=new RivetWeaponMotion();
        for(int i=0;i<=100;i++)poses.add(idle.sample(100,i/100.0,false));
        for(var cue:List.of(cue(2,CHARGE,.85f,3),cue(2,ARC,110,7),cue(3,BURST,3,10),cue(4,SHIELD,0,12),
                cue(5,BEAM,0,3),cue(5,BEAM,1,6),cue(6,SHIELD,0,12),cue(7,CHARGE,.8f,3),
                cue(7,CHARGE,2,3),cue(1,BURST,5,8))){
            var motion=new RivetWeaponMotion();motion.accept(cue,100);
            for(int i=0;i<=48;i++)poses.add(motion.sample(100+i/4.0,0,false));
        }
        var vertices=itemVertices();
        verifyHammerPrototype(h, vertices);
        int count=0;
        for(var pose:poses)for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
            var grip=RivetWeaponRig.grip(pose,left);
            var rawItem=RivetWeaponRig.rawItem(pose,left);
            var rawHand=RivetWeaponRig.rawHand(pose,slim,left);
            var itemPalm=rawItem.transformPosition(new Vector3f(0,RivetWeaponRig.GRIP_Y,0));
            var skinPalm=rawHand.transformPosition(RivetWeaponRig.skinPalm(slim,left));
            h.assertTrue(itemPalm.distance(grip)<1E-6&&skinPalm.distance(grip)<1E-6,"Actual item grip and wide/slim left/right skin palm remain welded through every action");
            h.assertTrue(rawItem.determinant()>0&&rawHand.determinant()>0,"Mirrored grips retain positive winding and ordinary face culling");
            h.assertTrue(Float.isFinite(grip.x)&&Float.isFinite(grip.y)&&Float.isFinite(grip.z)
                            &&(left?-grip.x:grip.x)>0&&grip.z<-.05,
                    "The actual hand grip remains finite and on the operating side of the camera");
            var shaft=rawItem.transformDirection(new Vector3f(0,1,0)).normalize();
            var acrossFist=rawHand.transformDirection(new Vector3f(1,0,0)).normalize();
            var forearm=rawHand.transformDirection(new Vector3f(0,1,0)).normalize();
            h.assertTrue(acrossFist.dot(shaft)>.819&&Math.abs(forearm.dot(shaft))<.574,
                    "The handle crosses the fist with at most 35 degrees of strike-time wrist flex, instead of extending through the forearm");
            for(var source:vertices){
                var v=rawItem.transformPosition(new Vector3f(source));
                h.assertTrue(Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z)&&v.z<-.05,
                        "The real hammer stays in front of the hand near plane without nonfinite projection");
                h.assertTrue((left?-v.x:v.x)>.005,"The whole actual hammer stays on its operating side instead of sweeping through the opposite HUD");
            }
            for(float hudFov:new float[]{60,70}){
                double y=.5-.5*grip.y/(-grip.z*Math.tan(Math.toRadians(hudFov/2)));
                h.assertTrue(y>0&&y<1,"The actual skin/item grip remains on screen under standard and underwater hand projections");
            }
            count++;
        }
        var stroke=new RivetWeaponMotion();
        var restHand=RivetWeaponRig.rawHand(RivetWeaponMotion.IDLE,false,false);
        var restShaft=RivetWeaponRig.rawItem(RivetWeaponMotion.IDLE,false).transformDirection(new Vector3f(0,1,0)).normalize();
        h.assertTrue(restHand.transformDirection(new Vector3f(1,0,0)).normalize().dot(restShaft)>.9999,
                "The relaxed grip retains a neutral wrist across the handle");
        var raised=RivetWeaponRig.rawItem(stroke.sample(100,.24,false),false).transformDirection(new Vector3f(-1,0,0)).normalize();
        var contact=RivetWeaponRig.rawItem(stroke.sample(100,.50,false),false).transformDirection(new Vector3f(-1,0,0)).normalize();
        h.assertTrue(raised.y>.3&&contact.y<-.5&&contact.z<0&&raised.dot(contact)<.4,
                "The real anvil face rotates from a raised backswing to a forward-down impact rather than translating one presentation pose");
        var recovered=stroke.sample(100,1,false);
        h.assertTrue(recovered.equals(RivetWeaponMotion.IDLE),"The real stroke recovers to the selected relaxed grip");
        System.out.println("[MYRIAD-RIVET-GRIP] PASS: "+count+" real pose/hand/skin matrix samples, welded palm, neutral wrist/handle relationship, directional backswing and forward-down strike, actual model operating-side vertices, positive winding and standard/underwater hand projection; offline geometry only");
    }

    private static Vector3f pixel(Matrix4f item, float x, float y, float z) {
        return item.transformPosition(new Vector3f(x / 16 - .5f, y / 16 - .5f, z / 16 - .5f));
    }

    // In units of viewport height, centred on the crosshair. Hand projection is 70 degrees.
    private static Vector3f project(Vector3f p) {
        float divisor = (float) (-2 * p.z * Math.tan(Math.toRadians(35)));
        return new Vector3f(p.x / divisor, p.y / divisor, 0);
    }

    private static void verifyHammerPrototype(GameTestHelper h, List<Vector3f> corners) {
        var motion = new RivetHammerPrototype();
        var idle = motion.item(0, false);
        var grip = pixel(idle, 0, 5.5f, 0);
        var core = pixel(idle, 0, 23, 0);
        double depth = Math.toDegrees(Math.asin(-new Vector3f(core).sub(grip).normalize().z));
        h.assertTrue(depth >= 25 && depth <= 45, "Rest grip to hammer-head centre depth: " + depth);
        for (var corner : corners) {
            var p = project(pixel(idle, corner.x, corner.y, corner.z));
            h.assertTrue(p.x > .04f && p.x < 8f / 9, "Idle hammer clears the aiming axis");
            if (corner.y >= 1)
                h.assertTrue(Math.abs(p.y) < .5f, "The head and ribbed gripping surface fit at 16:9: " + p);
        }
        double cadence = 20 / .70;
        for (int cut = 0; cut < 3; cut++) {
            double start = cut * cadence;
            var before = motion.item(start, false);
            motion.attack(start, cadence);
            h.assertTrue(motion.cut() == cut && motion.item(start, false).equals(before, 1e-4f),
                    "A submitted attack inherits the frame displayed through the normal cooldown");
            var from = project(RivetHammerPrototype.strikingFace(motion.item(motion.sweepStart(), false)));
            var to = project(RivetHammerPrototype.strikingFace(motion.item(motion.sweepEnd(), false)));
            if (cut < 2) {
                h.assertTrue(from.x * to.x < 0 && (cut == 0 ? from.x > to.x : from.x < to.x),
                        "The two sweeps must carry the real striking die across the centre in opposite directions");
                h.assertTrue(!motion.item(start + cadence, false).equals(idle, 1e-3f),
                        "The next eligible swing should enter from the opposite guard");
            } else {
                h.assertTrue(from.y - to.y > .3f,
                        "Downward blow drops the head visibly, by at least 30 percent of viewport height");
                // In the fixed forward-facing fixture the die should cross the aimed target
                // when contact starts. Check the next two 60 fps frames of an early receipt.
                var contact = new RivetHammerContact();
                contact.start(start, motion.contactOpen(), motion.contactClose());
                contact.receive((long) Math.floor(start), start);
                for (int f = 0; f <= 2; f++) {
                    double t = motion.contactOpen() + f * (20.0 / 60);
                    var die = project(RivetHammerPrototype.strikingFace(motion.item(contact.animationTime(t), false)));
                    h.assertTrue(die.length() < .12f, "At first contact, the real die is near the aiming point");
                }
                var end = motion.sweepEnd();
                var head = project(pixel(motion.item(end, false), 0, 23, 0));
                boolean sank = false;
                for (double t = end + .1; t < end + 2.5; t += .1) {
                    var item = motion.item(t, false);
                    var coreAfter = project(pixel(item, 0, 23, 0));
                    sank |= coreAfter.y < head.y - .015f;
                    h.assertTrue(project(RivetHammerPrototype.strikingFace(item)).y > -.5f,
                            "The die must remain visible at the bottom of the heavy overshoot");
                }
                h.assertTrue(sank, "Hammer head continues to settle down after the main stroke");
            }
            for (int fps : new int[]{30, 60, 120}) {
                double step = 20.0 / fps;
                for (int f = 0; start + step * f <= motion.settledAt(); f++) {
                    double t = start + step * f;
                    var right = motion.item(t, false);
                    var left = motion.item(t, true);
                    h.assertTrue(right.isFinite() && left.isFinite()
                                    && right.determinant() > 0 && left.determinant() > 0,
                            "Both physical hands must preserve the model's face winding");
                    for (var anchor : List.of(new Vector3f(0, 5.5f, 0), new Vector3f(-10, 23, 0),
                            new Vector3f(9.5f, 23, 0), new Vector3f(-10, 26.5f, 3))) {
                        var r = pixel(right, anchor.x, anchor.y, anchor.z);
                        var l = pixel(left, anchor.x, anchor.y, -anchor.z);
                        h.assertTrue(r.z < -.05f && l.distance(-r.x, r.y, r.z) < 1e-4f,
                                "Grip, striking die and rear bearing must mirror without swapping the two ends");
                    }
                    h.assertTrue(RivetHammerPrototype.strikingFace(right).distance(pixel(right, -10, 23, 0)) < 1e-5f,
                            "Contact anchor stays on the striking die's actual outside face");
                    h.assertTrue(motion.item(t, false).equals(right, 1e-6f),
                            "Rendering another hand in this frame must not advance the hammer");
                }
            }
            h.assertTrue(motion.item(motion.settledAt(), false).equals(idle, 1e-5f),
                    "An uninterrupted swing settles back at the resting grip");
        }
        System.out.println("[MYRIAD-RIVET-PROTOTYPE] PASS: model anchors, idle depth " + depth
                + " degrees, alternating sweeps, downward blow, mirrored dies and 30/60/120 fps sampling");
    }
}
