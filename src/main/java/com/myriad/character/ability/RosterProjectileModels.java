package com.myriad.character.ability;

import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small immutable face cache for the two roster weapons' spec-generated +Y projectile models. */
public final class RosterProjectileModels {
    private record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float v0,float u1,float v1,int shade){}
    private record Mesh(int parts,double tip,List<Face> faces){}
    private final Map<String,Mesh> meshes;private final double sourceLength;
    private RosterProjectileModels(Map<String,Mesh> meshes,double length){this.meshes=Map.copyOf(meshes);sourceLength=length;}
    public static RosterProjectileModels load(String resource){
        try(var in=RosterProjectileModels.class.getResourceAsStream("/assets/myriad/vfx/"+resource)){
            if(in==null)throw new IllegalStateException("Missing projectile mesh "+resource);
            var root=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1||!root.get("forward_axis").getAsString().equals("+Y"))throw new IllegalStateException("Projectile source axes");
            double length=root.get("source_length").getAsDouble();if(!(length>0))throw new IllegalStateException("Projectile source length");
            var result=new HashMap<String,Mesh>();
            for(var entry:root.getAsJsonObject("meshes").entrySet()){
                var data=entry.getValue().getAsJsonObject();var faces=new ArrayList<Face>();double tip=data.get("tip_y").getAsDouble();
                for(var value:data.getAsJsonArray("faces")){
                    var row=value.getAsJsonArray();if(row.size()!=17)throw new IllegalStateException("Projectile face schema");
                    Vec3[] vertices=new Vec3[4];for(int i=0;i<4;i++){
                        vertices[i]=new Vec3(row.get(i*3).getAsDouble(),row.get(i*3+1).getAsDouble(),row.get(i*3+2).getAsDouble());
                        if(!Double.isFinite(vertices[i].x)||!Double.isFinite(vertices[i].y)||!Double.isFinite(vertices[i].z)||vertices[i].y>tip+1E-6)throw new IllegalStateException("Projectile front extent");
                    }
                    for(int i=12;i<16;i++)if(!Float.isFinite(row.get(i).getAsFloat())||row.get(i).getAsFloat()<0||row.get(i).getAsFloat()>1)throw new IllegalStateException("Projectile UV bounds");
                    faces.add(new Face(vertices[0],vertices[1],vertices[2],vertices[3],row.get(12).getAsFloat(),row.get(13).getAsFloat(),row.get(14).getAsFloat(),row.get(15).getAsFloat(),row.get(16).getAsInt()));
                }
                result.put(entry.getKey(),new Mesh(data.get("parts").getAsInt(),tip,List.copyOf(faces)));
            }
            return new RosterProjectileModels(result,length);
        }catch(Exception e){throw new IllegalStateException("Cannot load roster projectile: "+resource,e);}
    }
    public int faces(String key){return meshes.get(key).faces.size();}
    public int parts(String key){return meshes.get(key).parts;}
    public void append(List<Quad> out,String key,Vec3 tip,Vec3 direction,double length,int material,float alpha){append(out,key,tip,direction,length,length,material,alpha);}
    /** Independent transverse length permits a long penetration needle without broadening its blade. */
    public void append(List<Quad> out,String key,Vec3 tip,Vec3 direction,double length,double transverseLength,int material,float alpha){
        if(alpha<=0||length<=0||transverseLength<=0)return;
        Vec3 forward=direction.lengthSqr()<1E-10?new Vec3(0,0,1):direction.normalize();
        Vec3 side=forward.cross(new Vec3(0,1,0));side=side.lengthSqr()<1E-10?new Vec3(1,0,0):side.normalize();
        Vec3 up=side.cross(forward).normalize();var mesh=meshes.get(key);
        for(var face:mesh.faces)out.add(new Quad(world(face.a,tip,forward,side,up,mesh.tip,length,transverseLength),world(face.b,tip,forward,side,up,mesh.tip,length,transverseLength),
                world(face.c,tip,forward,side,up,mesh.tip,length,transverseLength),world(face.d,tip,forward,side,up,mesh.tip,length,transverseLength),face.shade,alpha,material,false,face.u0,face.u1,face.v0,face.v1));
    }
    private Vec3 world(Vec3 p,Vec3 tip,Vec3 forward,Vec3 side,Vec3 up,double sourceTip,double length,double width){
        return tip.add(forward.scale((p.y-sourceTip)*length/sourceLength)).add(side.scale(p.x*width/sourceLength)).add(up.scale(p.z*width/sourceLength));
    }
}
