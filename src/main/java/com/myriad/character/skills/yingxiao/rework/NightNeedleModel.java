package com.myriad.character.skills.yingxiao.rework;

import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.ability.AbilityVfxMaterials;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable cached model from the validated editable BBModel source, no display entities. */
public final class NightNeedleModel {
    private record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float v0,float u1,float v1,int shade){}
    private static final Map<String,List<Face>> MESHES=load();
    private NightNeedleModel(){}
    private static Map<String,List<Face>> load(){
        try(var in=NightNeedleModel.class.getResourceAsStream("/assets/myriad/vfx/night_shadow_needle_mesh.json")){
            if(in==null)throw new IllegalStateException("Missing needle mesh");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1||root.get("source_length").getAsInt()!=64)throw new IllegalStateException("Needle units");
            var result=new HashMap<String,List<Face>>();
            for(var entry:root.getAsJsonObject("meshes").entrySet()){
                var faces=new ArrayList<Face>();
                for(var item:entry.getValue().getAsJsonObject().getAsJsonArray("faces")){
                    var a=item.getAsJsonArray();if(a.size()!=17)throw new IllegalStateException("Needle face");
                    Vec3[] v=new Vec3[4];for(int i=0;i<4;i++)v[i]=new Vec3(a.get(i*3).getAsDouble(),a.get(i*3+1).getAsDouble(),a.get(i*3+2).getAsDouble());
                    faces.add(new Face(v[0],v[1],v[2],v[3],a.get(12).getAsFloat(),a.get(13).getAsFloat(),a.get(14).getAsFloat(),a.get(15).getAsFloat(),a.get(16).getAsInt()));
                }
                result.put(entry.getKey(),List.copyOf(faces));
            }
            return Map.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Cannot load Night needle",e);}
    }
    public static int faces(boolean low){return MESHES.get(low?"lod":"body").size();}
    /** Source +Y maps to real 3D travel. Tip is precisely at tip, tail 1.25 blocks behind. */
    public static void append(List<Quad> out,Vec3 tip,Vec3 direction,boolean low,float alpha){
        Vec3 f=direction.normalize(),s=f.cross(new Vec3(0,1,0));
        if(s.lengthSqr()<1E-8)s=new Vec3(1,0,0);else s=s.normalize();
        Vec3 u=s.cross(f).normalize();
        for(var q:MESHES.get(low?"lod":"body"))out.add(new Quad(
                world(q.a,tip,f,s,u),world(q.b,tip,f,s,u),world(q.c,tip,f,s,u),world(q.d,tip,f,s,u),
                q.shade,alpha,AbilityVfxMaterials.NIGHT_NEEDLE_MODEL,false,q.u0,q.u1,q.v0,q.v1));
    }
    private static Vec3 world(Vec3 p,Vec3 tip,Vec3 f,Vec3 s,Vec3 u){
        double scale=1.25/64;
        return tip.add(f.scale((p.y-64)*scale)).add(s.scale(p.x*scale)).add(u.scale(p.z*scale));
    }
}
