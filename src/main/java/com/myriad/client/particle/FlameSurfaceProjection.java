package com.myriad.client.particle;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Per-particle bounded surface cache: no chunk loading and no repeated frame-by-frame world ray casts. */
public final class FlameSurfaceProjection {
    public static final double GROUND_OFFSET=.025;
    private record Key(int x,int y,int z){}
    private final Map<Key,Double> heights=new LinkedHashMap<>();private long refreshed=-100;
    public Quad project(Quad q,ClientLevel level,double referenceY,boolean ground){
        long now=level.getGameTime();if(now-refreshed>=20){heights.clear();refreshed=now;}
        return project(q,v->height(level,v.x,referenceY,v.z),ground,referenceY);
    }
    private double height(ClientLevel level,double x,double reference,double z){
        var key=new Key((int)Math.floor(x*8),(int)Math.floor(reference*2),(int)Math.floor(z*8));
        var cached=heights.get(key);if(cached!=null)return cached;
        double best=Double.NaN,difference=Double.POSITIVE_INFINITY;
        double sx=(key.x+.5)/8,sz=(key.z+.5)/8;
        for(int y=(int)Math.floor(reference+1);y>=Math.floor(reference-2);y--){
            var pos=BlockPos.containing(sx,y,sz);if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);if(!state.getFluidState().isEmpty())continue;
            var hit=state.getCollisionShape(level,pos).clip(new Vec3(sx,reference+1.2,sz),new Vec3(sx,reference-2,sz),pos);
            if(hit==null||hit.getDirection()!=Direction.UP)continue;
            double distance=Math.abs(hit.getLocation().y-reference);
            if(distance<difference){difference=distance;best=hit.getLocation().y;}
        }
        if(heights.size()>=512)heights.remove(heights.keySet().iterator().next());
        heights.put(key,best);return best;
    }
    /** Pure seam for slab/step/cliff tests. Missing ground and steep cliffs are clipped, never stretched. */
    public static Quad project(Quad q,ToDoubleFunction<Vec3> height,boolean ground,double referenceY){
        Vec3 a,b,c,d;
        if(ground){
            double ya=height.applyAsDouble(q.a()),yb=height.applyAsDouble(q.b()),yc=height.applyAsDouble(q.c()),yd=height.applyAsDouble(q.d());
            double min=Math.min(Math.min(ya,yb),Math.min(yc,yd)),max=Math.max(Math.max(ya,yb),Math.max(yc,yd));
            if(!Double.isFinite(min)||!Double.isFinite(max)||max-min>.60)return null;
            a=new Vec3(q.a().x,ya+GROUND_OFFSET,q.a().z);b=new Vec3(q.b().x,yb+GROUND_OFFSET,q.b().z);
            c=new Vec3(q.c().x,yc+GROUND_OFFSET,q.c().z);d=new Vec3(q.d().x,yd+GROUND_OFFSET,q.d().z);
        }else{
            var center=q.a().add(q.b()).add(q.c()).add(q.d()).scale(.25);
            double y=height.applyAsDouble(center);if(!Double.isFinite(y))return null;
            var offset=new Vec3(0,y-referenceY+.018,0);a=q.a().add(offset);b=q.b().add(offset);c=q.c().add(offset);d=q.d().add(offset);
        }
        return new Quad(a,b,c,d,q.color(),q.alpha(),q.material(),q.reverseV(),q.startU(),q.endU(),q.startV(),q.endV());
    }
}
