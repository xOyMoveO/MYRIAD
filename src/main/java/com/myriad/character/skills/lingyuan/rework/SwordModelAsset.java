package com.myriad.character.skills.lingyuan.rework;

import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.character.ability.AbilityVfxMaterials;
import net.minecraft.world.phys.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.myriad.character.ability.AbilityMeshBuilder.*;

/** Cached faces compiled from approved cuboid sources, without client class dependencies. */
public final class SwordModelAsset {
    private SwordModelAsset(){}
    private record Face(Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float v0,float u1,float v1,int shade){}
    private record Mesh(int parts,List<Face> faces){}
    private static final Map<String,Mesh> MESHES=load();
    private static Map<String,Mesh> load(){
        try(var input=SwordModelAsset.class.getResourceAsStream("/assets/myriad/vfx/lingyuan_sword_mesh.json")){
            if(input==null)throw new IllegalStateException("Missing compiled Lingyuan model");
            var root=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("version").getAsInt()!=1||root.get("source_length").getAsInt()!=112)throw new IllegalStateException("Unexpected model units");
            var meshes=new HashMap<String,Mesh>();
            for(var entry:root.getAsJsonObject("meshes").entrySet()){
                var faces=new ArrayList<Face>();
                for(var element:entry.getValue().getAsJsonObject().getAsJsonArray("faces")){
                    var a=element.getAsJsonArray();if(a.size()!=17)throw new IllegalStateException("Invalid model face");
                    Vec3[] vertices=new Vec3[4];
                    for(int i=0;i<4;i++)vertices[i]=new Vec3(a.get(i*3).getAsDouble(),a.get(i*3+1).getAsDouble(),a.get(i*3+2).getAsDouble());
                    faces.add(new Face(vertices[0],vertices[1],vertices[2],vertices[3],a.get(12).getAsFloat(),a.get(13).getAsFloat(),
                            a.get(14).getAsFloat(),a.get(15).getAsFloat(),a.get(16).getAsInt()));
                }
                meshes.put(entry.getKey(),new Mesh(entry.getValue().getAsJsonObject().get("parts").getAsInt(),List.copyOf(faces)));
            }
            return Map.copyOf(meshes);
        }catch(Exception e){throw new IllegalStateException("Cannot read the approved Lingyuan model",e);}
    }
    public static int sourceParts(String name){return MESHES.get(name).parts;}
    public static int faceCount(String name){return MESHES.get(name).faces.size();}
    public static Vec3 centerAtTip(Vec3 tip,Vec3 forward,double length){return tip.subtract(forward.normalize().scale(length*.5));}
    /** Local +Y follows the true 3D direction, using a right-handed frame. */
    public static void append(List<Quad> out,String name,Vec3 center,Vec3 forward,double length,double phase,float alpha){
        if(alpha<=0||length<=0)return;
        Mesh mesh=MESHES.get(name);if(mesh==null)throw new IllegalArgumentException(name);
        if(out.size()+mesh.faces.size()>4096)return;
        Vec3 f=forward.lengthSqr()<1E-10?Z:forward.normalize(),s=side(f),u=s.cross(f).normalize();
        appendFaces(out,mesh,name,center,f,s,u,length,phase,alpha);
    }
    /** Stable sword-plane basis for a chop crossing vertical; prevents a 180-degree roll. */
    public static void appendOriented(List<Quad> out,String name,Vec3 center,Vec3 forward,Vec3 right,double length,float alpha){
        if(alpha<=0||length<=0)return;
        Mesh mesh=MESHES.get(name);if(mesh==null)throw new IllegalArgumentException(name);
        if(out.size()+mesh.faces.size()>4096)return;
        Vec3 f=forward.lengthSqr()<1E-10?Z:forward.normalize();
        Vec3 s=right.subtract(f.scale(right.dot(f))).normalize();if(s.lengthSqr()<1E-8)s=side(f);
        appendFaces(out,mesh,name,center,f,s,s.cross(f).normalize(),length,0,alpha);
    }
    private static void appendFaces(List<Quad> out,Mesh mesh,String name,Vec3 center,Vec3 f,Vec3 s,Vec3 u,double length,double phase,float alpha){
        double spin=switch(name){case "trail_cyan","trail_blue"->phase;case "sigil"->phase;
            case "afterimage"->Math.sin(phase)*.035;default->0;};
        double co=Math.cos(spin),si=Math.sin(spin),scale=length/112;
        for(var face:mesh.faces){
            out.add(new Quad(world(face.a,name,center,f,s,u,scale,co,si,phase),world(face.b,name,center,f,s,u,scale,co,si,phase),
                    world(face.c,name,center,f,s,u,scale,co,si,phase),world(face.d,name,center,f,s,u,scale,co,si,phase),face.shade,
                    Math.clamp(alpha,0,1),AbilityVfxMaterials.LINGYUAN_PIXEL_MODEL,false,face.u0,face.u1,face.v0,face.v1));
        }
    }
    private static Vec3 world(Vec3 v,String name,Vec3 center,Vec3 f,Vec3 s,Vec3 u,double scale,double co,double si,double phase){
        double x=v.x,y=v.y,z=v.z;
        if(name.equals("trail_cyan")||name.equals("trail_blue")){double nx=x*co+z*si;z=-x*si+z*co;x=nx;}
        else if(name.equals("sigil")){double yy=y-5,nx=x*co-yy*si;y=5+x*si+yy*co;x=nx;}
        else if(name.equals("afterimage")){double yy=y+18,nx=x*co-yy*si;y=-18+x*si+yy*co-3.5*(1-Math.cos(phase));x=nx;}
        else if(name.equals("spark"))y+=Math.sin(phase)*2;
        return center.add(s.scale(x*scale)).add(f.scale((y-30)*scale)).add(u.scale(z*scale));
    }
}
