package com.myriad.character.skills.lingyuan.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.myriad.character.ability.AbilityMeshBuilder.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** Rich pixel-painted transparent sprites, layered in 3D. No block/cube ornaments. */
final class SwordPixelLayers {
    private final List<Quad> out;private final float fade;private final boolean low;private int count;
    private final long started;private final float age;
    SwordPixelLayers(List<Quad> out,float fade,boolean low,long started,float age){
        this.out=out;this.fade=fade;this.low=low;this.started=started;this.age=age;
    }
    private void plane(Vec3 at,Vec3 right,Vec3 up,double width,double height,int material,double alpha){
        plane(at,right,up,width,height,material,alpha,SwordFlipbook.loop(material,started,age,0));
    }
    private void plane(Vec3 at,Vec3 right,Vec3 up,double width,double height,int material,double alpha,SwordFlipbook.Cell cell){
        if(count+2>(low?32:128)||out.size()+2>4096||alpha*fade<=0)return;
        Vec3 r=right.normalize().scale(width),u=up.normalize().scale(height);
        Vec3 a=at.subtract(r).subtract(u),b=at.add(r).subtract(u),c=at.add(r).add(u),d=at.subtract(r).add(u);
        float opacity=(float)Math.clamp(alpha*fade,0,1);
        out.add(new Quad(a,b,c,d,0xFFFFFF,opacity,material,false,cell.u0(),cell.u1(),cell.v0(),cell.v1()));
        out.add(new Quad(d,c,b,a,0xFFFFFF,opacity,material,true,cell.u0(),cell.u1(),cell.v0(),cell.v1()));count+=2;
    }
    /** Narrow crossed energy ribbon; preserves footprint, not the discarded cuboid. */
    void bar(Vec3 at,Vec3 forward,double length,double width,double depth,int ignored,double alpha){
        Vec3 f=forward.lengthSqr()<1E-8?Z:forward.normalize(),s=side(f),u=s.cross(f).normalize();
        plane(at,f,s,length*.8,Math.max(width,depth)*2.5,LINGYUAN_PIXEL_STREAM,alpha);
        if(!low)plane(at,f,u,length*.8,Math.max(width,depth)*2.1,LINGYUAN_PIXEL_STREAM,alpha*.7,SwordFlipbook.loop(LINGYUAN_PIXEL_STREAM,started,age,3));
    }
    void chip(Vec3 at,double size,int ignored,double alpha){
        plane(at,X,UP,size*2,size*2,LINGYUAN_PIXEL_MOTES,alpha);
        if(!low)plane(at,Z,UP,size*1.6,size*1.6,LINGYUAN_PIXEL_MOTES,alpha*.65,SwordFlipbook.loop(LINGYUAN_PIXEL_MOTES,started,age,4));
    }
    void ring(Vec3 at,double radius,double phase,int ignored){
        Vec3 right=new Vec3(Math.cos(phase),0,Math.sin(phase)),up=new Vec3(-Math.sin(phase),0,Math.cos(phase));
        plane(at,right,up,radius,radius,LINGYUAN_PIXEL_SEAL,.9);
        if(!low)plane(at.add(0,.065,0),new Vec3(Math.cos(-phase),0,Math.sin(-phase)),new Vec3(-Math.sin(-phase),0,Math.cos(-phase)),radius*.9,radius*.9,LINGYUAN_PIXEL_FILAMENT,.85);
    }
    void halo(Vec3 at,Vec3 forward,double radius,double phase){
        Vec3 f=forward.lengthSqr()<1E-8?Z:forward.normalize(),s=side(f),u=s.cross(f).normalize();
        Vec3 right=s.scale(Math.cos(phase)).add(u.scale(Math.sin(phase))),up=u.scale(Math.cos(phase)).subtract(s.scale(Math.sin(phase)));
        plane(at,right,up,radius,radius,LINGYUAN_PIXEL_SEAL,.95);
        if(!low)plane(at.add(f.scale(.06)),up,right.scale(-1),radius*.9,radius*.9,LINGYUAN_PIXEL_FILAMENT,.9);
    }
    void crown(Vec3 at,Vec3 forward,double radius,double phase,int n){
        Vec3 f=forward.lengthSqr()<1E-8?Z:forward.normalize();
        n=low?Math.min(2,n):n;
        for(int i=0;i<n;i++){
            double a=i*Math.PI*2/n+phase;Vec3 p=circle(at,radius,a,.25+Math.sin(phase+a)*.06);
            if(i%3==0||low)crescent(p,f,.28,.4,a,.85);
            else {
                Vec3 s=side(f),u=s.cross(f).normalize();int material=i%3==1?LINGYUAN_PIXEL_FILAMENT:LINGYUAN_PIXEL_MOTES;
                plane(p,s,u,.4,.4,material,.85,SwordFlipbook.loop(material,started,age,i*2));
            }
        }
    }
    void wake(Vec3 from,Vec3 to,double width,double phase){
        if(from.distanceToSqr(to)<1E-8)return;
        Vec3 f=direction(from,to),s=side(f),u=s.cross(f).normalize(),center=from.lerp(to,.5);
        double half=from.distanceTo(to)*.65;
        for(int i=0;i<(low?1:2);i++){
            double a=phase*.25+i*Math.PI/3;Vec3 up=s.scale(Math.cos(a)).add(u.scale(Math.sin(a)));
            plane(center.add(up.scale(i==0?0:width*.25)),f,up,half,width*(i==0?2.5:1.8),LINGYUAN_PIXEL_STREAM,i==0?.95:.65,SwordFlipbook.loop(LINGYUAN_PIXEL_STREAM,started,age,i*3));
        }
        if(!low)plane(to.subtract(f.scale(width)),s,u,width*2,width*2,LINGYUAN_PIXEL_MOTES,.8);
    }
    void impact(Vec3 at,Vec3 forward,double radius,double progress){
        Vec3 f=forward.lengthSqr()<1E-8?Z:forward.normalize(),s=side(f),u=s.cross(f).normalize();
        double size=radius*(.55+progress*.65);
        plane(at,s,u,size,size,LINGYUAN_PIXEL_IMPACT,.96,SwordFlipbook.burst(progress));
        if(!low){
            plane(at.add(f.scale(.045)),s.add(u).normalize(),u.subtract(s).normalize(),size*.95,size*.95,LINGYUAN_PIXEL_MOTES,.8*(1-Math.clamp(progress,0,1)));
            if(progress<.7)crescent(at.add(0,.035,0),f,size*.8,size*.8,Math.PI/2,.75*(1-progress));
        }
    }
    void slash(Vec3 at,Vec3 forward,double size,double phase){
        Vec3 f=forward.lengthSqr()<1E-8?Z:forward.normalize();
        crescent(at,f,size,size,phase,.97);
        if(!low){
            crescent(at.add(f.scale(.06)),f,size*.82,size*.82,phase+Math.PI/2,.65);
            Vec3 s=side(f),u=s.cross(f).normalize();
            plane(at,s,u,size*.65,size*.65,LINGYUAN_PIXEL_FILAMENT,.8);
        }
    }
    /** The texture's convex front is -U (left), and its opening/tips are +U.
     * Map -U to true travel, not the plane normal. Roll only around that axis,
     * so crossing layers never turn the crescent's front sideways or backward. */
    private void crescent(Vec3 at,Vec3 forward,double width,double height,double roll,double alpha){
        Vec3 f=forward.lengthSqr()<1E-8?Z:forward.normalize(),s=side(f),u=s.cross(f).normalize();
        Vec3 across=u.scale(Math.cos(roll)).add(s.scale(Math.sin(roll)));
        plane(at,f.scale(-1),across,width,height,LINGYUAN_PIXEL_SLASH,alpha);
    }
}
