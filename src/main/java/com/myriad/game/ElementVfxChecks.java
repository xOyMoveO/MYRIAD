package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Common-server geometry QA; deliberately does not assert in-game visual quality or frame rate. */
final class ElementVfxChecks {
    static void verify(GameTestHelper h){
        try(var stream=ElementVfxChecks.class.getResourceAsStream("/assets/myriad/particles/ability_mesh.json")){
            var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var textures=json.getAsJsonArray("textures");
            h.assertTrue(textures.size()==AbilityVfxMaterials.MAX+1,"SpriteSet count matches the stable material indices");
            for(int i=1;i<textures.size();i++){
                String id=textures.get(i).getAsString().split(":",2)[1];
                try(var png=ElementVfxChecks.class.getResourceAsStream("/assets/myriad/textures/particle/"+id+".png")){
                    h.assertTrue(png!=null,"Every custom sprite is packaged: "+id);
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        int count=0;
        for(int theme=1;theme<=4;theme++)for(int branch=0;branch<8;branch++)for(int life:new int[]{2,5,8,80}){
            var p=new AbilityVfxPayload(1,-1,theme,branch,FLIGHT,Vec3.ZERO,new Vec3(1.25,.2,0),.45f,0,life,100,false);
            final int firstMaterial=theme==1?AbilityVfxMaterials.THUNDER_PIXEL_SLASH:theme==2?AbilityVfxMaterials.FENJUE_PIXEL_SLASH:7+(theme-1)*4,
                    lastMaterial=theme==1?AbilityVfxMaterials.THUNDER_PIXEL_PILLAR:theme==2?AbilityVfxMaterials.FENJUE_PIXEL_EMBERS:firstMaterial+3;
            final boolean pixelThunder=theme==1,pixelFire=theme==2;
            for(float age:new float[]{0,.5f,life*.5f,life-.01f}){
                var mesh=AbilityVfxGeometry.build(p,age,false);
                int budget=com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.usesProjectileModel(p)
                        ?com.myriad.character.skills.tianxing.rework.ThunderVfxGeometry.budget(p,false):192;
                h.assertTrue(!mesh.isEmpty()&&mesh.size()<=budget,"Real-life projectile stages remain finite");
                h.assertTrue(mesh.stream().anyMatch(q->(pixelThunder?q.material()==AbilityVfxMaterials.THUNDER_PIXEL_BOLT:pixelFire?q.material()==AbilityVfxMaterials.FENJUE_PIXEL_STREAM:q.material()==0)
                                &&q.alpha()>.8*Math.min(1,(life-age)/Math.max(1,life*.25))),
                        "Projectile keeps an opaque-core pixel ribbon or solid bevel, not only translucent wisps");
                h.assertTrue(mesh.stream().anyMatch(q->q.material()>=firstMaterial&&q.material()<=lastMaterial),
                        "Every role uses its own sprite family");
                count++;
            }
        }
        for(int theme=1;theme<=4;theme++){
            var p=new AbilityVfxPayload(1,-1,theme,6,SEAL,Vec3.ZERO,new Vec3(1,0,0),5,359,5,100,false);
            var warning=AbilityVfxGeometry.build(p,1,false);
            double maxY=warning.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).mapToDouble(v->v.y).max().orElse(0);
            h.assertTrue(maxY<.2,"Ground warning does not pretend that an active pillar/flame field already exists");
        }
        exportPreview();
        System.out.println("[MYRIAD-ELEMENT-VFX] PASS: "+count+" solid projectile stages, own material families, warning/active separation, atlas resources");
    }
    private static void exportPreview(){
        String file=System.getProperty("myriad.elementPreview","");if(file.isBlank())return;
        var shots=new ArrayList<Map<String,Object>>();
        String[] roles={"Thunder","Flame","Ink","Wind"};
        for(int theme=1;theme<=4;theme++){
            int[] kinds={FLIGHT,ARC,BURST,SEAL,SHIELD,CHARGE,TRAIL,theme==4?WALL:PILLAR};
            int[] variants={theme==4?5:theme==3?2:3,2,6,theme==3?6:theme==4?7:6,theme==1?4:theme==2?7:3,theme==1?7:6,theme==2?3:2,theme==1?6:4};
            for(int index=0;index<kinds.length;index++){
                Vec3 from=Vec3.ZERO,to=kinds[index]==PILLAR?new Vec3(0,6,0)
                        :kinds[index]==TRAIL?new Vec3(5,0,0):kinds[index]==CHARGE?new Vec3(8,0,0):new Vec3(1.5,0,0);
                double radius=switch(kinds[index]){
                    case SEAL->theme==3?6:5;
                    case WALL->3;
                    case BURST->theme==2?5:theme==3?.75:2.5;
                    case ARC->theme==2?4:1.5;
                    case TRAIL->.09;
                    case CHARGE->theme==1?1.25:.35;
                    case FLIGHT->theme==4?.45:.22;
                    default->.65;
                };
                int life=kinds[index]==FLIGHT?3:kinds[index]==SEAL?80:12;float age=kinds[index]==FLIGHT?.8f:3;
                var p=new AbilityVfxPayload(1,-1,theme,variants[index],kinds[index],from,to,(float)radius,105,life,100,false);
                shots.add(Map.of("label",roles[theme-1]+" / "+new String[]{"Projectile","Cut","Impact","Active field","Guard","Windup","Trail","Wall / Pillar"}[index],
                        "theme",theme,"kind",kinds[index],"variant",variants[index],"age",age,"mesh",AbilityVfxGeometry.build(p,age,false)));
            }
        }
        try{var path=java.nio.file.Path.of(file);java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path,new com.google.gson.Gson().toJson(shots));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
