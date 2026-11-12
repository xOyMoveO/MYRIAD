package com.myriad.game;

import com.myriad.character.skills.fenjue.rework.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class FlameSlashAimChecks {
    static void verify(GameTestHelper h){
        int directions=0;
        for(float yaw:new float[]{0,45,90,180,270,359})for(float pitch:new float[]{-90,-89.99f,-65,0,65,89.99f,90}){
            var look=Vec3.directionFromRotation(pitch,yaw).normalize();var eye=new Vec3(100,90,-100);
            var p=FlameSlashAim.recipe(1,eye,look,yaw,100);var frame=FlameSlashAim.basis(look,yaw);
            h.assertTrue(p.valid()&&p.from().equals(eye)&&p.to().subtract(p.from()).normalize().dot(look)>.99999,"Visual starts at eye and retains complete yaw/pitch, including vertical: yaw="+yaw+" pitch="+pitch+" valid="+p.valid()+" dot="+p.to().subtract(p.from()).normalize().dot(look));
            h.assertTrue(Math.abs(frame.side().dot(look))<1E-6&&Math.abs(frame.up().dot(look))<1E-6,"Pole-safe orthogonal view basis");
            for(int cut=0;cut<2;cut++){
                var sweep=FlameSlashAim.sweep(p,cut);
                for(double distance:new double[]{1,3,4.5,6.5}){
                    var hit=eye.add(look.scale(distance));
                    h.assertTrue(sweep.contains(new AABB(hit,hit).inflate(.3)),"Each stroke reaches the visible front beyond the old cutoff: yaw="+yaw+" pitch="+pitch+" cut="+cut+" d="+distance);
                }
                for(var excluded:java.util.List.of(eye.subtract(look.scale(3)),eye.add(frame.side().scale(4)),eye.add(look.scale(8.5))))
                    h.assertTrue(!sweep.contains(new AABB(excluded,excluded).inflate(.3)),"Rear, side and beyond painted arc are excluded");
                for(var point:sweep.rim())h.assertTrue(sweep.contains(new AABB(point,point).inflate(.01)),"Painted leading contour has no un-hittable gaps");
                var interior=sweep.origin().lerp(sweep.rim().get(18),.7);
                var normal=frame.forward().cross(FlameCrossSlashVfx.diagonal(p,cut)).normalize();
                var outside=interior.add(normal.scale(1));
                h.assertTrue(!sweep.contains(new AABB(outside,outside).inflate(.05)),"Thin stroke volume is not an oversized invisible cone");
                h.assertTrue(sweep.contains(new AABB(interior.add(normal.scale(-.8)),interior.add(normal.scale(.8))).inflate(.1)),"A body crossing the blade plane is hit even when its center is outside");
            }
            var full=FlameCrossSlashVfx.build(p,4.5f,false);var low=FlameCrossSlashVfx.build(p,4.5f,true);
            h.assertTrue(!full.isEmpty()&&full.size()<=192&&low.size()<=36,"Curved painted images fit 192/36 full/low quad budgets");
            for(var q:full)for(var v:java.util.List.of(q.a(),q.b(),q.c(),q.d()))
                h.assertTrue(v.subtract(eye).dot(look)>.3&&FlameVfxGeometry.bounds(p).contains(v),"Full pitch waves stay in front and within culling bounds");
            for(var q:full){
                double lead=q.b().subtract(q.a()).normalize().dot(look);
                double facing=Math.abs(q.b().subtract(q.a()).cross(q.d().subtract(q.a())).normalize().dot(look));
                h.assertTrue(lead>=-1E-6&&facing<1E-6,
                    "Every image is edge-on in local XY and bulges only forward, including straight up/down");
            }
            // Tiny camera pitch changes must not make vertical slashes flip by 180 degrees.
            var adjacent=FlameSlashAim.basis(Vec3.directionFromRotation(Math.clamp(pitch+.001f,-90,90),yaw),yaw);
            h.assertTrue(adjacent.side().dot(frame.side())>.9999,"No near-vertical roll snap");
            directions++;
        }
        var p=FlameSlashAim.recipe(1,Vec3.ZERO,new Vec3(0,0,1),0,100);
        h.assertTrue(FlameVfxGeometry.firstPersonOpacity(p,.42f)==1,"Eye-origin slash is not mistakenly faded as if its real geometry touched the camera");
        h.assertTrue(p.life()==14&&FlameCrossSlashVfx.build(p,0,false).isEmpty()&&!FlameCrossSlashVfx.build(p,.8f,false).isEmpty(),"Longer 14-tick appearance/expansion/tail clip grows smoothly from invisible");
        h.assertTrue(FlameCrossSlashVfx.build(p,14,false).isEmpty(),"Longer tail still ends exactly with the recipe");
        var gesture=new com.myriad.client.firstperson.FlameSlashMotion();
        h.assertTrue(gesture.accept(p,0,0,new com.myriad.client.firstperson.FlameDaoMotion().frame(0))&&gesture.duration()==8&&!gesture.active(8),"Longer VFX tail does not slow the 8-tick fast double-cut hand motion");
        for(int sky:new int[]{0,4,15})for(int block:new int[]{0,8,15}){
            int ambient=(sky<<20)|(block<<4),body=FlameCrossSlashVfx.sceneLight(ambient,0xFFFFFF),core=FlameCrossSlashVfx.sceneLight(ambient,0xFFD078);
            h.assertTrue(((body>>20)&15)==sky&&((core>>20)&15)==sky,"World sky lighting is preserved, never forced to fullbright");
            h.assertTrue(((body>>4)&15)==Math.max(10,block)&&((core>>4)&15)==Math.max(14,block),"Stronger painted body and warm inner image retain ambient sky lighting");
        }
        for(float age:new float[]{0.002f,.5f,1.8f,2.56f,3.6f,4.5f,7.8f,10,13.8f}){
            double before=energy(p,age-.001f),after=energy(p,age+.001f);
            h.assertTrue(Math.abs(after-before)<.03*Math.max(1,Math.max(before,after)),"No hard relative area/opacity jump at image reveal, flipbook transitions or fade");
        }
        for(double t=.1;t<13.9;t+=.1)for(int cut=0;cut<2;cut++){
            var w=FlameCrossSlashVfx.window(t,cut,14);
            h.assertTrue(w.head()>=w.tail()&&w.head()<=1&&w.tail()>=0&&w.opacity()>=0&&w.opacity()<=1,"Ordered finite moving head and eroding tail");
        }
        h.assertTrue(energy(p,.6f)<energy(p,2)&&energy(p,10)<energy(p,5),"Arc grows spatially, then erodes, not just full-sprite opacity");
        var axes=FlameSlashAim.basis(p.to().subtract(p.from()),p.angle());
        for(int cut=0;cut<2;cut++){
            var movement=head(p,cut,cut==0?2:5.6f).subtract(head(p,cut,cut==0?1:4.6f));
            h.assertTrue(movement.dot(axes.up())<-.4&&(cut==0?movement.dot(axes.side())<-.4:movement.dot(axes.side())>.4),
                "Leading edge matches the hand: upper-right to lower-left, then upper-left to lower-right");
        }
        h.assertTrue(FlameCrossSlashVfx.build(p,Float.NaN,false).isEmpty(),"Invalid visual age cannot create NaN geometry");
        h.assertTrue(FlameSlashAim.hitTick(1)-FlameSlashAim.hitTick(0)==4,"Two strokes are 0.2 seconds apart");
        System.out.println("[MYRIAD-FLAME-3D-AIM] PASS: "+directions+" complete yaw/pitch directions, both painted arc sweeps including 6.5-block hits, SAT edge/body/plane exclusions, stable pole roll and VFX budgets");
    }
    private static double energy(com.myriad.network.AbilityVfxPayload p,float age){
        return FlameCrossSlashVfx.build(p,age,false).stream().mapToDouble(q->q.b().subtract(q.a()).cross(q.d().subtract(q.a())).length()*q.alpha()).sum();
    }
    private static Vec3 head(com.myriad.network.AbilityVfxPayload p,int cut,float age){
        var w=FlameCrossSlashVfx.window(age,cut,p.life());double t=cut==0?w.head():1-w.head();
        var basis=FlameSlashAim.basis(p.to().subtract(p.from()),p.angle());double angle=Math.toRadians(FlameCrossSlashVfx.CUT_ANGLE);
        var diagonal=basis.side().scale(Math.cos(angle)).add(basis.up().scale((cut==0?1:-1)*Math.sin(angle)));
        var local=FlameCrossSlashVfx.localPoint(t,FlameCrossSlashVfx.sourceEdge(t)/512,p.radius());
        return diagonal.scale(local.x).add(basis.forward().scale(local.y));
    }
}
