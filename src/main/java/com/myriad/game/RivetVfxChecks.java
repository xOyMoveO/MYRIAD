package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Production mesh/data validity only; no subjective visual or sound acceptance. */
final class RivetVfxChecks {
    static void verify(GameTestHelper h) {
        int frames=0;
        for(int branch=0;branch<8;branch++)for(int kind:List.of(AbilityVfxGeometry.WALL,AbilityVfxGeometry.CHARGE,
                AbilityVfxGeometry.ARC,AbilityVfxGeometry.BEAM,AbilityVfxGeometry.SHIELD,AbilityVfxGeometry.SEAL,
                AbilityVfxGeometry.BURST,AbilityVfxGeometry.TRAIL))for(int stage=0;stage<2;stage++){
            var payload=new AbilityVfxPayload(1,-1,13,branch,kind,new Vec3(0,64,0),new Vec3(0,64,3),
                    kind==AbilityVfxGeometry.SEAL?4:kind==AbilityVfxGeometry.ARC?3:.7f,stage,10,100,false);
            h.assertTrue(payload.valid(),"Industrial effect uses a valid production packet");
            for(float age:new float[]{.25f,2,6,9.75f})for(boolean reduced:new boolean[]{false,true}){
                var mesh=AbilityVfxGeometry.build(payload,age,reduced);
                h.assertTrue(!mesh.isEmpty()&&mesh.size()<=192,"Industrial stage stays within its real mesh budget");
                for(var quad:mesh){h.assertTrue(quad.alpha()>0&&quad.alpha()<=1&&quad.material()>=0&&quad.material()<=AbilityVfxMaterials.MAX,"Industrial stage has finite alpha and registered material");
                    for(var point:List.of(quad.a(),quad.b(),quad.c(),quad.d()))h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)&&point.distanceToSqr(payload.from())<400,"Industrial stage contains only finite bounded vertices");}
                if(kind==AbilityVfxGeometry.WALL||kind==AbilityVfxGeometry.ARC||kind==AbilityVfxGeometry.CHARGE&&branch==2)
                    h.assertTrue(mesh.stream().anyMatch(q->q.material()==AbilityVfxMaterials.RIVET_MODEL),"Actual industrial 3D body emits model faces");
                frames++;
            }
            h.assertTrue(AbilityVfxGeometry.build(payload,10,false).isEmpty(),"Industrial effect vanishes at actual expiry");
            var wire=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{AbilityVfxPayload.CODEC.encode(wire,payload);h.assertTrue(AbilityVfxPayload.CODEC.decode(wire).equals(payload),"Industrial state survives the actual wire codec");}finally{wire.release();}
        }
        var loader=RivetVfxChecks.class.getClassLoader();
        for(var id:List.of("rivet_station_armor","rivet_breach_rhythm","rivet_impact_hammer","rivet_tactical_anchor","rivet_emergency_strut","rivet_tow_cable","rivet_hold_protocol","rivet_breach_charge","rivet_tow_cable_2")){
            try(var stream=loader.getResourceAsStream("assets/myriad/textures/gui/sprites/skill_icons/generated/rivet/"+id+".png")){
                h.assertTrue(stream!=null,"Every selected/recast action has a packaged icon: "+id);var image=javax.imageio.ImageIO.read(stream);h.assertTrue(image!=null&&image.getWidth()==64&&image.getHeight()==64,"Industrial action icon has its actual 64-pixel dimensions");
            }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        }
        verifyPolish(h);
        RivetWeaponGripChecks.verify(h);
        System.out.println("[MYRIAD-RIVET-ASSETS] PASS: "+frames+" production mesh stage/detail frames, finite vertices, <=192 faces, actual model material, expiry, wire and nine action icons; subjective presentation untested");
    }
    private static AbilityVfxPayload recipe(int variant,int kind,float stage,int life,long started){
        return new AbilityVfxPayload(1,-1,13,variant,kind,Vec3.ZERO,new Vec3(0,0,3),.7f,stage,life,started,false);
    }
    private static void verifyPolish(GameTestHelper h){
        var models=RosterProjectileModels.load("rivet_models_mesh.json");
        for(var world:List.of(recipe(3,AbilityVfxGeometry.WALL,1,5,100),recipe(6,AbilityVfxGeometry.SEAL,8,6,100),
                recipe(3,AbilityVfxGeometry.BURST,3,10,100))){
            h.assertTrue(com.myriad.character.skills.rivet.RivetVfx.survivesOwner(world,false,false),
                    "Position-authorized anchor and region remain when their owner entity is outside tracking");
            h.assertTrue(!com.myriad.character.skills.rivet.RivetVfx.survivesOwner(world,true,false),
                    "A known dead caster still clears its position-authorized presentation");
            h.assertTrue(AbilityVfxGeometry.build(world,world.life(),false).isEmpty(),"Owner-independent presentation still expires at its original TTL");
        }
        for(var attached:List.of(recipe(2,AbilityVfxGeometry.ARC,110,7,100),recipe(7,AbilityVfxGeometry.CHARGE,.7f,3,100),
                recipe(5,AbilityVfxGeometry.BEAM,0,3,100),recipe(4,AbilityVfxGeometry.SHIELD,0,12,100))){
            h.assertTrue(!com.myriad.character.skills.rivet.RivetVfx.survivesOwner(attached,false,false),
                    "Missing owner still removes moving attacks, cables and actor shields");
            h.assertTrue(com.myriad.character.skills.rivet.RivetVfx.survivesOwner(attached,true,true),"A live tracked owner retains its ordinary presentation");
        }
        for(var normal:List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1))){
            var wall=new AbilityVfxPayload(1,-1,13,3,AbilityVfxGeometry.WALL,Vec3.ZERO,normal,.38f,1,5,100,false);
            for(boolean reduced:new boolean[]{false,true}){
                var mesh=AbilityVfxGeometry.build(wall,1,reduced);
                for(var quad:mesh)if(quad.material()==AbilityVfxMaterials.RIVET_MODEL)
                    for(var vertex:List.of(quad.a(),quad.b(),quad.c(),quad.d()))
                        h.assertTrue(vertex.dot(normal)>=.0149,"Anchor backplate stays outside the actual wall in all four orientations");
            }
        }
        h.assertTrue(models.faces("hammer_lod")<models.faces("hammer")&&models.faces("anchor_lod")<models.faces("anchor"),
                "Distance fallback uses a genuinely smaller closed-volume mesh");
        for(int kind:List.of(AbilityVfxGeometry.CHARGE,AbilityVfxGeometry.ARC))for(boolean reduced:new boolean[]{false,true}){
            var north=new AbilityVfxPayload(1,-1,13,2,kind,Vec3.ZERO,new Vec3(0,0,3),3,.6f,7,100,false);
            var east=new AbilityVfxPayload(1,-1,13,2,kind,Vec3.ZERO,new Vec3(3,0,0),3,.6f,7,100,false);
            var a=AbilityVfxGeometry.build(north,2,reduced).stream().filter(q->q.material()==AbilityVfxMaterials.RIVET_MODEL).toList();
            var b=AbilityVfxGeometry.build(east,2,reduced).stream().filter(q->q.material()==AbilityVfxMaterials.RIVET_MODEL).toList();
            h.assertTrue(a.size()==b.size(),"Turning keeps the same complete hammer body");
            for(int i=0;i<a.size();i++){
                var x=a.get(i);var y=b.get(i);var va=List.of(x.a(),x.b(),x.c(),x.d());var vb=List.of(y.a(),y.b(),y.c(),y.d());
                for(int j=0;j<4;j++){
                    var vertex=va.get(j);var rotated=new Vec3(vertex.z,vertex.y,-vertex.x);
                    h.assertTrue(rotated.distanceToSqr(vb.get(j))<1E-12,"Every solid hammer vertex follows owner facing and the real sweep, including LOD");
                }
            }
        }
        int stages=0;
        for(int stage=0;stage<=5;stage++)for(float age:new float[]{0,.5f,2,4,6,10,13.75f})for(boolean reduced:new boolean[]{false,true}){
            var mesh=AbilityVfxGeometry.build(recipe(stage==3||stage==4?3:stage==5?1:2,AbilityVfxGeometry.BURST,stage,14,100),age,reduced);
            h.assertTrue(!mesh.isEmpty()&&mesh.size()<=192,"Every contact/deployment/release stage has a bounded real mesh");
            for(var q:mesh)for(var vertex:List.of(q.a(),q.b(),q.c(),q.d()))
                h.assertTrue(Double.isFinite(vertex.x)&&Double.isFinite(vertex.y)&&Double.isFinite(vertex.z),"Staged contact geometry remains finite");
            stages++;
        }
        var ordinary=AbilityVfxGeometry.build(recipe(2,AbilityVfxGeometry.BURST,0,14,100),4,false);
        var anchored=AbilityVfxGeometry.build(recipe(2,AbilityVfxGeometry.BURST,1,14,100),4,false);
        h.assertTrue(!ordinary.equals(anchored),"Ordinary wall contact and anchor clamp are different production geometry");
        var swing=recipe(2,AbilityVfxGeometry.ARC,110,7,100);
        var held=com.myriad.character.skills.rivet.RivetVfx.firstPerson(swing,AbilityVfxGeometry.build(swing,2,false));
        h.assertTrue(!held.isEmpty()&&held.stream().noneMatch(q->q.material()==AbilityVfxMaterials.RIVET_MODEL),
                "First person retains the strike wake without drawing a second world hammer");
        var motion=new com.myriad.character.skills.rivet.RivetWeaponMotion();
        motion.accept(recipe(2,AbilityVfxGeometry.CHARGE,.3f,3,100),100);
        // Allow the two-tick entry gesture to settle before comparing the unchanged authoritative progress.
        var paused=motion.sample(102,0,false);
        h.assertTrue(paused.equals(motion.sample(103,0,false)),"Preparation does not invent progress between authoritative samples");
        motion.accept(recipe(2,AbilityVfxGeometry.CHARGE,.8f,3,102),102);
        var advanced=motion.sample(103,0,false);
        motion.accept(recipe(2,AbilityVfxGeometry.CHARGE,.1f,3,101),103);
        h.assertTrue(advanced.equals(motion.sample(103,0,false)),"Late snapshots cannot rewind the held hammer");
        h.assertTrue(motion.sample(120,0,false).equals(com.myriad.character.skills.rivet.RivetWeaponMotion.IDLE),
                "Interrupted or missing preparation expires to rest without a fabricated release");
        motion.accept(recipe(2,AbilityVfxGeometry.ARC,110,7,121),121);
        h.assertTrue(!motion.sample(123,0,false).equals(com.myriad.character.skills.rivet.RivetWeaponMotion.IDLE),"Committed release starts its own swing");
        motion.accept(recipe(2,AbilityVfxGeometry.CANCEL,1,1,123),123);
        h.assertTrue(motion.sample(123,0,false).equals(com.myriad.character.skills.rivet.RivetWeaponMotion.IDLE),"Actual skill cancellation clears its held pose");
        for(int i=0;i<=120;i++){
            var pose=motion.sample(124+i/10.0,i/120.0,i%2==0);
            h.assertTrue(Double.isFinite(pose.x())&&Double.isFinite(pose.pitch())&&pose.z()<-.7&&pose.z()>-1.6,
                    "Cosmetic basic attack palm stays finite and in front of the camera");
        }
        System.out.println("[MYRIAD-RIVET-POLISH] PASS: "+stages+" extra real contact stage/detail frames; four wall-normal attachments, real LOD, distinct wall/anchor stages, duplicate-solid suppression and authoritative pose lifecycle; no graphical acceptance");
    }
}
