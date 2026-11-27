package com.myriad.client.firstperson;

import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import com.myriad.client.platform.HandRenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

/** All attachment positions come from the same item transform used to draw the current blade. */
final class MeleeBladeLight {
    static final AttackTrailMesh.Style SILVER = new AttackTrailMesh.Style(
            LINGYUAN_PIXEL_STREAM,LINGYUAN_PIXEL_SLASH,0xAAA3EC,0xDDF6FF,.60f,.009);
    static final AttackTrailMesh.Style COPPER = new AttackTrailMesh.Style(
            WAR_HD_RIBBON,WAR_HD_EDGE,0x982D29,0xFFD794,.70f,.015);
    static final AttackTrailMesh.Style MOON = new AttackTrailMesh.Style(
            SHUO_STREAM,SHUO_SLASH,0x829CC8,0xF0FAFF,.66f,.012);
    private static final AttackTrailMesh.Cell CELL = new AttackTrailMesh.Cell(.005f,.245f,.005f,.995f);
    private MeleeBladeLight() {}

    static List<Quad> swept(double now,double start,double from,double end,AttackTrailMesh.Style style,
                            Vector3f root,Vector3f tip,DoubleFunction<Matrix4f> weapon) {
        if (!Double.isFinite(start) || now<=start+from || now>=start+end+AttackTrailMesh.LIFE) return List.of();
        double duration=end+AttackTrailMesh.LIFE;
        var slices=AttackTrailMesh.samples(now,start,duration,from/duration,end/duration,
                tick->AttackTrailMesh.slice(weapon.apply(tick),root,tip,tick));
        return AttackTrailMesh.build(slices,now,style,CELL,CELL);
    }

    static void needle(HandRenderContext event,Vector3f tip,int side,double age) {
        var at=new Vec3(tip.x,tip.y,tip.z);
        var quads=new ArrayList<Quad>();
        line(quads,at,at.add(.033*side,-.011,.19),.005,0xE4F8FF,Math.min(1,(age-.35)*3)*(2.4-age)/2.05);
        AttackTrailRenderer.submit(event,quads);
    }

    static void sparks(HandRenderContext event,Vector3f head,int side,double age) {
        var origin=new Vec3(head.x,head.y,head.z);
        var quads=new ArrayList<Quad>();
        for (int i=0;i<4;i++) {
            double angle=.38+i*1.72;
            var ray=new Vec3(side*Math.cos(angle),Math.sin(angle),-.25).scale(.038);
            line(quads,origin.add(ray.scale(age)),origin.add(ray.scale(age+1.4)),.007,
                    i%2==0?0xFFBD64:0xFFF1C3,1-age/2.5);
        }
        AttackTrailRenderer.submit(event,quads);
    }

    private static void line(List<Quad> out,Vec3 a,Vec3 b,double width,int color,double alpha) {
        var side=b.subtract(a).cross(new Vec3(0,0,1)).normalize().scale(width);
        var q=new Quad(a.subtract(side),b.subtract(side),b.add(side),a.add(side),
                color,(float)alpha,0,false,.45f,.55f,.45f,.55f);
        out.add(q);
        out.add(new Quad(q.d(),q.c(),q.b(),q.a(),color,(float)alpha,0,true,.45f,.55f,.45f,.55f));
    }
}
