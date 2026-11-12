package com.myriad.character.skills.tianxing.rework;

import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.ability.AbilityVfxMaterials;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Cached approved Blockbench source faces. No ItemDisplay entities or independent lifetime. */
public final class ThunderProjectileModels {
    private record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float v0,float u1,float v1,int shade){}
    private record Mesh(int parts,double tip,List<Face> faces){}
    private static final Map<String,Mesh> MESHES=load();
    private ThunderProjectileModels(){}
    private static Map<String,Mesh> load(){
        try(var in=ThunderProjectileModels.class.getResourceAsStream("/assets/myriad/vfx/tianxing_projectiles_mesh.json")){
            if(in==null)throw new IllegalStateException("Missing thunder projectile meshes");
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1||root.get("source_length").getAsInt()!=64||!root.get("forward_axis").getAsString().equals("+X"))throw new IllegalStateException("Thunder model axes");
            var meshes=new HashMap<String,Mesh>();
            for(var entry:root.getAsJsonObject("meshes").entrySet()){
                var source=entry.getValue().getAsJsonObject();var faces=new ArrayList<Face>();
                for(var item:source.getAsJsonArray("faces")){
                    var a=item.getAsJsonArray();if(a.size()!=17)throw new IllegalStateException("Thunder face");
                    Vec3[] v=new Vec3[4];for(int i=0;i<4;i++)v[i]=new Vec3(a.get(i*3).getAsDouble(),a.get(i*3+1).getAsDouble(),a.get(i*3+2).getAsDouble());
                    faces.add(new Face(v[0],v[1],v[2],v[3],a.get(12).getAsFloat(),a.get(13).getAsFloat(),a.get(14).getAsFloat(),a.get(15).getAsFloat(),a.get(16).getAsInt()));
                }meshes.put(entry.getKey(),new Mesh(source.get("parts").getAsInt(),source.get("tip_x").getAsDouble(),List.copyOf(faces)));
            }return Map.copyOf(meshes);
        }catch(Exception e){throw new IllegalStateException("Cannot load thunder projectile models",e);}
    }
    private static String key(boolean slash,boolean low){return (slash?"slash":"arc")+(low?"_lod":"");}
    public static int faces(boolean slash,boolean low){return MESHES.get(key(slash,low)).faces.size();}
    public static int parts(boolean slash,boolean low){return MESHES.get(key(slash,low)).parts;}
    public static double scale(boolean slash){return (slash?2.4:1.8)/64;}
    /** +X maps to actual 3D forward, +Y to blade height, +Z to side: proper right-handed frame. */
    public static void append(List<Quad> out,Vec3 tip,Vec3 direction,boolean slash,boolean low,float alpha){
        Vec3 f=direction.lengthSqr()<1E-9?new Vec3(0,0,1):direction.normalize();
        Vec3 side=f.cross(new Vec3(0,1,0));side=side.lengthSqr()<1E-9?new Vec3(1,0,0):side.normalize();
        Vec3 up=side.cross(f).normalize();double scale=scale(slash);
        var mesh=MESHES.get(key(slash,low));
        Vec3 origin=tip.subtract(f.scale(mesh.tip*scale));
        for(var q:mesh.faces)out.add(new Quad(
                world(q.a,origin,f,up,side,scale),world(q.b,origin,f,up,side,scale),world(q.c,origin,f,up,side,scale),world(q.d,origin,f,up,side,scale),
                q.shade,alpha,AbilityVfxMaterials.THUNDER_PROJECTILE_MODEL,false,q.u0,q.u1,q.v0,q.v1));
    }
    private static Vec3 world(Vec3 p,Vec3 tip,Vec3 f,Vec3 up,Vec3 side,double scale){
        return new Vec3(tip.x+scale*(f.x*p.x+up.x*p.y+side.x*p.z),
                tip.y+scale*(f.y*p.x+up.y*p.y+side.y*p.z),tip.z+scale*(f.z*p.x+up.z*p.y+side.z*p.z));
    }
}
