package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Authored translucent moving ground fire and vertical tongues; no solid colour fissure strips. */
public final class FlameMajorVfx {
    private static final Vec3 UP=new Vec3(0,1,0);
    private final AbilityVfxPayload p;private final double age,time,t,fade;private final boolean low;
    private final List<Quad> out=new ArrayList<>();
    private FlameMajorVfx(AbilityVfxPayload p,double age,boolean low){this.p=p;this.age=age;this.low=low;
        t=Math.clamp(age/p.life(),0,1);time=Math.floorMod(p.started(),200)+age;
        fade=Math.min(1,(age+1)/2)*Math.min(1,(p.life()-age)/Math.min(8,p.life()*.35));}
    public static boolean owns(AbilityVfxPayload p){return p.theme()==2&&p.variant()>=4&&p.variant()<=7
            &&(p.kind()==SEAL||p.kind()==BURST||p.kind()==CHARGE||p.kind()==SHIELD||p.kind()==TRAIL);}
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!owns(p)||!p.valid()||age<0||age>=p.life())return List.of();var g=new FlameMajorVfx(p,age,low);g.draw();return List.copyOf(g.out);}
    public static int sceneLight(int light,boolean ground){
        // These are emitting flame/lava, not ordinary terrain lit by the sky.
        return 0xF000F0;
    }
    private void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,double opacity,int material,float u0,float u1,float v0,float v1){
        float alpha=(float)Math.clamp(opacity*fade,0,1);if(alpha<=0||out.size()>=(low?80:192))return;
        out.add(new Quad(a,b,c,d,color,alpha,material,false,u0,u1,v0,v1));
    }
    public static void fire(List<Quad> out,Vec3 feet,Vec3 across,double w,double h,double time,double opacity,int budget){
        // One authored silhouette per plane: alpha-crossfading two different curls creates double-image ghosts.
        int frame=Math.floorMod((int)Math.floor(time*.34),4);
        Vec3 r=across.normalize().scale(w),u=UP.scale(h*.5),at=feet.add(0,h*.5,0);
        {
            int index=frame;float u0=(index%2)*.5f+.5f/1312,u1=u0+.5f-1f/1312;
            float v0=(index/2)*.5f+.5f/1312,v1=v0+.5f-1f/1312;
            float alpha=(float)Math.clamp(opacity*1.22,0,1);if(alpha<=.002||out.size()+2>budget)return;
            var a=at.subtract(r).subtract(u);var b=at.add(r).subtract(u);var c=at.add(r).add(u);var d=at.subtract(r).add(u);
            out.add(new Quad(a,b,c,d,0xFFFFFF,alpha,FENJUE_HD_FIRE,false,u0,u1,v0,v1));
            out.add(new Quad(d,c,b,a,0xFFFFFF,alpha,FENJUE_HD_FIRE,true,u0,u1,v0,v1));
        }
    }
    private void flame(Vec3 feet,double size,int index,double opacity){
        double a=index*2.399;Vec3 across=new Vec3(Math.cos(a),0,Math.sin(a));
        fire(out,feet,across,size*.58,size*1.55,time+index*5,opacity*fade,low?80:192);
        if(!low)fire(out,feet.add(0,.01,0),across.cross(UP),size*.44,size*1.44,time+index*5+1,opacity*.28*fade,192);
    }
    private Vec3 at(double r,double a){return p.from().add(Math.cos(a)*r,0,Math.sin(a)*r);}
    private void line(Vec3 a,Vec3 b,double width,double alpha,int color,boolean ground){
        var side=b.subtract(a).cross(UP);if(side.lengthSqr()<1E-8)return;side=side.normalize().scale(width*.5);
        // Sample the white centre of vanilla glow, not its tiny plus-shaped silhouette stretched across a long fissure.
        quad(a.subtract(side),a.add(side),b.add(side),b.subtract(side),color,alpha,ground?FENJUE_GROUND:0,.375f,.625f,.375f,.625f);
    }
    private void rim(double radius,double alpha){
        int segments=low?16:32;
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments,b=(i+.76)*Math.PI*2/segments;
            line(at(radius,a),at(radius,b),.065,Math.min(.95,alpha*1.55),i%4==0?0xFFF0AD:0xFFB64E,true);
        }
    }
    private void flow(double radius,double alpha,boolean ring){
        FlameGroundFlipbook.add(out,p.from(),radius,time,(float)(alpha*fade),ring?FENJUE_FLOW_RING:FENJUE_FLOW_FIELD,low,low?80:192);
    }
    private void draw(){
        double r=Math.max(.1,p.radius());boolean warning=p.kind()==SEAL&&p.angle()==359;
        if(p.personal()){rim(r,.66);return;}
        if(warning){flow(r,.44,true);return;}
        if(p.kind()==TRAIL){flame(p.from(),.36,0,.68);return;}
        if(p.kind()==CHARGE){
            flow(Math.min(r,1.5),.46,false);
            for(int i=0;i<(low?3:5);i++){double a=i*2.399+time*.015;flame(at(.55,a),.38,i,.62);}return;
        }
        if(p.kind()==SHIELD){
            flow(.9,.48,true);
            for(int i=0;i<(low?3:5);i++){double a=i*Math.PI*2/(low?3:5)+time*.025;
                flame(at(.5,a).add(0,.25+.15*Math.sin(time*.12+i),0),.82,i,.76);}return;
        }
        if(p.kind()==BURST){
            double reach=r*(.20+.80*FlameSkillEase.out(t));
            flow(reach,.84*(1-t*.55),true);
            flow(reach*.70,.50*(1-t*.75),false);
            for(int i=0;i<(low?4:8);i++)flame(at(reach*.70,i*2.399),Math.min(1.6,.45+r*.16)*(1-t*.45),i,.86*(1-t*.65));
            return;
        }
        // A small transient seal complements impact; the persistent cage/sea owns the full area.
        if(p.life()<20){flow(Math.min(r,1.8),.25,false);return;}
        boolean cage=p.variant()==4;
        flow(cage?r:r*.96,cage?.70:.52,cage);
        int count=low?6:12;
        for(int i=0;i<count;i++){
            double a=i*2.399,radius=cage?r*.94:r*Math.sqrt((i+.65)/count)*.90;
            double pulse=.88+.12*Math.sin(time*.15+i*1.7);
            flame(at(radius,a),cage?(1.65+.28*(i%3))*pulse:(.85+.25*(i%4))*pulse,i,cage?.88:.84);
        }
    }
    private static final class FlameSkillEase {static double out(double t){return 1-Math.pow(1-t,3);}}
}
