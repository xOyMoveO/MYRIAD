package com.myriad.client.firstperson;
import com.google.gson.JsonParser;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;
/** Native blade-edge coordinates read from the packaged weapon, not a detached camera crescent. */
public final class ShuoBladeLight {
    public static final int SEGMENTS=6;
    private static final List<Vector3f> EDGE=load();
    private ShuoBladeLight(){}
    private static List<Vector3f> load(){
        var rows=new TreeMap<Float,Vector3f>();
        try(var in=ShuoBladeLight.class.getResourceAsStream("/assets/myriad/models/item/shuo_sword.json");var reader=new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){
            var model=JsonParser.parseReader(reader).getAsJsonObject();
            for(var value:model.getAsJsonArray("elements")){
                var e=value.getAsJsonObject();String name=e.get("name").getAsString();if(!name.startsWith("sword_blade_edge_")&&!name.startsWith("sword_blade_tip_"))continue;
                var a=e.getAsJsonArray("from");var b=e.getAsJsonArray("to");
                float x=b.get(0).getAsFloat()/16-.5f,z=(a.get(2).getAsFloat()+b.get(2).getAsFloat())/32-.5f;
                for(float y:new float[]{a.get(1).getAsFloat()/16-.5f,b.get(1).getAsFloat()/16-.5f}){
                    var old=rows.get(y);if(old==null||old.x<x)rows.put(y,new Vector3f(x,y,z));
                }
            }
        }catch(Exception e){throw new IllegalStateException("Shuo packaged blade edge unavailable",e);}
        if(rows.size()<20)throw new IllegalStateException("Incomplete Shuo cutting edge");
        return List.copyOf(rows.values());
    }
    public static Vector3f point(double fraction){
        float y=EDGE.getFirst().y+(EDGE.getLast().y-EDGE.getFirst().y)*(float)Math.clamp(fraction,0,1);
        var before=EDGE.getFirst();for(var next:EDGE){if(next.y>=y){float span=next.y-before.y;return span<1E-6?new Vector3f(next):new Vector3f(before).lerp(next,(y-before.y)/span);}before=next;}return new Vector3f(EDGE.getLast());
    }
    /** The part still inside the sheath never emits a visible blade, including partial draw/return. */
    public static double exposure(ShuoRig.Frame f){
        if(f.axis().dot(f.sheathAxis())<.995f)return 1;
        float mouth=new Vector3f(f.mouth()).sub(f.palm()).dot(f.axis())/ShuoRig.WEAPON_LENGTH;
        return Math.clamp((mouth-EDGE.getFirst().y)/(EDGE.getLast().y-EDGE.getFirst().y),0,1);
    }
    public static Vec3 at(Matrix4f blade,double fraction){var v=blade.transformPosition(point(fraction));return new Vec3(v.x,v.y,v.z);}
    public static List<Quad> build(Matrix4f blade,double visible,double phase,float alpha,boolean camera){
        if(!blade.isFinite()||!Double.isFinite(visible)||!Double.isFinite(phase)||!Float.isFinite(alpha)||visible<=0||alpha<=0)return List.of();
        var out=new ArrayList<Quad>();
        var wide=blade.transformDirection(new Vector3f(1,0,0)).normalize();var thin=blade.transformDirection(new Vector3f(0,0,1)).normalize();
        var x=new Vec3(wide.x,wide.y,wide.z).scale(.036);var z=new Vec3(thin.x,thin.y,thin.z).scale(.030);
        for(int i=0;i<SEGMENTS;i++){
            double a=visible*i/SEGMENTS,b=visible*(i+1)/SEGMENTS;var p=at(blade,a);var q=at(blade,b);
            float pulse=(float)(.72+.20*Math.sin(phase*1.2-i*.8));
            panel(out,p.subtract(x),q.subtract(x),q.add(x),p.add(x),77,0xD3ECFF,alpha*pulse,camera,0,1,0,1);
            panel(out,p.subtract(z),q.subtract(z),q.add(z),p.add(z),77,0xB5DEFF,alpha*pulse*.75f,camera,0,1,0,1);
            var core=x.scale(.28);panel(out,p.subtract(core),q.subtract(core),q.add(core),p.add(core),0,0xF8FDFF,alpha*.88f,camera,.45f,.55f,.45f,.55f);
        }
        return List.copyOf(out);
    }
    public static void panel(List<Quad> out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int mat,int rgb,float alpha,boolean camera,float u,float v,float y,float z){
        if(camera&&(a.z>=-.055||b.z>=-.055||c.z>=-.055||d.z>=-.055)||alpha<.005||b.subtract(a).cross(c.subtract(a)).lengthSqr()<1E-14)return;
        alpha=com.myriad.character.skills.shuo.ShuoBrightness.opacity(alpha);rgb=com.myriad.character.skills.shuo.ShuoBrightness.tint(rgb);
        out.add(new Quad(a,b,c,d,rgb,alpha,mat,false,u,v,y,z));out.add(new Quad(d,c,b,a,rgb,alpha,mat,true,u,v,y,z));
    }
}
