package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.lingyuan.rework.SwordModelAsset;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Verifies the real packaged cuboid mesh and recipe transforms, not a substitute test model. */
final class LingyuanModelChecks {
    static void verify(GameTestHelper h){
        h.assertTrue(com.myriad.registry.ModItems.initialWeaponItem("lingyuan")==com.myriad.registry.ModItems.LINGYUAN_SWORD.get(),
                "Existing and newly selected Lingyuan players keep the same bound item, now with the new model");
        h.assertTrue(SwordModelAsset.sourceParts("body")==186,"Near view uses all 186 approved source cuboids");
        h.assertTrue(SwordModelAsset.sourceParts("body_lod")==48,"Far view has a complete compact sword, not a truncated near mesh");
        h.assertTrue(SwordModelAsset.faceCount("body")>SwordModelAsset.faceCount("body_lod")
                &&SwordModelAsset.faceCount("body")<186*6,"Hidden-face culling retains a detailed near model");
        for(String name:List.of("trail_cyan","trail_blue","sigil","spark","afterimage"))
            h.assertTrue(SwordModelAsset.faceCount(name)>0,"Independent effect layer is packaged: "+name);
        Vec3 tip=new Vec3(5,7,11);
        for(Vec3 direction:List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),
                new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,2,3).normalize())){
            var out=new ArrayList<Quad>();double length=1.6;
            SwordModelAsset.append(out,"body",SwordModelAsset.centerAtTip(tip,direction,length),direction,length,0,1);
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(var q:out){
                h.assertTrue(q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL,"Model samples its real pixel atlas");
                h.assertTrue(q.startU()>=0&&q.startU()<=1&&q.endU()>=0&&q.endU()<=1
                        &&q.startV()>=0&&q.startV()<=1&&q.endV()>=0&&q.endV()<=1,"Two-dimensional face UVs stay in the sprite");
                for(var v:List.of(q.a(),q.b(),q.c(),q.d())){
                    h.assertTrue(AbilityTargeting.finite(v),"All six axes and diagonal model poses are finite");
                    double along=v.subtract(tip).dot(direction);min=Math.min(min,along);max=Math.max(max,along);
                }
            }
            h.assertTrue(Math.abs(max)<1E-6&&Math.abs(min+length)<1E-6,"Actual model tip lands on the authoritative endpoint, including vertical flight");
        }
        for(String layer:List.of("trail_cyan","trail_blue","sigil","afterimage")){
            var first=new ArrayList<Quad>();var moved=new ArrayList<Quad>();var looped=new ArrayList<Quad>();
            SwordModelAsset.append(first,layer,Vec3.ZERO,new Vec3(0,1,0),2,0,1);
            SwordModelAsset.append(moved,layer,Vec3.ZERO,new Vec3(0,1,0),2,Math.PI/2,1);
            SwordModelAsset.append(looped,layer,Vec3.ZERO,new Vec3(0,1,0),2,Math.PI*2,1);
            h.assertTrue(first.size()==moved.size()&&first.size()==looped.size(),"Layer topology is stable through its loop");
            h.assertTrue(first.getFirst().a().distanceToSqr(moved.getFirst().a())>1E-8,"Layer really moves, not only an animation label");
            for(int i=0;i<first.size();i++)h.assertTrue(first.get(i).a().distanceToSqr(looped.get(i).a())<1E-10,"Layer loop has no reset jump");
        }
        int stages=0;
        for(int kind:new int[]{FLIGHT,BEAM,ARC,SEAL,SHIELD,CHARGE,TRAIL,PILLAR,BURST})for(int variant=0;variant<8;variant++){
            var p=new AbilityVfxPayload(1,-1,5,variant,kind,Vec3.ZERO,new Vec3(1,.3,2),1.2f,100,8,100,false);
            var mesh=AbilityVfxGeometry.build(p,.75f,false);var low=AbilityVfxGeometry.build(p,.75f,true);
            h.assertTrue(mesh.size()<=maxQuads(p)&&low.size()<mesh.size(),"Every active Lingyuan composition has complete bounded LOD");
            h.assertTrue(mesh.stream().allMatch(q->q.startV()>=0&&q.endV()<=1),"Recipe preserves model V coordinates");stages++;
        }
        var warning=new AbilityVfxPayload(1,-1,5,6,SEAL,Vec3.ZERO,new Vec3(1,0,0),4,359,5,100,false);
        h.assertTrue(AbilityVfxGeometry.build(warning,1,false).stream().noneMatch(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL),
                "Warning does not spawn active swords before the skill triggers");
        var baseline=new AbilityVfxPayload(1,-1,4,0,FLIGHT,Vec3.ZERO,new Vec3(1,0,0),.2f,0,3,100,false);
        h.assertTrue(maxQuads(baseline)==192,"Other roles retain their original budget");
        verifySkillProfiles(h);
        verifyCrescentOrientation(h);
        LingyuanFlipbookChecks.verify(h);
        exportPreview();
        System.out.println("[MYRIAD-LINGYUAN-MODEL] PASS: 186 source cuboids, 48-cuboid LOD, 7 flight axes, UV2D, 4 real loop layers, "+stages+" recipe stages and warning separation");
    }
    private static void verifyCrescentOrientation(GameTestHelper h){
        int faces=0;
        for(Vec3 aim:List.of(new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),
                new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,2,3).normalize(),new Vec3(-2,-3,1).normalize(),Vec3.ZERO)){
            Vec3 f=aim.lengthSqr()<1E-8?new Vec3(0,0,1):aim;
            for(int kind:new int[]{FLIGHT,ARC,TRAIL,CHARGE,BEAM,PILLAR,BURST,SEAL,SHIELD})
            for(float age:new float[]{.25f,3.5f,7.75f})for(boolean reduced:new boolean[]{false,true}){
                int variant=kind==FLIGHT||kind==CHARGE?7:kind==SHIELD?3:kind==BEAM||kind==PILLAR?6:2;
                var p=new AbilityVfxPayload(1,-1,5,variant,kind,Vec3.ZERO,aim.scale(2),1,80,8,100,false);
                var mesh=AbilityVfxGeometry.build(p,age,reduced);int crescents=0;
                for(var q:mesh)if(q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_SLASH){
                    Vec3 visibleFront=q.a().subtract(q.b()).normalize();
                    h.assertTrue(visibleFront.dot(f)>1-1E-8,"Texture convex -U leads the true 3D direction, on both faces and all layer rolls");
                    Vec3 normal=q.b().subtract(q.a()).cross(q.d().subtract(q.a())).normalize();
                    h.assertTrue(Math.abs(normal.dot(f))<1E-8,"Crescent plane contains travel instead of facing it sideways");
                    h.assertTrue(q.endU()-q.startU()==.25f&&q.endV()-q.startV()==.5f,"Front calibration retains the local frame U axis");
                    faces++;crescents++;
                }
                // Reduced trails intentionally keep only the stream texture, without the decorative crescent.
                if(kind==FLIGHT||kind==CHARGE||kind==ARC||kind==TRAIL&&!reduced)
                    h.assertTrue(crescents>0,"Actual moving crescent composition exercised: kind="+kind+", reduced="+reduced);
            }
        }
        System.out.println("[MYRIAD-LINGYUAN-CRESCENT] PASS: "+faces+" actual sprite faces, 6 axes / 2 diagonals / zero fallback, near/far, three ages, convex front follows travel");
    }
    private static AbilityVfxPayload profile(int variant){
        int kind=switch(variant){case 0,1,3->SHIELD;case 2->ARC;case 4->FLIGHT;case 5,6->SEAL;default->CHARGE;};
        float radius=switch(variant){case 0->1.05f;case 1->.8f;case 2->2;case 3->.65f;case 4->.22f;case 5->4;case 6->10;default->1.2f;};
        float angle=switch(variant){case 0->4;case 1->3;case 2->80;case 5,6->360;case 7->300;default->0;};
        int life=switch(variant){case 0,1->14;case 2->12;case 3->8;case 4->3;case 5->80;case 6->120;default->5;};
        return new AbilityVfxPayload(1,kind==SHIELD?1:-1,5,variant,kind,Vec3.ZERO,new Vec3(0,0,variant==7?20:1.5),radius,angle,life,100,false);
    }
    private static void verifySkillProfiles(GameTestHelper h){
        var fingerprints=new HashSet<Integer>();
        for(int i=0;i<8;i++){
            var p=profile(i);
            for(float age:new float[]{.5f,p.life()*.45f,p.life()-.1f}){
                var full=AbilityVfxGeometry.build(p,age,false);var low=AbilityVfxGeometry.build(p,age,true);
                h.assertTrue(!full.isEmpty()&&full.size()<=4096&&low.size()<=full.size(),"Actual skill profile has bounded full/reduced detail: "+i);
                h.assertTrue(full.stream().anyMatch(q->q.material()>=AbilityVfxMaterials.LINGYUAN_PIXEL_SLASH),"Each skill combines pixel-painted transparent planes, not cube ornaments: "+i);
                for(var q:full)for(var v:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(AbilityTargeting.finite(v)&&v.length()<24,"Actual skill geometry is finite and stays local");
                if(age==.5f)fingerprints.add(full.hashCode());
            }
        }
        h.assertTrue(fingerprints.size()==8,"Eight branches have distinct authored compositions, not one shared effect");
        var ready=profile(1);var fullReady=AbilityVfxGeometry.build(ready,.5f,false);
        var two=new AbilityVfxPayload(1,1,5,1,SHIELD,ready.from(),ready.to(),.8f,2,14,100,false);
        h.assertTrue(fullReady.size()>AbilityVfxGeometry.build(two,.5f,false).size(),"Third resonance stack adds a distinct ready ring");
        for(int count=1;count<=4;count++){
            var p=new AbilityVfxPayload(1,1,5,0,SHIELD,Vec3.ZERO,new Vec3(0,0,1),1.05f,count,14,100,false);
            long faces=AbilityVfxGeometry.build(p,.5f,false).stream().filter(q->q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL).count();
            h.assertTrue(faces==count*SwordModelAsset.faceCount("body"),"Passive orbit renders exactly the real sword count");
        }
        var initial=new AbilityVfxPayload(1,-1,5,7,CHARGE,Vec3.ZERO,new Vec3(0,0,20),1.2f,0,5,100,false);
        var complete=new AbilityVfxPayload(1,-1,5,7,CHARGE,Vec3.ZERO,new Vec3(0,0,20),1.2f,350,5,103,false);
        double a=span(AbilityVfxGeometry.build(initial,.5f,false)),b=span(AbilityVfxGeometry.build(complete,.5f,false));
        h.assertTrue(b>a+.5,"Actual charge stage grows the blade across packet renewals");
        System.out.println("[MYRIAD-LINGYUAN-SKILLS] PASS: 8 distinct layered profiles, early/mid/late, real passive count, resonance-ready cue, cross-packet charge growth");
    }
    private static double span(List<Quad> mesh){
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(var q:mesh)if(q.material()==AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL)for(var v:List.of(q.a(),q.b(),q.c(),q.d())){min=Math.min(min,v.z);max=Math.max(max,v.z);}
        return max-min;
    }
    private static void exportPreview(){
        String file=System.getProperty("myriad.lingyuanPreview","");if(file.isBlank())return;
        var shots=new ArrayList<Map<String,Object>>();
        for(int variant=0;variant<8;variant++){
            var p=profile(variant);
            shots.add(Map.of("kind",p.kind(),"variant",variant,"label",List.of("Sword heart / real blade count","Resonance / three stacks ready","Cloud break / dash release","Sword mantle / active shield","Sword volley / flying blade","Sword array / active field","Thousand swords / active field","Heaven sword / charge").get(variant),
                    "age",.75f,"mesh",AbilityVfxGeometry.build(p,.75f,false)));
        }
        for(int kind:new int[]{FLIGHT,BEAM,SHIELD,SEAL,CHARGE,ARC,BURST}){
            var p=new AbilityVfxPayload(1,-1,5,kind==CHARGE?7:kind==SEAL?6:4,kind,Vec3.ZERO,new Vec3(0,.2,2),1.2f,100,8,100,false);
            shots.add(Map.of("kind",kind,"age",.75f,"mesh",AbilityVfxGeometry.build(p,.75f,false)));
        }
        try{var path=java.nio.file.Path.of(file);java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
            var animation=new ArrayList<Map<String,Object>>();
            for(int tick=0;tick<32;tick++){
                var field=new AbilityVfxPayload(1,-1,5,5,SEAL,Vec3.ZERO,new Vec3(0,0,1),4,360,80,100,false);
                var flight=new AbilityVfxPayload(1,-1,5,7,FLIGHT,Vec3.ZERO,new Vec3(0,.2,2),.25f,0,3,100+tick,false);
                var hit=new AbilityVfxPayload(1,-1,5,2,BURST,Vec3.ZERO,new Vec3(0,0,1),1,0,8,100+tick/16*16,false);
                animation.add(Map.of("sequence",0,"frame",tick,"kind",SEAL,"label","Sword array / flowing seal + filaments", "mesh",AbilityVfxGeometry.build(field,tick+.25f,false)));
                animation.add(Map.of("sequence",1,"frame",tick,"kind",FLIGHT,"label","Giant flying blade / renewed short packets", "mesh",AbilityVfxGeometry.build(flight,.25f,false)));
                animation.add(Map.of("sequence",2,"frame",tick,"kind",BURST,"label","Impact / one shot then eight-tick pause", "mesh",AbilityVfxGeometry.build(hit,tick%16+.25f,false)));
            }
            java.nio.file.Files.writeString(path.resolveSibling("runtime-animation.json"),new com.google.gson.Gson().toJson(animation));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
