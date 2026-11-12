package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Deterministic geometry checks; does not claim client packet-latency/visual acceptance. */
final class FirstGroupSpatialChecks {
    private FirstGroupSpatialChecks() { }
    static void verify(GameTestHelper h,ServerPlayer p,PlayerData d){
        var level=h.getLevel();BlockPos floor=h.absolutePos(new BlockPos(4,3,4));
        for(int x=-2;x<=2;x++)for(int z=-2;z<=7;z++){
            level.setBlockAndUpdate(floor.offset(x,0,z),Blocks.STONE.defaultBlockState());
            for(int y=1;y<=5;y++)level.setBlockAndUpdate(floor.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        Vec3 origin=Vec3.atBottomCenterOf(floor.above());p.setPos(origin);p.setYRot(0);p.setXRot(0);
        h.assertTrue(AbilityTargeting.ground(p,4,true)!=null,"Horizontal aiming projects onto a supported floor");
        var at=AbilityTargeting.ground(p,4,true);
        h.assertTrue(AbilityTargeting.inRange(origin,at,4),"Ray projection must not extend cast distance");
        BlockPos farWall=floor.offset(0,1,5);level.setBlockAndUpdate(farWall,Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(farWall.above(),Blocks.STONE.defaultBlockState());
        h.assertTrue(AbilityTargeting.ground(p,4,true)!=null,"A wall beyond maximum range cannot invalidate an in-range floor target");
        level.setBlockAndUpdate(farWall,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(farWall.above(),Blocks.AIR.defaultBlockState());
        BlockPos slab=floor.offset(0,0,2);level.setBlockAndUpdate(slab,Blocks.STONE_SLAB.defaultBlockState());
        Vec3 slabPoint=Vec3.atBottomCenterOf(slab).add(0,.525,0);
        h.assertTrue(AbilityTargeting.canStand(p,slabPoint),"Half slabs are supported landing surfaces");
        level.setBlockAndUpdate(slab.above(),Blocks.STONE.defaultBlockState());
        h.assertTrue(AbilityTargeting.supported(p,slabPoint)&&!AbilityTargeting.canStand(p,slabPoint),
                "A ground area may use support, but player landing additionally needs headroom");
        h.assertTrue(!FirstGroupSkills.node("tianxing",6).requiresStandingPoint()
                &&FirstGroupSkills.node("yingxiao",5).requiresStandingPoint()
                &&FirstGroupSkills.node("fenjue",6).requiresStandingPoint()
                &&FirstGroupSkills.node("xuanchen",4).requiresStandingPoint(),"Skills declare ground-area versus landing semantics");
        level.setBlockAndUpdate(slab,Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(slab.above(),Blocks.AIR.defaultBlockState());
        BlockPos wall=floor.offset(0,1,1);level.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
        h.assertTrue(!AbilityTargeting.clearSweep(p,new Vec3(0,0,3)),"Swept body collision catches a wall between clear endpoints");
        d.setCharacterId("yingxiao");d.setBuff("g1_root",0);d.setBuff("g1_stun",0);
        var c=AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",4));
        c.dash(new Vec3(0,0,6),2,1,(t,b)->{},()->{},false,false);
        h.assertTrue(p.getDeltaMovement().lengthSqr()==0&&!AbilityRuntime.moving(p),"Blocked first step never starts a dash");
        level.setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(wall.above(),Blocks.AIR.defaultBlockState());
        c.dash(new Vec3(0,0,5),6,1,(t,b)->{},()->{},false,false);
        h.assertTrue(p.position().equals(origin)&&p.getDeltaMovement().length()>0,"A valid dash sets motion and never teleports the player");
        AbilityRuntime.clear(p);
        h.assertTrue(p.getDeltaMovement().lengthSqr()==0&&!AbilityRuntime.moving(p),"Lifecycle cleanup releases both server and client motion ownership");
        Vec3 landing=origin.add(0,.025,4);
        h.assertTrue(AbilityTargeting.clearArc(p,landing,10,1.7),"An unobstructed arc is valid");
        BlockPos ceiling=floor.offset(0,3,2);level.setBlockAndUpdate(ceiling,Blocks.STONE.defaultBlockState());
        h.assertTrue(!AbilityTargeting.clearArc(p,landing,10,1.7),"Arc prevalidation rejects a ceiling before consuming cast resources");
        level.setBlockAndUpdate(ceiling,Blocks.AIR.defaultBlockState());
        p.setXRot(89);h.assertTrue(Math.abs(c.forward(5).y)<.00001&&Math.abs(c.forward(5).length()-5)<.00001,
                "Ground dashes keep their range when looking down");p.setXRot(0);
        Vec3 clipped=AbilityTargeting.clampPrediction(Vec3.ZERO,new Vec3(.1,0,0),new Vec3(1,0,0));
        h.assertTrue(clipped.equals(new Vec3(.1,0,0)),"Prediction clips the final velocity before overshooting");
        h.assertTrue(AbilityTargeting.clampPrediction(new Vec3(2,0,0),new Vec3(1,0,0),new Vec3(1,0,0)).equals(Vec3.ZERO),
                "An already-passed waypoint cannot send the player farther away");
        h.assertTrue(!AbilityTargeting.canStand(p,new Vec3(Double.NaN,0,0)),"Non-finite landing coordinates are rejected");
        System.out.println("[MYRIAD-G1-SPATIAL] PASS: reticle, slabs, headroom, swept walls, motion-only, cleanup, endpoint clamp");
    }
}
