package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.shuo.*;
import com.myriad.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Production casts and spatial counterexamples for the 2026-11-12 small rework. */
final class ShuoMiniReworkChecks {
    private record Fixture(ServerPlayer p,LivingEntity t,LivingEntity miss,Vec3 origin,int node,Map<BlockPos,net.minecraft.world.level.block.state.BlockState> blocks){}
    static void start(GameTestHelper h){
        for(int n=0;n<=100;n++)h.assertTrue(Math.abs(WuShengSkill.executionScale(n,100)-(1+.25f*(1-n/100f)))<1E-6,"Finisher bonus is missing-health-scaled and capped, not percent-max-health damage");
        h.assertTrue(WuShengSkill.executionScale(-10,100)==1.25f&&WuShengSkill.executionScale(120,100)==1&&WuShengSkill.executionScale(Float.NaN,0)==1,"Invalid health cannot amplify finisher");
        var all=new ArrayList<Fixture>();
        for(int i=0;i<10;i++){
            var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(45,190+i*10,5)));
            var p=ShuoChecks.player(h,at,new ArrayList<>());int node=switch(i){case 0->2;case 1,7,8,9->5;case 2->7;case 3->6;case 4->4;default->3;};
            PlayerDataProvider.get(p).setSkillLevel(FirstGroupSkills.node("shuo",node).getId(),1);
            var t=ShuoChecks.target(h,at.add(0,0,i==9?8.2:i==1||i>=7?5.5:i==6?5:2.5));
            var miss=ShuoChecks.target(h,at.add(i==1?3:8,0,3.5));
            if(i==0)ShuoCombat.draw(p,0,9);if(i==3)t.setHealth(800);
            all.add(new Fixture(p,t,miss,at,node,new HashMap<>()));
        }
        boolean[] done={false};
        for(int n=1;n<=55;n++){int age=n;h.runAfterDelay(n,()->{
            if(done[0])return;
            try{
                for(int i=0;i<all.size();i++){
                    var f=all.get(i);var p=f.p;var skill=FirstGroupSkills.node("shuo",f.node);
                    if(age==2){
                        if(i==7)for(int x=-2;x<=2;x++)for(int y=0;y<=2;y++){
                            var pos=p.blockPosition().offset(x,y,3);f.blocks.put(pos,p.level().getBlockState(pos));
                            p.level().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        }
                        SkillSystem.cast(p,skill.slot());
                        if(i==5||i==6){
                            h.assertTrue(ShuoCombat.parry(p,p.damageSources().mobAttack(f.t),com.myriad.combat.DamageCalc.Kind.PHYSICAL),"Actual guard opens manual counter");
                            SkillSystem.cast(p,1);
                            h.assertTrue(Math.abs(ModShieldSystem.value(p)-(i==5?75:0))<.01,"Counter shield requires an effective hit, fixture="+i+" shield="+ModShieldSystem.value(p)+" hp="+f.t.getHealth());
                            h.assertTrue(Math.abs(f.t.getHealth()-(i==5?910:1000))<.01,"Counter reach excludes out-of-range target");
                        }
                    }
                    if(i==0&&age==5)f.t.setPos(f.origin.add(0,0,10));
                    if(i==1&&age==12)p.setYRot(90); // Second lane must not turn after the first cut.
                    if(i==8&&(age==12||age==13))PlayerDataProvider.get(p).setBuff("g1_stun",age==12?1:0);
                    if(i==2&&(age==9||age==21)){
                        p.setShiftKeyDown(age==9);h.assertTrue(skill.recastAvailable(AbilityRuntime.context(p,skill)),"Manual chase reuses the original paid combo");SkillSystem.cast(p,3);
                    }
                    if(i==4&&age==18)AbilityRuntime.clear(p); // Remove an already scheduled wound burst.
                    if(AbilityRuntime.moving(p)){
                        var end=i==0?f.origin.add(.4358899*3,0,-.9*3):f.origin.add(0,0,i==2?2:i==3?12:7);
                        p.move(MoverType.SELF,AbilityTargeting.clampPrediction(p.position(),end,p.getDeltaMovement()));
                    }
                    AbilityRuntime.tick(p);
                    if(i==1&&age==12)h.assertTrue(Math.abs(f.t.getHealth()-883)<.01&&f.miss.getHealth()==1000,"Seven-block line hits distant centre, not old wide-cone side targets");
                    if(age==35){
                        if(i==0)h.assertTrue(Math.abs(f.t.getHealth()-943)<.01,"Leaving the original retreat-cut position avoids its delayed 40%");
                        if(i==1)h.assertTrue(Math.abs(f.t.getHealth()-805)<.01&&f.miss.getHealth()==1000,"Second lane preserves locked direction and total damage budget");
                        if(i==2){
                            h.assertTrue(p.position().distanceTo(f.origin.add(0,0,2))<.001&&Math.abs(f.t.getHealth()-430)<.01,"Optional chase is bounded two-block motion, and each of three cuts hits once");
                            h.assertTrue(PlayerDataProvider.get(p).getMana()==550,"Manual chase never pays a second ultimate cost");
                        }
                        if(i==3)h.assertTrue(Math.abs(f.t.getHealth()-296)<.05,"Real marked finisher uses 20% missing health = 5% bonus: 800-504");
                        if(i==4)h.assertTrue(Math.abs(f.t.getHealth()-895)<.05,"Runtime cleanup removes delayed wound, retaining only actual initial hit");
                        if(i==7||i==9)h.assertTrue(f.t.getHealth()==1000&&!f.t.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),"Wall or beyond-end target receives neither line damage nor control, fixture="+i);
                        if(i==8)h.assertTrue(Math.abs(f.t.getHealth()-883)<.01,"Brief control after lane commitment permanently cancels its second hit");
                    }
                }
                if(age==55){done[0]=true;cleanup(all);System.out.println("[MYRIAD-SHUO-MINI-REWORK] PASS: fixed after-cut dodge, exact two-stage lane/side miss/locked aim, real guard hit-only shield, optional bounded combo chase, capped live finisher and delayed-wound cleanup");}
            }catch(Throwable e){done[0]=true;cleanup(all);throw e;}
        });}
    }
    private static void cleanup(List<Fixture> all){for(var f:all){f.blocks.forEach((pos,state)->f.p.level().setBlockAndUpdate(pos,state));AbilityRuntime.clear(f.p);ModShieldSystem.clearFor(f.p);f.p.discard();f.t.discard();f.miss.discard();}}
}
