package com.myriad.character.skills.shuo;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxGeometry.BEAM;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Silver seam -> two shearing strokes -> seeded travelling blade fragments. */
public final class ShuoSilentLineVfx {
    public static final int SEAM=317,CUT=318,MAX_QUADS=160;
    private ShuoSilentLineVfx(){}
    public static boolean owns(AbilityVfxPayload p){return p.valid()&&p.theme()==10&&p.variant()==6&&p.kind()==BEAM&&(p.angle()==SEAM||p.angle()==CUT);}
    public static boolean replaces(AbilityVfxPayload incoming,AbilityVfxPayload old){
        return owns(incoming)&&owns(old)&&incoming.angle()==SEAM&&old.angle()==SEAM&&incoming.instance()>0
                &&incoming.owner()==old.owner()&&incoming.instance()==old.instance();
    }
    public static List<Quad> build(AbilityVfxPayload p,float age,boolean low){
        if(!owns(p)||!Float.isFinite(age)||age<0||age>=p.life())return List.of();
        var delta=p.to().subtract(p.from());double length=delta.length();if(length<.02)return List.of();
        var along=delta.normalize();var side=along.cross(new Vec3(0,1,0));
        if(side.lengthSqr()<1E-8)side=new Vec3(1,0,0);else side=side.normalize();
        var up=side.cross(along).normalize();var mesh=new Mesh(p.from(),along,length,low?96:MAX_QUADS);
        float t=age/p.life();
        if(p.angle()==SEAM){
            float fade=Math.min(1,(p.life()-age)/1.25f);
            mesh.stroke(p.from(),p.to(),up,.065,SHUO_STREAM,0x263542,fade*.35f);
            mesh.stroke(p.from(),p.to(),up,.035,SHUO_STREAM,0xDDF4FF,fade*.92f);
            mesh.stroke(p.from(),p.to(),side,.018,SHUO_STREAM,0xACD8FF,fade*.78f);
            mesh.stroke(p.from(),p.to(),up,.018,0,0xF5FCFF,fade*.92f);
            if(!low){
                double phase=((p.started()+age)*.14)%1;
                var center=p.from().add(delta.scale(.1+.8*phase));
                mesh.stroke(center.subtract(along.scale(Math.min(.18,length*.1))),center.add(along.scale(Math.min(.18,length*.1))),up,.014,SHUO_STREAM,0xFFFFFF,fade);
            }
        }else{
            float fade=(float)(Math.min(1,(age+.7)/1.5)*Math.pow(1-t,1.15));
            double head=Math.min(1,(t+.04)/.30),tail=Math.max(0,(t-.38)/.62);
            // Both diagonal cuts share the actual path but cross in opposite transverse directions.
            for(int sign:new int[]{-1,1}){
                var offset=side.scale(sign*.60).add(up.scale(sign*.42));
                Vec3 a=p.from().add(offset),b=p.to().subtract(offset);
                Vec3 start=a.lerp(b,tail),end=a.lerp(b,head);
                mesh.stroke(start,end,up,.20*(1-t*.6),SHUO_STREAM,0xFFFFFF,fade);
                mesh.stroke(start,end,side,.10*(1-t*.5),SHUO_STREAM,0xADDFFF,fade*.82f);
                mesh.stroke(start,end,up,.025*(1-t*.5),0,0xF6FCFF,fade);
            }
            var random=new SplittableRandom(((long)p.owner()<<32)^p.started()^((long)p.instance()*0x9E3779B97F4A7C15L));
            for(int i=0;i<(low?1:3);i++){
                double center=.22+random.nextDouble()*.56,angle=random.nextDouble()*Math.PI*2,sign=random.nextBoolean()?1:-1;
                double born=.08+i*.085+random.nextDouble()*.025;
                float phase=(float)((t-born)/.38);if(phase<=0||phase>=1)continue;
                var cross=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                var at=p.from().add(delta.scale(center)).add(cross.scale((phase-.5)*1.4));
                var right=along.scale(.78).add(cross.scale(sign*.62));var vertical=up.add(side.scale(sign*.34));
                // Small painted slash sheets travel independently; no frame-random flicker.
                for(var q:ShuoSlashMesh.build(at,right,vertical,Math.min(1.6,length*.22),.55,phase,true))mesh.prepared(q);
            }
        }
        return List.copyOf(mesh.quads);
    }
    private static final class Mesh {
        final List<Quad> quads=new ArrayList<>();final Vec3 origin,along;final double length;final int limit;
        Mesh(Vec3 origin,Vec3 along,double length,int limit){this.origin=origin;this.along=along;this.length=length;this.limit=limit;}
        Vec3 clamp(Vec3 point){double n=point.subtract(origin).dot(along);return point.add(along.scale(Math.clamp(n,0,length)-n));}
        void prepared(Quad q){
            if(quads.size()>=limit)return;var a=clamp(q.a());var b=clamp(q.b());var c=clamp(q.c());var d=clamp(q.d());
            if(b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-13)return;
            quads.add(new Quad(a,b,c,d,q.color(),q.alpha(),q.material(),q.reverseV(),q.startU(),q.endU(),q.startV(),q.endV()));
        }
        void stroke(Vec3 from,Vec3 to,Vec3 normal,double width,int material,int color,float alpha){
            if(alpha<.005||from.distanceToSqr(to)<1E-10||quads.size()+2>limit)return;
            var w=normal.normalize().scale(width);float opacity=ShuoBrightness.opacity(alpha);int rgb=ShuoBrightness.tint(color);
            prepared(new Quad(from.subtract(w),to.subtract(w),to.add(w),from.add(w),rgb,opacity,material));
            prepared(new Quad(from.add(w),to.add(w),to.subtract(w),from.subtract(w),rgb,opacity,material,true));
        }
    }
}
