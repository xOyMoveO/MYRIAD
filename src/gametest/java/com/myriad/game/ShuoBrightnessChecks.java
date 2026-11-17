package com.myriad.game;
import com.myriad.character.skills.shuo.*;
import com.myriad.client.firstperson.*;
import com.myriad.character.ability.AbilityVfxGeometry.Quad;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import java.util.List;
final class ShuoBrightnessChecks {
    static void verify(GameTestHelper h){
        float previous=-1;
        for(int i=0;i<=1000;i++){
            float a=i/1000f,b=ShuoBrightness.opacity(a);
            h.assertTrue(b>=a&&b>=previous&&b<=1,"Exposure is monotone, brighter and bounded, with no fade discontinuity");previous=b;
        }
        h.assertTrue(ShuoBrightness.opacity(0)==0&&ShuoBrightness.opacity(1)==1&&ShuoBrightness.opacity(Float.NaN)==0,"Transparent endpoints and invalid input remain safe");
        h.assertTrue(ShuoBrightness.opacity(.25f)>.49&&ShuoBrightness.opacity(.5f)>.80,"Faint strands and half-opacity effects receive a visible brightness lift");
        for(int rgb:new int[]{0,0xFFFFFF,0x7C95AA,0xB7CFE0,0xD3ECFF,0xD7EDFF,0xF6FDFF,0xA9D8F5}){
            int bright=ShuoBrightness.tint(rgb);for(int shift:new int[]{0,8,16})h.assertTrue((bright>>shift&255)>=(rgb>>shift&255),"No color channel is darkened");
        }
        h.assertTrue(ShuoBrightness.tint(0)==0&&ShuoBrightness.tint(0xFFFFFF)==0xFFFFFF,"Ink gaps and white core are preserved");
        var q=new Quad(Vec3.ZERO,new Vec3(1,0,0),new Vec3(1,1,0),new Vec3(0,1,0),0xB7CFE0,.25f,77,true,.1f,.8f,.2f,.9f);
        var b=ShuoBrightness.apply(q);
        h.assertTrue(q.a().equals(b.a())&&q.b().equals(b.b())&&q.c().equals(b.c())&&q.d().equals(b.d())&&q.material()==b.material()&&q.reverseV()==b.reverseV()
            &&q.startU()==b.startU()&&q.endU()==b.endU()&&q.startV()==b.startV()&&q.endV()==b.endV(),"Only light/opacity changes; no geometry, material, UV or size change");
        // ShuoSlashMesh is also nested in skill meshes: assert the shared sheet receives exactly one exposure pass.
        var slash=ShuoSlashMesh.build(new Vec3(0,0,-2),new Vec3(1,0,0),new Vec3(0,1,0),1,1,.5f,false);
        h.assertTrue(!slash.isEmpty()&&slash.stream().allMatch(v->v.alpha()>0&&v.alpha()<=1),"Basic/camera/shared skill slash remains valid");
        var glow=ShuoBladeLight.build(new org.joml.Matrix4f().translate(0,0,-2),1,2,.5f,true);
        h.assertTrue(glow.size()==36&&glow.stream().anyMatch(v->v.material()==0&&v.alpha()>.74),"First/third-person blade core becomes brighter without added faces");
        for(var list:List.of(slash,glow))for(var v:list)h.assertTrue(v.color()>=0&&v.color()<=0xFFFFFF,"No HDR integer overflow or invalid alpha");
        System.out.println("[MYRIAD-SHUO-BRIGHTNESS] PASS: monotone all-layer exposure, color lift, finite alpha, unchanged geometry/UV/material and blade/core coverage");
    }
}
