package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.skills.yingxiao.rework.*;
import com.myriad.network.AbilityVfxPayload;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Real sprite/geometry contract; intentionally not a claim of client visual/FPS acceptance. */
final class NightGrayVfxChecks {
    static void verify(GameTestHelper h) {
        String[] names={"blade","slash","wake","smoke","seal","impact"};
        int frames=0;
        try {
            for(String name:names)try(var in=NightGrayVfxChecks.class.getResourceAsStream(
                    "/assets/myriad/textures/particle/vfx/night_gray_"+name+".png")) {
                h.assertTrue(in!=null,"New shadow sprite must be packaged: "+name);
                var image=ImageIO.read(in);
                h.assertTrue(image.getWidth()==512&&image.getHeight()==256&&image.getColorModel().hasAlpha(),"4x2 RGBA atlas");
                var hashes=new HashSet<Integer>();var colors=new HashSet<Integer>();
                for(int frame=0;frame<8;frame++) {
                    int count=0,solid=0,hash=1;
                    for(int y=0;y<128;y++)for(int x=0;x<128;x++) {
                        int pixel=image.getRGB(frame%4*128+x,frame/4*128+y),a=pixel>>>24;
                        hash=hash*31+pixel;if(a==0)continue;count++;
                        h.assertTrue(x>=12&&x<116&&y>=12&&y<116,"Safe transparent cell gutters");
                        int r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
                        h.assertTrue(r==g&&g==b,"Every visible pixel is neutral gray, not purple/blue");
                        colors.add(pixel&0xFFFFFF);if(a>=224)solid++;
                    }
                    h.assertTrue(count>0&&count<11000,"Distinct effect shape and transparent background");
                    if(!name.equals("impact"))h.assertTrue(solid>20,"Shadow core is substantial, not uniformly faint");
                    h.assertTrue(hashes.add(hash),"All eight frames have real painted changes");frames++;
                }
                h.assertTrue(colors.size()<=12,"Shared limited grayscale pixel palette");
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        for(int material=NIGHT_GRAY_BLADE;material<=NIGHT_GRAY_IMPACT;material++) {
            var seen=new HashSet<Integer>();
            for(int t=0;t<24;t++)seen.add(NightFlipbook.loop(material,100,t,0).frame());
            h.assertTrue(seen.size()==8,"Every looping layer visits its frames");
            h.assertTrue(NightFlipbook.loop(material,100,4.5,3).equals(NightFlipbook.loop(material,101,3.5,3)),
                    "Short packet renewals share a stable animation phase");
        }
        h.assertTrue(NightFlipbook.once(0).frame()==0&&NightFlipbook.once(1).frame()==7,"One-shot impact does not loop after decay");
        int count=0;
        for(int variant=0;variant<8;variant++)for(int kind:new int[]{SEAL,BURST,ARC,BEAM,PILLAR,SHIELD,TRAIL,FLIGHT,CHARGE,WALL})
            for(Vec3 direction:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.6,-.4,.8)))
                for(float age:new float[]{0,.5f,1.5f,3,7,15.9f}) {
                    var p=new AbilityVfxPayload(1,-1,0,variant,kind,Vec3.ZERO,direction.scale(2),.8f,120,16,100,false);
                    var high=AbilityVfxGeometry.build(p,age,false);var low=AbilityVfxGeometry.build(p,age,true);
                    h.assertTrue(!high.isEmpty()&&high.size()<=192&&low.size()<=high.size()&&low.size()<=80,"Layer budgets/LOD");
                    for(var q:high) {
                        h.assertTrue(q.material()>=NIGHT_GRAY_BLADE&&q.material()<=NIGHT_NEEDLE_MODEL,"No old purple sprite/solid cube fallback");
                        h.assertTrue(q.alpha()>0&&q.alpha()<=1,"Bounded opacity");
                        h.assertTrue(q.startU()>=0&&q.endU()<=1&&q.startV()>=0&&q.endV()<=1
                                &&q.startU()<q.endU()&&q.startV()<q.endV(),"Cell UV bounds");
                        int color=q.color();h.assertTrue(((color>>16)&255)==((color>>8)&255)&&((color>>8)&255)==(color&255),"Neutral mesh tint");
                        for(var point:List.of(q.a(),q.b(),q.c(),q.d()))
                            h.assertTrue(Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z)
                                    &&NightVfxGeometry.bounds(p).inflate(.001).contains(point),"Finite mesh inside actual culling bounds");
                    }
                    h.assertTrue(AbilityVfxGeometry.build(p,16,false).isEmpty(),"All layers disappear at expiry");count++;
                }
        for(Vec3 f:List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(-.6,.4,.8).normalize())) {
            var p=new AbilityVfxPayload(1,-1,0,2,FLIGHT,Vec3.ZERO,f.scale(1.5),.22f,0,2,100,false,7);
            var fresh=AbilityVfxGeometry.build(p,.6f,false);
            var model=fresh.stream().filter(q->q.material()==NIGHT_NEEDLE_MODEL).toList();
            h.assertTrue(model.size()==NightNeedleModel.faces(false),"Full flying needle replaces the flat blade");
            double front=model.stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                    .mapToDouble(v->v.subtract(f.scale(.9)).dot(f)).max().orElseThrow();
            h.assertTrue(Math.abs(front)<1E-5,"Model's narrow +Y tip leads the real 3D travel");
            h.assertTrue(AbilityVfxGeometry.build(p,1.2f,false).stream().noneMatch(q->q.material()==NIGHT_GRAY_BLADE||q.material()==NIGHT_NEEDLE_MODEL),"Old flight frame cannot leave a stationary knife/needle");
        }
        var warning=new AbilityVfxPayload(1,-1,0,6,SEAL,Vec3.ZERO,new Vec3(1,0,0),2,359,8,100,false);
        h.assertTrue(AbilityVfxGeometry.build(warning,2,false).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d()))
                .allMatch(v->Math.abs(v.y)<.01),"Ground warning never renders active execution blades");
        var arc=new AbilityVfxPayload(1,-1,0,0,ARC,Vec3.ZERO,new Vec3(1,0,0),.8f,95,8,100,false);
        var planes=AbilityVfxGeometry.build(arc,2,false).stream().filter(q->q.material()==NIGHT_GRAY_SLASH&&!q.reverseV()).toList();
        var normal=planes.get(0).b().subtract(planes.get(0).a()).cross(planes.get(0).d().subtract(planes.get(0).a())).normalize();
        h.assertTrue(planes.stream().skip(1).anyMatch(q->Math.abs(normal.dot(q.b().subtract(q.a()).cross(q.d().subtract(q.a())).normalize()))<.1),
                "Crossed slash planes remain substantial when viewed from the side");
        System.out.println("[MYRIAD-NIGHT-GRAY-VFX] PASS: "+frames+" unique RGBA frames, neutral palette, solid cores/gutters; "+count+" mesh stages, phase, LOD, culling, travel and no stale blade");
    }
}
