package com.myriad.character.skills.fenjue.rework;

import com.myriad.network.AbilityVfxPayload;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.ARC;

/** Shared full-3D cast frame and filled blade sweeps; server collision derives from the painted arc. */
public final class FlameSlashAim {
    public static final float VISUAL_RADIUS=4;
    public static final double HIT_PADDING=.20;
    private static final int SEGMENTS=36;
    public static int hitTick(int cut){if(cut<0||cut>1)throw new IllegalArgumentException("cut");return cut==0?3:7;}
    public record Basis(Vec3 forward,Vec3 side,Vec3 up){}
    private FlameSlashAim(){}
    public static Basis basis(Vec3 look,double yaw){
        if(!Double.isFinite(yaw))yaw=0;
        var f=look.lengthSqr()>1E-12?look.normalize():new Vec3(0,0,1);
        double a=Math.toRadians(yaw);var side=new Vec3(-Math.cos(a),0,-Math.sin(a));
        side=side.subtract(f.scale(side.dot(f)));
        if(side.lengthSqr()<1E-10){side=f.cross(new Vec3(0,1,0));if(side.lengthSqr()<1E-10)side=new Vec3(1,0,0);}
        side=side.normalize();return new Basis(f,side,side.cross(f).normalize());
    }
    /** For theme2/variant2/ARC only, angle carries cast yaw, not a gameplay cone width. */
    public static AbilityVfxPayload recipe(int owner,Vec3 eye,Vec3 look,double yaw,long started){
        var frame=basis(look,yaw);float encoded=(float)((yaw%360+360)%360);
        return new AbilityVfxPayload(owner,-1,2,2,ARC,eye,eye.add(frame.forward),VISUAL_RADIUS,encoded,14,started,false);
    }

    /** The arc is the leading blade edge; the area between the hand and that edge is swept too.
     * Reuse the actual VFX center/diagonal/contour, not an unrelated spherical radius or eye cone.
     * Each stroke has its own tilted plane. Padding is only 0.2 blocks, not a giant hidden sphere. */
    public static Sweep sweep(AbilityVfxPayload p,int cut){
        var frame=basis(p.to().subtract(p.from()),p.angle());
        var center=FlameCrossSlashVfx.center(p,cut,hitTick(cut));
        var diagonal=FlameCrossSlashVfx.diagonal(p,cut);
        var origin=p.from().add(frame.up().scale(FlameCrossSlashVfx.LIFT));
        var rim=new java.util.ArrayList<Vec3>();
        AABB bounds=new AABB(origin,origin);
        for(int i=0;i<=SEGMENTS;i++){
            double t=i/(double)SEGMENTS;
            var local=FlameCrossSlashVfx.localPoint(t,FlameCrossSlashVfx.sourceEdge(t)/512,p.radius());
            var point=center.add(diagonal.scale(local.x)).add(frame.forward().scale(local.y));
            rim.add(point);bounds=bounds.minmax(new AABB(point,point));
        }
        return new Sweep(origin,java.util.List.copyOf(rim),bounds.inflate(HIT_PADDING));
    }
    public record Sweep(Vec3 origin,java.util.List<Vec3> rim,AABB bounds){
        public boolean contains(AABB target){
            if(!bounds.intersects(target))return false;
            var box=target.inflate(HIT_PADDING);
            for(int i=1;i<rim.size();i++)if(triangleBox(origin,rim.get(i-1),rim.get(i),box))return true;
            return false;
        }
    }
    /** Triangle/AABB separating-axis test: includes grazes and large bodies whose center is outside. */
    private static boolean triangleBox(Vec3 a,Vec3 b,Vec3 c,AABB box){
        var center=box.getCenter();a=a.subtract(center);b=b.subtract(center);c=c.subtract(center);
        var extent=new Vec3(box.getXsize()*.5,box.getYsize()*.5,box.getZsize()*.5);
        var axes=java.util.List.of(new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        for(var axis:axes)if(separated(axis,a,b,c,extent))return false;
        var ab=b.subtract(a);var bc=c.subtract(b);var ca=a.subtract(c);
        if(separated(ab.cross(c.subtract(a)),a,b,c,extent))return false;
        for(var edge:java.util.List.of(ab,bc,ca))for(var axis:axes)
            if(separated(edge.cross(axis),a,b,c,extent))return false;
        return true;
    }
    private static boolean separated(Vec3 axis,Vec3 a,Vec3 b,Vec3 c,Vec3 extent){
        if(axis.lengthSqr()<1E-16)return false;
        double x=a.dot(axis),y=b.dot(axis),z=c.dot(axis);
        double radius=Math.abs(axis.x)*extent.x+Math.abs(axis.y)*extent.y+Math.abs(axis.z)*extent.z;
        return Math.min(x,Math.min(y,z))>radius+1E-8||Math.max(x,Math.max(y,z))< -radius-1E-8;
    }
}
