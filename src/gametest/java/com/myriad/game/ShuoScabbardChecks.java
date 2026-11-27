package com.myriad.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.myriad.client.firstperson.ShuoMotion;
import com.myriad.client.firstperson.ShuoRig;
import com.myriad.network.ShuoViewPayload;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Intersectionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.myriad.network.ShuoViewPayload.*;

/** Exercise the actual blade and hollow sheath solids, including an interrupted draw. */
final class ShuoScabbardChecks {
    private record Box(String name, Vector3f center, Vector3f half) {}
    private record Axes(Vector3f x, Vector3f y, Vector3f z) {}
    private static Vector3f vertex(JsonArray a) {
        return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat())
                .sub(8,8,8).div(16);
    }
    private static List<Box> read(String model) {
        var boxes=new ArrayList<Box>();
        try (var in=ShuoScabbardChecks.class.getResourceAsStream("/assets/myriad/models/item/shuo_"+model+".json");
             var reader=new InputStreamReader(in,StandardCharsets.UTF_8)) {
            for (var element:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("elements")) {
                var part=element.getAsJsonObject();
                var min=vertex(part.getAsJsonArray("from"));
                var max=vertex(part.getAsJsonArray("to"));
                // Inset by < 0.001 source pixel; a shared boundary is a valid fit.
                var half=new Vector3f(max).sub(min).mul(.5f).sub(.00002f,.00002f,.00002f);
                boxes.add(new Box(part.get("name").getAsString(),min.lerp(max,.5f),half));
            }
        } catch (Exception e) { throw new IllegalStateException("Could not read "+model,e); }
        return boxes;
    }
    private static Axes axes(Matrix4f m) {
        return new Axes(m.transformDirection(new Vector3f(1,0,0)).normalize(),
                m.transformDirection(new Vector3f(0,1,0)).normalize(),
                m.transformDirection(new Vector3f(0,0,1)).normalize());
    }
    private static List<Box> placed(List<Box> mesh, Matrix4f m) {
        var scale=m.getScale(new Vector3f());
        return mesh.stream().map(b->new Box(b.name,m.transformPosition(new Vector3f(b.center)),
                new Vector3f(b.half).mul(scale))).toList();
    }
    private static void check(GameTestHelper h,List<Box> blade,List<Box> sheath,ShuoRig.Frame pose,boolean left,double tick) {
        var bm=ShuoRig.blade(pose,left);
        var sm=ShuoRig.sheath(pose,left);
        var ba=axes(bm); var sa=axes(sm);
        var walls=placed(sheath,sm);
        for (var b:placed(blade,bm)) {
            float radius=b.half.length();
            for (var w:walls) {
                float reach=radius+w.half.length();
                if (b.center.distanceSquared(w.center)>=reach*reach) continue;
                if (Intersectionf.testObOb(b.center,ba.x,ba.y,ba.z,b.half,w.center,sa.x,sa.y,sa.z,w.half))
                    h.fail("Shuo "+b.name+" passes through "+w.name+" at "+tick+" ticks, left="+left);
            }
        }
    }
    static void verify(GameTestHelper h) {
        var blade=read("sword"); var sheath=read("scabbard");
        for (boolean left:new boolean[]{false,true}) {
            for (int i=0;i<=64;i++) {
                double t=i/64.0;
                check(h,blade,sheath,ShuoRig.frame(DRAW,t,false),left,t);
                check(h,blade,sheath,ShuoRig.frame(SHEATHE,t,true),left,t);
            }
            var interrupted=new ShuoMotion();
            interrupted.basic(0,false,28.57);
            interrupted.accept(new ShuoViewPayload(DRAW,9,0,1),.1,.1,false);
            interrupted.accept(new ShuoViewPayload(SHEATHE,8,0,2),.2,0,true);
            for(int i=0;i<=48;i++) check(h,blade,sheath,interrupted.frame(.2+i/6.,true),left,.2+i/6.);
        }
    }
}
