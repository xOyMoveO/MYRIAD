package com.myriad.character.skills.fenjue.rework;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static com.myriad.character.ability.AbilityVfxMaterials.FENJUE_GROUND;

/** One tessellated surface: the warm core and dark edges meet, never cover each other. */
public final class FlameFissureMesh {
    private static final Vec3 UP=new Vec3(0,1,0);
    private FlameFissureMesh(){}
    public static List<Quad> build(List<Vec3> points,double width,float alpha,boolean low,int seed){
        if(points.size()<2||width<=0||alpha<=0)return List.of();
        var offsets=new ArrayList<Vec3>();int count=points.size();
        for(int i=0;i<count;i++){
            Vec3 before=points.get(i).subtract(points.get(Math.max(0,i-1))).multiply(1,0,1).normalize();
            Vec3 after=points.get(Math.min(count-1,i+1)).subtract(points.get(i)).multiply(1,0,1).normalize();
            if(i==0)before=after;if(i==count-1)after=before;
            Vec3 normal=before.cross(UP).add(after.cross(UP)).normalize();
            double denominator=Math.abs(normal.dot(after.cross(UP)));
            double taper=i==0?.24:i==count-1?.16:.78+.20*Math.sin(seed*1.7+i*2.3);
            offsets.add(normal.scale(width*.5*taper/Math.max(.70,denominator)));
        }
        var out=new ArrayList<Quad>();double[] bands=low?new double[]{-1,1}:new double[]{-1,-.27,.27,1};
        for(int segment=0;segment<count-1;segment++)for(int band=0;band<bands.length-1;band++){
            var a=points.get(segment).add(offsets.get(segment).scale(bands[band]));
            var b=points.get(segment).add(offsets.get(segment).scale(bands[band+1]));
            var c=points.get(segment+1).add(offsets.get(segment+1).scale(bands[band+1]));
            var d=points.get(segment+1).add(offsets.get(segment+1).scale(bands[band]));
            if(b.subtract(a).cross(c.subtract(a)).y<=1E-12)continue;
            int color=low?0xFFD077:band==1?0xFFF1AB:band==0?0xFF7A25:0xFFAC3D;
            float opacity=alpha*(low?1f:band==1?1f:.76f);
            out.add(new Quad(a,b,c,d,color,Math.clamp(opacity,0,1),FENJUE_GROUND,false,.375f,.625f,.375f,.625f));
        }
        return List.copyOf(out);
    }
}
