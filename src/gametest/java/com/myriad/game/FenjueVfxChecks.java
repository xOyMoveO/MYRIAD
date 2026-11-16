package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.fenjue.rework.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import javax.imageio.ImageIO;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

final class FenjueVfxChecks {
    static void verify(GameTestHelper h){
        h.assertTrue(FlameStepSkill.DISTANCE==8&&FlameBurstSkill.RADIUS==6,"Requested 8-block dash / 6-block burst");
        int frames=0,recipes=0;
        try{
            var palette=new HashSet<Integer>();
            for(String name:new String[]{"slash","flame","burst","ring","stream","embers"}){
                try(var in=FenjueVfxChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/fenjue_pixel_"+name+".png")){
                    h.assertTrue(in!=null,"Packaged flame atlas: "+name);var image=ImageIO.read(in);
                    h.assertTrue(image.getWidth()==1024&&image.getHeight()==512&&image.getColorModel().hasAlpha(),"Four by two RGBA frame atlas");
                    var hashes=new HashSet<Integer>();int solid=0;
                    for(int frame=0;frame<8;frame++){
                        int hash=1,visible=0;
                        for(int y=0;y<256;y++)for(int x=0;x<256;x++){
                            int pixel=image.getRGB(frame%4*256+x,frame/4*256+y),alpha=pixel>>>24;hash=31*hash+pixel;
                            if(alpha==0)continue;visible++;if(alpha>224)solid++;
                            h.assertTrue(x>=24&&x<232&&y>=24&&y<232,"No frame-edge clipping or atlas bleed");palette.add(pixel&0xFFFFFF);
                        }
                        h.assertTrue(visible>20&&hashes.add(hash),"Eight distinct nonempty flame phases: "+name);frames++;
                    }
                    h.assertTrue(solid>100,"Substantial bright body, not only transparent mist");
                }
            }
            h.assertTrue(palette.size()<=64,"One coherent fire/ember palette across the set");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        for(int variant=0;variant<8;variant++)for(int kind:new int[]{SEAL,BURST,ARC,BEAM,PILLAR,SHIELD,TRAIL,FLIGHT,CHARGE,WALL})
            for(Vec3 direction:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.6,-.4,.8)))
                for(float age:new float[]{0,.5f,2,7,15.9f}){
                    var p=new AbilityVfxPayload(1,-1,2,variant,kind,Vec3.ZERO,direction.scale(2),.8f,100,16,100,false);
                    var high=AbilityVfxGeometry.build(p,age,false);var low=AbilityVfxGeometry.build(p,age,true);
                    boolean swept=FlameCrossSlashVfx.owns(p),empty=swept&&(age==0||age>=p.life()*13.6/14);
                    h.assertTrue((empty?high.isEmpty()&&low.isEmpty():!high.isEmpty())&&high.size()<=192&&low.size()<=80&&low.size()<=high.size(),"Bounded full/reduced geometry; swept stroke has finite zero-area birth and eroded end");
                    for(var q:high){
                        h.assertTrue(q.material()>=FENJUE_PIXEL_SLASH&&q.material()<=FENJUE_PIXEL_EMBERS||swept&&q.material()==FENJUE_CRISP_SLASH
                                ||FlameMajorVfx.owns(p)&&(q.material()==FENJUE_HD_FIRE||flameGround(q.material())),"Explicit skill-owned fire family, including the two animated ground sheets, never an old cube-flame model");
                        h.assertTrue(q.alpha()>0&&q.alpha()<=1&&q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1,"Safe alpha/UVs");
                        for(var point:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)
                                &&FlameVfxGeometry.bounds(p).inflate(.001).contains(point),"Actual layered vertices fit role-specific culling bounds");
                    }
                    h.assertTrue(AbilityVfxGeometry.build(p,16,false).isEmpty(),"No fire layer outlives the recipe");recipes++;
                }
        h.assertTrue(FlameFlipbook.loop(100,4.5,2).equals(FlameFlipbook.loop(101,3.5,2)),"Renewed field samples do not restart the flame animation");
        h.assertTrue(FlameFlipbook.once(0,0).frame()==0&&FlameFlipbook.once(1,0).frame()==7,"Explosion progresses once, does not restart its contact flash");
        for(int variant:new int[]{4,5,6}){
            var warning=new AbilityVfxPayload(1,-1,2,variant,SEAL,Vec3.ZERO,new Vec3(1,0,0),6,359,8,100,false);
            h.assertTrue(AbilityVfxGeometry.build(warning,2,false).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                    .allMatch(point->Math.abs(point.y)<.04),"Warning remains on the ground, not premature active flames");
        }
        h.assertTrue(FlameVfxGeometry.ownerOpacity(SHIELD)<.3&&FlameVfxGeometry.ownerOpacity(CHARGE)<.4&&FlameVfxGeometry.ownerOpacity(ARC)==1,"Own guard/charge preserve first-person sight");
        System.out.println("[MYRIAD-FENJUE-VFX] PASS: "+frames+" distinct packed frames, shared palette/alpha/gutters, "+recipes+" full/LOD/3D recipes, bounds/TTL/UV, phase and warning separation");
    }
}
