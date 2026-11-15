package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.skills.rivet.RivetWeaponMotion;
import com.myriad.character.skills.rivet.RivetWeaponRig;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
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
    static void verify(GameTestHelper h){
        var poses=new ArrayList<RivetWeaponMotion.Pose>();var idle=new RivetWeaponMotion();
        for(int i=0;i<=100;i++)poses.add(idle.sample(100,i/100.0,false));
        for(var cue:List.of(cue(2,CHARGE,.85f,3),cue(2,ARC,110,7),cue(3,BURST,3,10),cue(4,SHIELD,0,12),
                cue(5,BEAM,0,3),cue(5,BEAM,1,6),cue(6,SHIELD,0,12),cue(7,CHARGE,.8f,3),
                cue(7,CHARGE,2,3),cue(1,BURST,5,8))){
            var motion=new RivetWeaponMotion();motion.accept(cue,100);
            for(int i=0;i<=48;i++)poses.add(motion.sample(100+i/4.0,0,false));
        }
        var vertices=itemVertices();int count=0;
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
}
