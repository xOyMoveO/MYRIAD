package com.myriad.game;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/** Actual mesh recipe and wire validation, without loading client classes on a dedicated server. */
final class AbilityVfxChecks {
    static void run(GameTestHelper h) {
        int count=0;
        for(int theme=0;theme<5;theme++)for(int branch=0;branch<8;branch++)for(int kind=0;kind<12;kind++){
            var p=new AbilityVfxPayload(1,-1,theme,branch,kind,new Vec3(0,64,0),new Vec3(0,70,12),3,120,80,100,false);
            h.assertTrue(p.valid(),"Valid five-role recipe");
            var quads=AbilityVfxGeometry.build(p,3.5f,false);
            h.assertTrue(!quads.isEmpty()&&quads.size()<=192,"Finite tessellation budget");
            for(var q:quads){
                for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()})
                    h.assertTrue(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)
                            &&v.distanceToSqr(p.from())<400,"No NaN/oversized mesh");
                h.assertTrue(q.alpha()>0&&q.alpha()<=1,"Safe alpha");
                h.assertTrue(q.material()>=0&&q.material()<=com.myriad.character.ability.AbilityVfxMaterials.MAX,"VFX uses a registered atlas sprite");
                h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startU()<q.endU(),"Continuous ribbon UVs stay inside their atlas sprite");
            }
            h.assertTrue(AbilityVfxGeometry.build(p,81,false).isEmpty(),"Expired meshes vanish");
            h.assertTrue(AbilityVfxGeometry.build(p,3.5f,true).size()<=quads.size(),"Low detail reduces decorative geometry");
            count++;
        }
        var vertical=new AbilityVfxPayload(1,-1,1,3,AbilityVfxGeometry.BEAM,
                Vec3.ZERO,new Vec3(0,10,0),.5f,0,12,100,false);
        h.assertTrue(!AbilityVfxGeometry.build(vertical,2,false).isEmpty(),"Vertical beams use a stable basis");
        for(int theme=0;theme<5;theme++) {
            var shortPreview=new AbilityVfxPayload(1,-1,theme,0,AbilityVfxGeometry.RETICLE,
                    Vec3.ZERO,new Vec3(0,0,1),3,360,2,100,true);
            h.assertTrue(AbilityVfxGeometry.build(shortPreview,1,false).stream().anyMatch(q->q.alpha()>=.85f),
                    "Two-tick previews keep a visible body on their second tick");
            for(int kind:new int[]{AbilityVfxGeometry.SEAL,AbilityVfxGeometry.ARC,AbilityVfxGeometry.FLIGHT}) {
                var volume=new AbilityVfxPayload(1,-1,theme,0,kind,Vec3.ZERO,new Vec3(0,0,3),.6f,120,80,100,false);
                var mesh=AbilityVfxGeometry.build(volume,3,false);
                double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
                for(var q:mesh)for(var v:new Vec3[]{q.a(),q.b(),q.c(),q.d()}){minY=Math.min(minY,v.y);maxY=Math.max(maxY,v.y);}
                h.assertTrue(maxY-minY>=.1,"Seals, slash arcs and projectiles have vertical thickness");
            }
        }
        var zero=new AbilityVfxPayload(1,-1,0,2,AbilityVfxGeometry.TRAIL,Vec3.ZERO,Vec3.ZERO,.1f,0,4,100,false);
        h.assertTrue(AbilityVfxGeometry.build(zero,1,false).isEmpty(),"Zero-length trail creates no invalid ribbon");
        var invalid=new AbilityVfxPayload(1,-1,0,2,0,new Vec3(Double.NaN,0,0),Vec3.ZERO,1,0,4,100,false);
        h.assertTrue(!invalid.valid()&&AbilityVfxGeometry.build(invalid,1,false).isEmpty(),"Reject corrupt parameters");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            AbilityVfxPayload.CODEC.encode(buffer,vertical);
            h.assertTrue(AbilityVfxPayload.CODEC.decode(buffer).equals(vertical),"Visual payload wire roundtrip");
        }finally{buffer.release();}
        nightChecks(h);
        ElementVfxChecks.verify(h);
        KehengVisualChecks.verify(h);
        YikeVisualChecks.verify(h);
        RivetVfxChecks.verify(h);
        RosterVfxTuningChecks.verify(h);
        YikeWeaponAnimationChecks.run(h);
        System.out.println("[MYRIAD-VFX] PASS: "+count+" role/branch/shape recipes, budget, expiry, volume, short-life visibility, vertical/zero vectors, wire");
    }
    private static void nightChecks(GameTestHelper h){
        for(int kind:new int[]{AbilityVfxGeometry.FLIGHT,AbilityVfxGeometry.ARC,AbilityVfxGeometry.BURST,
                AbilityVfxGeometry.SHIELD,AbilityVfxGeometry.CHARGE}){
            var p=new AbilityVfxPayload(1,-1,0,2,kind,Vec3.ZERO,new Vec3(1.5,0,0),.6f,90,8,100,false);
            var mesh=AbilityVfxGeometry.build(p,.75f,false);
            h.assertTrue(mesh.stream().anyMatch(q->q.material()>0),"Night effects sample real alpha silhouettes, not only solid-colour quads");
            if(kind==AbilityVfxGeometry.FLIGHT)h.assertTrue(mesh.stream().anyMatch(q->q.material()==com.myriad.character.ability.AbilityVfxMaterials.NIGHT_NEEDLE_MODEL)&&mesh.stream().anyMatch(q->q.material()==com.myriad.character.ability.AbilityVfxMaterials.NIGHT_GRAY_WAKE),
                    "Shadow dagger has the approved flying needle model and a separate tapered trail");
        }
        String file=System.getProperty("myriad.nightPreview","");
        if(!file.isBlank()){
            var shots=new java.util.ArrayList<java.util.Map<String,Object>>();
            int[] kinds={9,9,9,4,3,3,2,2,7,10,8,5};int[] variants={2,2,2,2,2,3,5,6,7,6,4,5};
            for(int i=0;i<kinds.length;i++){
                float time=i<3?.25f+i*.6f:2;
                var p=new AbilityVfxPayload(1,-1,0,variants[i],kinds[i],Vec3.ZERO,new Vec3(1.5,0,0),.7f,90,i<3?2:8,100,false);
                shots.add(java.util.Map.of("kind",kinds[i],"variant",variants[i],"age",time,"mesh",AbilityVfxGeometry.build(p,time,false)));
            }
            try{var path=java.nio.file.Path.of(file);java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
            }catch(java.io.IOException e){throw new IllegalStateException("Cannot export requested QA geometry",e);}
        }
    }
}
