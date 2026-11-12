package com.myriad.game;

import com.myriad.client.firstperson.*;
import com.myriad.client.particle.FlameSurfaceProjection;
import com.myriad.character.ability.*;
import com.myriad.character.skills.fenjue.rework.*;
import com.myriad.network.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import javax.imageio.ImageIO;
import static com.myriad.network.FlameSkillViewPayload.*;
import static com.myriad.character.ability.AbilityVfxMaterials.*;

final class FenjueMajorViewChecks {
    static void verify(GameTestHelper h){
        try(var in=FenjueMajorViewChecks.class.getResourceAsStream("/assets/myriad/textures/particle/vfx/fenjue_hd_fire.png")){
            var im=ImageIO.read(in);h.assertTrue(im.getWidth()==1312&&im.getHeight()==1312&&im.getColorModel().hasAlpha(),"Native 627px fire frames padded to 656px, never an upscale of old 256px fire");
            var hashes=new java.util.HashSet<Integer>();
            for(int i=0;i<4;i++){int hash=1,visible=0;
                for(int y=0;y<656;y++)for(int x=0;x<656;x++){
                    int pixel=im.getRGB(i%2*656+x,i/2*656+y);hash=31*hash+pixel;
                    if((pixel>>>24)>0){visible++;h.assertTrue(x>=14&&x<641&&y>=14&&y<641,"Safe atlas gutters, no frame bleed");}
                }h.assertTrue(visible>50000&&hashes.add(hash),"Four real detailed distinct RGBA frames");
            }
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
        h.assertTrue(HeavenFireSeaSkill.RANGE==14&&HeavenFireSeaSkill.MIN_HEIGHT==5,"Requested fire-sea range/apex");
        for(double dy:new double[]{-4,-2,0,2,4}){
            double height=HeavenFireSeaSkill.arcHeight(Vec3.ZERO,new Vec3(0,dy,14));
            h.assertTrue(dy*.5+height>=5,"Downhill endpoints cannot lower the midpoint below takeoff+5");
        }
        var m=new FlameSkillMotion();var idle=FlameDaoMotion.IDLE;
        h.assertTrue(m.accept(new FlameSkillViewPayload(BURST_READY,13,100,1),100,0,idle,false),"Burst confirmed ready accepted");
        var cross=m.frame(108);h.assertTrue(!cross.showBlade()&&cross.main().x()<-.7&&cross.off().x()<-.7,"Actual crossed arms while charging");
        h.assertTrue(m.accept(new FlameSkillViewPayload(BURST_FIRE,10,113,1),113,0,idle,false)&&m.frame(116).main().x()>.8,"Only server completion opens arms");
        h.assertTrue(!m.accept(new FlameSkillViewPayload(BURST_READY,13,100,1),114,1,idle,false),"Late ready cannot overwrite release");
        m.accept(new FlameSkillViewPayload(CANCEL,0,114,1),114,0,idle,false);
        h.assertTrue(!m.active(114)&&!m.accept(new FlameSkillViewPayload(BURST_FIRE,10,113,1),115,2,idle,false),"Cancelled gesture cannot revive");
        m.reset();m.accept(new FlameSkillViewPayload(SEA_FLIGHT,32,100,2),100,0,idle,false);
        var rise=m.frame(108);m.vertical(109,.1);h.assertTrue(m.frame(110).main().y()<0,"Ascent never prematurely enters the descending slam guard");
        m.vertical(110,-.1);h.assertTrue(m.frame(113).main().y()>.4,"Real negative Y motion raises both-handed slam guard");
        m.accept(new FlameSkillViewPayload(SEA_LAND,10,118,2),118,0,idle,false);
        h.assertTrue(m.frame(121).main().pitch()<-100,"Confirmed landing performs downward cut");
        for(int action=0;action<=5;action++)for(boolean left:new boolean[]{false,true})for(boolean slim:new boolean[]{false,true}){
            m.reset();m.accept(new FlameSkillViewPayload(action,20,100,3),100,0,idle,false);if(action==SEA_FLIGHT)m.vertical(109,-.1);
            for(int i=0;i<80;i++){
                var f=m.frame(100+i*.249);var wrist=FlameDaoRig.hand(f.main(),0,left);
                var blade=FlameSkillRig.main(f,left).mul(FlameSkillRig.blade(f,left));var off=FlameSkillRig.off(f,left);
                h.assertTrue(wrist.isFinite()&&blade.isFinite()&&off.isFinite()&&blade.determinant()>0,"Finite mirrored skill rigs");
                var palm=wrist.mul(FlameDaoRig.arm(slim,left)).transformPosition(new Vector3f((slim?.5f:1)/16*(left?1:-1),.5f,0));
                h.assertTrue(palm.distance(blade.transformPosition(new Vector3f()))<1E-6,"Skin palm still contacts the actual weapon handle");
                h.assertTrue(FlameSkillHandVfx.build(f,100+i*.249,left).size()<=32,"Bounded palm-local layered fire");
            }
        }
        var q=new AbilityVfxGeometry.Quad(new Vec3(-.2,2,-.2),new Vec3(.2,2,-.2),new Vec3(.2,2,.2),new Vec3(-.2,2,.2),0xFF8833,.5f,FENJUE_GROUND);
        var slab=FlameSurfaceProjection.project(q,v->.5,true,2);h.assertTrue(slab!=null&&Math.abs(slab.a().y-.525)<1E-8,"Ground fissures actually follow slab height with the explicit 0.025 decal clearance");
        h.assertTrue(FlameSurfaceProjection.project(q,v->Double.NaN,true,2)==null,"No ground effect floating over a void");
        h.assertTrue(FlameSurfaceProjection.project(q,v->v.x<0?0:2,true,2)==null,"No texture stretched over a cliff face");
        h.assertTrue(FlameMajorVfx.sceneLight(0,true)==0xF000F0&&FlameMajorVfx.sceneLight(15<<20,false)==0xF000F0,"Requested bright lava/fire is emissive, rather than dimly terrain-lit");
        var field=new AbilityVfxPayload(1,-1,2,6,AbilityVfxGeometry.SEAL,Vec3.ZERO,new Vec3(0,0,1),5,360,80,100,false);
        h.assertTrue(AbilityVfxGeometry.build(field,10,false).stream().filter(v->flameGround(v.material()))
                .allMatch(v->v.b().subtract(v.a()).cross(v.c().subtract(v.a())).y>0),"Ground fissure winding faces the player above the floor, not the underside");
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{var cue=new FlameSkillViewPayload(ARMOR,18,100,4);FlameSkillViewPayload.CODEC.encode(buf,cue);h.assertTrue(cue.equals(FlameSkillViewPayload.CODEC.decode(buf)),"Own-camera wire roundtrip");
            var charge=new SkillChargePayload("g1_fenjue_mb",13,100,.25f);SkillChargePayload.CODEC.encode(buf,charge);h.assertTrue(charge.equals(SkillChargePayload.CODEC.decode(buf)),"Mobile charge speed is sent to input/HUD client");
        }finally{buf.release();}
        System.out.println("[MYRIAD-FENJUE-MAJOR-VIEW] PASS: native HD alpha frames/gutters, four gestures, actual descent/landing, mirrored grip, token/cancel, grounded slabs/cliffs/voids, ambient light, mobile wire");
    }
}
