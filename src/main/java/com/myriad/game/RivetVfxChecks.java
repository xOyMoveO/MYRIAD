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
        System.out.println("[MYRIAD-RIVET-ASSETS] PASS: "+frames+" production mesh stage/detail frames, finite vertices, <=192 faces, actual model material, expiry, wire and nine action icons; subjective presentation untested");
    }
}
