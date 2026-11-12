package com.myriad.game;

import com.myriad.character.skills.fenjue.rework.FlameFissureMesh;
import com.myriad.client.particle.FlameSurfaceProjection;
import com.myriad.client.particle.FlameTransparentLayer;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Regression for the reported coplanar core/edge stippling, including projection and pipeline state. */
final class FlameFissureChecks {
    static void verify(GameTestHelper h){
        int cases=0;
        for(int seed=0;seed<16;seed++)for(double angle:new double[]{0,.37,1.6,3.8})for(boolean low:new boolean[]{false,true}){
            var points=List.of(rotate(new Vec3(0,2,0),angle),rotate(new Vec3(.14,2,1),angle),
                    rotate(new Vec3(-.1,2,2),angle),rotate(new Vec3(.25,2,3),angle));
            var mesh=FlameFissureMesh.build(points,.16,.8f,low,seed);
            h.assertTrue(mesh.size()==(low?3:9),"Each branch is one tessellated surface with three adjacent bands, not a wide quad plus a core overlay");
            var flat=mesh.stream().map(q->FlameSurfaceProjection.project(q,v->.5,true,2)).toList();
            for(int i=0;i<flat.size();i++){
                var q=flat.get(i);h.assertTrue(q!=null&&q.b().subtract(q.a()).cross(q.c().subtract(q.a())).y>0,"Single surface remains up-facing after slab projection");
                for(int j=i+1;j<flat.size();j++)h.assertTrue(overlap(q,flat.get(j))<1E-9,"Projected core and edge have zero overlapping area, including mitered joints");
            }
            int bands=low?1:3;
            for(int i=0;i<2;i++)for(int band=0;band<bands;band++){
                var a=flat.get(i*bands+band);var b=flat.get((i+1)*bands+band);
                h.assertTrue(a.d().equals(b.a())&&a.c().equals(b.b()),"Adjacent segments share the exact same joint: no overlapping rectangles or cracks at bends");
            }
            h.assertTrue(mesh.get(0).a().distanceTo(mesh.get(0).b())<mesh.get(bands).a().distanceTo(mesh.get(bands).b()),"Fissure ends taper, rather than rectangular orange tape");cases++;
        }
        var base=RenderPipelines.TRANSLUCENT_PARTICLE;var flame=FlameTransparentLayer.LAYER.pipeline();
        h.assertTrue(base.getDepthStencilState().writeDepth()&&!flame.getDepthStencilState().writeDepth(),"Only Fenjue's new layered sheets stop writing depth; vanilla pipeline stays unchanged");
        h.assertTrue(flame.getDepthStencilState().depthTest()==base.getDepthStencilState().depthTest(),"Depth testing is retained: fire never becomes an x-ray overlay through terrain");
        var ground=FlameTransparentLayer.GROUND.pipeline();
        h.assertTrue(!ground.getDepthStencilState().writeDepth()&&ground.getDepthStencilState().depthTest()==base.getDepthStencilState().depthTest()
                &&ground.getDepthStencilState().depthBiasConstant()>0&&flame.getDepthStencilState().depthBiasConstant()==0,
                "Only ground decals receive a positive reverse-Z raster bias against terrain; vertical fire keeps its original depth");
        h.assertTrue(flame.getVertexShader().equals(base.getVertexShader())&&flame.getFragmentShader().equals(base.getFragmentShader())
                &&flame.getBindGroupLayouts().equals(base.getBindGroupLayouts())&&flame.getShaderDefines().equals(base.getShaderDefines())
                &&Arrays.equals(flame.getVertexFormatBindings(),base.getVertexFormatBindings())
                &&Arrays.equals(flame.getColorTargetStates(),base.getColorTargetStates())&&flame.isCull()==base.isCull(),"Native shader/atlas/layout/blending/culling are preserved");
        System.out.println("[MYRIAD-FLAME-FISSURE] PASS: "+cases+" seed/heading/LOD cases, zero coplanar colour overlap after projection, shared tapered joints; depth-tested/no-depth-write role layer, vanilla shader and pipeline untouched");
    }
    private static Vec3 rotate(Vec3 p,double a){return new Vec3(p.x*Math.cos(a)-p.z*Math.sin(a),p.y,p.x*Math.sin(a)+p.z*Math.cos(a));}
    private static double cross(Vec3 a,Vec3 b,Vec3 p){return (b.x-a.x)*(p.z-a.z)-(b.z-a.z)*(p.x-a.x);}
    private static double overlap(Quad a,Quad b){
        var polygon=new ArrayList<>(List.of(a.a(),a.b(),a.c(),a.d()));var clip=List.of(b.d(),b.c(),b.b(),b.a());
        for(int edge=0;edge<4&&!polygon.isEmpty();edge++){
            var c=clip.get(edge);var d=clip.get((edge+1)%4);var input=polygon;polygon=new ArrayList<>();
            for(int i=0;i<input.size();i++){
                var p=input.get(i);var q=input.get((i+1)%input.size());double from=cross(c,d,p),to=cross(c,d,q);
                boolean pin=from>=-1E-12,qin=to>=-1E-12;
                if(pin)polygon.add(p);if(pin!=qin&&Math.abs(from-to)>1E-14)polygon.add(p.lerp(q,from/(from-to)));
            }
        }
        double area=0;for(int i=0;i<polygon.size();i++){var p=polygon.get(i);var q=polygon.get((i+1)%polygon.size());area+=p.x*q.z-p.z*q.x;}
        return Math.abs(area)*.5;
    }
}
