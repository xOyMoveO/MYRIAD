package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.tianxing.rework.*;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import javax.imageio.ImageIO;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Packaged art, real layered geometry/UVs, phase, bounds and no stationary flight body. */
final class ThunderPixelVfxChecks {
    static void verify(GameTestHelper h){
        int frames=0;
        try{
            for(String name:new String[]{"slash","bolt","impact","seal","corona","pillar"}){
                try(var in=ThunderPixelVfxChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/thunder_pixel_"+name+".png")){
                    h.assertTrue(in!=null,"New thunder atlas is packaged: "+name);var image=ImageIO.read(in);
                    int cell=ThunderFlipbook.CELL,pad=ThunderFlipbook.PADDING;
                    h.assertTrue(cell==256&&image.getWidth()==ThunderFlipbook.WIDTH&&image.getHeight()==ThunderFlipbook.HEIGHT&&image.getColorModel().hasAlpha(),"Native HD 4x2 RGBA sprite atlas");
                    var hashes=new HashSet<Integer>();var colors=new HashSet<Integer>();int peakSolid=0;
                    for(int frame=0;frame<8;frame++){
                        int hash=1,count=0,solid=0;
                        for(int y=0;y<cell;y++)for(int x=0;x<cell;x++){
                            int pixel=image.getRGB(frame%4*cell+x,frame/4*cell+y),a=pixel>>>24;hash=hash*31+pixel;
                            if(a==0)continue;count++;if(a>=224)solid++;
                            h.assertTrue(x>=pad&&x<cell-pad&&y>=pad&&y<cell-pad,"All cell gutters stay transparent");
                            int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
                            h.assertTrue(r<=g&&g<=b&&b>=160,"White/cyan/blue palette, no purple/red/gold/black plate");colors.add(pixel&0xFFFFFF);
                        }
                        h.assertTrue(count>0&&count<(cell-2*pad)*(cell-2*pad),"Nonempty authored silhouette and transparent background: "+name+" frame="+frame);
                        peakSolid=Math.max(peakSolid,solid);
                        h.assertTrue(hashes.add(hash),"Eight genuinely different animation frames");frames++;
                    }
                    h.assertTrue(colors.size()<=14,"One shared pixel palette");
                    h.assertTrue(peakSolid>20,"Each family has an opaque bright peak, with fading allowed during dissipation: "+name);
                }
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        int recipes=0;
        for(int variant=0;variant<8;variant++)for(int kind:new int[]{SEAL,BURST,ARC,BEAM,PILLAR,SHIELD,TRAIL,FLIGHT,CHARGE,WALL})
            for(Vec3 direction:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.6,-.4,.8)))
                for(float age:new float[]{0,.5f,1.5f,3,7,15.9f}){
                    var p=new AbilityVfxPayload(1,-1,1,variant,kind,Vec3.ZERO,direction.scale(2),.8f,120,16,100,false);
                    var high=AbilityVfxGeometry.build(p,age,false);var low=AbilityVfxGeometry.build(p,age,true);
                    h.assertTrue(!high.isEmpty()&&high.size()<=ThunderVfxGeometry.budget(p,false)&&low.size()<=ThunderVfxGeometry.budget(p,true)&&low.size()<=high.size(),"Finite layered/model LOD budgets");
                    for(var q:high){
                        h.assertTrue(q.material()>=THUNDER_PIXEL_SLASH&&q.material()<=THUNDER_PROJECTILE_MODEL
                                &&(q.material()!=THUNDER_PROJECTILE_MODEL||ThunderVfxGeometry.usesProjectileModel(p)),"Authored HD layers; only two actual projectiles use their approved models");
                        h.assertTrue(q.alpha()>0&&q.alpha()<=1&&q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1
                                &&q.startU()<q.endU()&&q.startV()<q.endV(),"Safe opacity and frame UV range");
                        for(var point:List.of(q.a(),q.b(),q.c(),q.d()))h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)
                                &&ThunderVfxGeometry.bounds(p).inflate(.001).contains(point),"All real vertices fit client culling bounds");
                    }
                    h.assertTrue(AbilityVfxGeometry.build(p,16,false).isEmpty(),"No layer outlives its recipe");recipes++;
                }
        for(boolean slow:new boolean[]{false,true}){
            var seen=new HashSet<Integer>();for(int age=0;age<32;age++)seen.add(ThunderFlipbook.loop(100,age,0,slow).frame());
            h.assertTrue(seen.size()==8,"All looping frames are visited");
            h.assertTrue(ThunderFlipbook.loop(100,4.5,2,slow).equals(ThunderFlipbook.loop(101,3.5,2,slow)),"Packet renewal does not restart animation");
        }
        for(int i=0;i<8;i++){
            var uv=ThunderFlipbook.cell(i);
            h.assertTrue(Math.abs(uv.u0()*ThunderFlipbook.WIDTH-i%4*ThunderFlipbook.CELL-.5)<.001
                    &&Math.abs(uv.u1()*ThunderFlipbook.WIDTH-(i%4+1)*ThunderFlipbook.CELL+.5)<.001
                    &&Math.abs(uv.v0()*ThunderFlipbook.HEIGHT-i/4*ThunderFlipbook.CELL-.5)<.001,
                    "HD atlas samples the actual 256px cell with half-texel safe borders");
        }
        h.assertTrue(6L*ThunderFlipbook.WIDTH*ThunderFlipbook.HEIGHT*4<=12*1024*1024,"Six HD atlases stay within 12 MiB raw RGBA budget");
        h.assertTrue(ThunderFlipbook.once(0,0).frame()==0&&ThunderFlipbook.once(1,0).frame()==7,"One-shot discharge dissipates, never loops");
        for(Vec3 dir:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(-.6,.4,.8).normalize())){
            var p=new AbilityVfxPayload(1,-1,1,3,FLIGHT,Vec3.ZERO,dir.scale(1.25),.22f,0,3,100,false,7);
            var fresh=AbilityVfxGeometry.build(p,.6f,false);
            h.assertTrue(fresh.stream().anyMatch(q->q.material()==THUNDER_PROJECTILE_MODEL),"Approved electric arc model exists during travel");
            h.assertTrue(AbilityVfxGeometry.build(p,1.2f,false).stream().noneMatch(q->q.material()==THUNDER_PROJECTILE_MODEL||q.material()==THUNDER_PIXEL_IMPACT),"Old packet keeps only wake, no parked body");
        }
        var warning=new AbilityVfxPayload(1,-1,1,6,SEAL,Vec3.ZERO,new Vec3(1,0,0),5,359,8,100,false);
        h.assertTrue(AbilityVfxGeometry.build(warning,2,false).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                .allMatch(v->Math.abs(v.y)<.04),"Warning is ground-only, not premature lightning");
        h.assertTrue(ThunderVfxGeometry.ownerOpacity(SHIELD)<.3&&ThunderVfxGeometry.ownerOpacity(CHARGE)<.4
                &&ThunderVfxGeometry.ownerOpacity(FLIGHT)==1,"Only own first-person guard/charge are subdued");
        System.out.println("[MYRIAD-THUNDER-PIXEL-VFX] PASS: "+frames+" native 256px frames, HD atlas/half-texel UV, 12MiB budget, palette/core/gutters; "+recipes+" mesh stages, LOD, culling, phase, warning, forward crescent and no stale flight body");
    }
}
