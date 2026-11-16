package com.myriad.game;

import com.google.gson.JsonParser;
import com.myriad.character.ability.*;
import com.myriad.character.skills.tianxing.rework.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

final class ThunderProjectileChecks {
    static void verify(GameTestHelper h){
        h.assertTrue(ThunderArcSkill.SPEED*20==50&&ThunderArcSkill.RANGE==18,"Arc: exactly 50 blocks/s; range unchanged");
        h.assertTrue(ThunderSlashSkill.SPEED*20==80&&ThunderSlashSkill.RANGE==20&&ThunderSlashSkill.RADIUS==1,"Slash: exactly 80 blocks/s, 20 blocks, hit radius unchanged");
        try{
            for(String lang:List.of("zh_cn","en_us")){
                try(var in=ThunderProjectileChecks.class.getResourceAsStream("/assets/myriad/lang/"+lang+".json")){
                    var obj=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
                    h.assertTrue(obj.get("skill.myriad.g1_tianxing_mb").getAsString().equals(lang.equals("zh_cn")?"斩雷":"Thunder Slash"),"Actual charging HUD translation no longer uses the retired name");
                }
            }
            try(var in=ThunderProjectileChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/thunder_projectile_models.png")){
                var image=ImageIO.read(in);h.assertTrue(image.getWidth()==128&&image.getHeight()==128,"Shared bounded model atlas");
                for(int region=0;region<8;region++)for(int y=0;y<128;y++)for(int x=region*16;x<(region+1)*16;x++)
                    h.assertTrue(image.getRGB(x,y)==image.getRGB(region*16,0),"Clean color regions: no random material noise");
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        int cases=0;
        for(int variant:new int[]{3,5})for(boolean low:new boolean[]{false,true})for(Vec3 direction:List.of(
                new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,1),new Vec3(.3,-.7,.6).normalize())){
            var p=new AbilityVfxPayload(1,-1,1,variant,FLIGHT,new Vec3(10,64,10),new Vec3(10,64,10).add(direction.scale(1.5)),.22f,0,3,100,false,7);
            var mesh=AbilityVfxGeometry.build(p,.5f,low);var body=mesh.stream().filter(q->q.material()==THUNDER_PROJECTILE_MODEL).toList();
            h.assertTrue(body.size()==ThunderProjectileModels.faces(variant==5,low),"Whole model, not a partially clipped frame-budget batch");
            h.assertTrue(mesh.stream().filter(q->q.material()==THUNDER_PIXEL_BOLT).count()>=(low?8:16),"HD crossed electric planes decorate both travelling models");
            h.assertTrue(mesh.size()<=ThunderVfxGeometry.budget(p,low),"Scoped model budget and finite detail fallback");
            Vec3 tip=p.from().lerp(p.to(),.5);double front=-1E9,back=1E9,width=0;
            for(var q:body){
                h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1&&q.startU()<q.endU()&&q.startV()<q.endV(),"Valid atlas UV");
                h.assertTrue(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).lengthSqr()>1E-12,"Positive-area 3D faces");
                for(var at:List.of(q.a(),q.b(),q.c(),q.d())){
                    h.assertTrue(ThunderVfxGeometry.bounds(p).inflate(.001).contains(at),"Real rotated vertices inside particle culling bounds");
                    var local=at.subtract(tip);double longitudinal=local.dot(direction);front=Math.max(front,longitudinal);back=Math.min(back,longitudinal);
                    width=Math.max(width,local.subtract(direction.scale(longitudinal)).length());
                }
            }
            h.assertTrue(Math.abs(front)<1E-5&&back<-.7&&back>=-2.5&&width>.15&&width<1.3,"Actual forward edge leads in every 3D direction, with stable scale");
            for(float age:new float[]{1,1.5f,2.5f,3})h.assertTrue(AbilityVfxGeometry.build(p,age,low).stream().noneMatch(q->q.material()==THUNDER_PROJECTILE_MODEL),"Never leave a parked solid model after one movement tick");
            h.assertTrue(ThunderProjectileModels.parts(variant==5,low)>12,"Authored multi-part shape, not a single cuboid");cases++;
        }
        h.assertTrue(com.myriad.character.rework.FirstGroupSkills.node("tianxing",5).displayName().equals("斩雷"),"Branch metadata and HUD name agree");
        System.out.println("[MYRIAD-THUNDER-PROJECTILES] PASS: "+cases+" real full/LOD meshes, +X-to-3D tip, exact source face counts, UV/bounds/area, clean atlas, stale/expiry, actual zh/en charge names");
    }
}
