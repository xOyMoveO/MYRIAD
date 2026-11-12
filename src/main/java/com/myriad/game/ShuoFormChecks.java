package com.myriad.game;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.shuo.*;
import com.myriad.network.ShuoViewPayload;
import com.myriad.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Executes actual dual-form skill paths, not stand-alone formula replicas. */
final class ShuoFormChecks {
    private record Fixture(ServerPlayer p,LivingEntity target,Vec3 end,int node,boolean drawn,List<ShuoViewPayload> cues,ShuoWorldVfxChecks.Observer observer){}
    static void start(GameTestHelper h){
        var all=new ArrayList<Fixture>();
        // Each ability gets both starting stances; all are separated vertically.
        for(int node=2;node<8;node++)for(boolean drawn:new boolean[]{false,true}){
            var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(65,80+all.size()*7,5)));
            var cues=new ArrayList<ShuoViewPayload>();var p=ShuoChecks.player(h,at,cues);
            var skill=FirstGroupSkills.node("shuo",node);var d=PlayerDataProvider.get(p);d.setSkillLevel(skill.getId(),1);
            if(drawn)ShuoCombat.draw(p,ShuoViewPayload.DRAW,9);
            var target=ShuoChecks.target(h,at.add(0,0,node==4?2.5:node==6&&drawn?8:2.5));
            var forward=new Vec3(0,0,1);
            Vec3 end=node==2?at.add(.4358899*3,0,-.9*3):node==4?at.add(forward.scale(drawn?4:7)):node==6?at.add(forward.scale(drawn?6:12)):at;
            var observer=ShuoWorldVfxChecks.observe(h,at.add(8,0,0));
            SkillSystem.cast(p,skill.slot());
            h.assertTrue(Math.abs(d.getMana()-(700-skill.getManaCost()))<.001&&d.getCooldown(skill.getId())==skill.getCooldownTicks(1),"Both stances pay the same one skill cost and cooldown");
            all.add(new Fixture(p,target,end,node,drawn,cues,observer));
        }
        // A brief control during the double cut must latch, even if gone at its due tick.
        var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(75,80,5)));
        var cancelPlayer=ShuoChecks.player(h,at,new ArrayList<>());ShuoCombat.draw(cancelPlayer,0,9);
        var cancelTarget=ShuoChecks.target(h,at.add(0,0,2.5));var doubleCut=FirstGroupSkills.node("shuo",5);
        PlayerDataProvider.get(cancelPlayer).setSkillLevel(doubleCut.getId(),1);SkillSystem.cast(cancelPlayer,2);
        // Fresh drawn W cannot produce a parry/recast window; switching stance cannot bypass its CD.
        boolean[] done={false};
        for(int n=1;n<=55;n++){int age=n;h.runAfterDelay(n,()->{
            if(done[0])return;
            try{
                if(age==1)PlayerDataProvider.get(cancelPlayer).setBuff("g1_stun",1);
                if(age==2)PlayerDataProvider.get(cancelPlayer).setBuff("g1_stun",0);
                AbilityRuntime.tick(cancelPlayer);
                for(var f:all){var p=f.p;var d=PlayerDataProvider.get(p);var skill=FirstGroupSkills.node("shuo",f.node);
                    if(f.node==7&&!f.drawn&&(age==6||age==13)){
                        h.assertTrue(skill.recastAvailable(AbilityRuntime.context(p,skill)),"Sheathed triple attack remains its original sequence after blade draws");SkillSystem.cast(p,3);
                    }
                    if(AbilityRuntime.moving(p))p.move(MoverType.SELF,AbilityTargeting.clampPrediction(p.position(),f.end,p.getDeltaMovement()));
                    AbilityRuntime.tick(p);
                    if(f.node==3&&f.drawn&&age==1){
                        h.assertTrue(!skill.recastAvailable(AbilityRuntime.context(p,skill))&&!ShuoCombat.parry(p,p.damageSources().mobAttack(f.target),com.myriad.combat.DamageCalc.Kind.PHYSICAL),"Drawn W is an offensive sweep, not a hidden guard");
                    }
                    if(f.node==3&&f.drawn&&age==4){
                        int cd=d.getCooldown(skill.getId());double mana=d.getMana();ShuoCombat.clear(p);SkillSystem.cast(p,1);
                        h.assertTrue(d.getCooldown(skill.getId())==cd&&d.getMana()==mana&&!skill.recastAvailable(AbilityRuntime.context(p,skill)),"Switching stance never gives a second cooldown or free recast");
                    }
                    if(f.node==5&&f.drawn&&age==1)h.assertTrue(Math.abs(f.target.getHealth()-920)<.05,"Drawn double cut first hit is immediate 80");
                    if(f.node==5&&f.drawn&&age==5)h.assertTrue(Math.abs(f.target.getHealth()-840)<.05,"Drawn double cut second hit arrives once at four ticks");
                    if(f.node==5&&!f.drawn&&age==8)h.assertTrue(f.target.getHealth()==1000,"Sheathed cone retains its nine-tick windup");
                    if(age==32){
                        ShuoWorldVfxChecks.form(h,f.observer,p,f.node,f.drawn);
                        float expected=switch(f.node){case 2->f.drawn?905:1000;case 3->f.drawn?890:1000;case 4->f.drawn?880:825;case 5->f.drawn?840:805;case 6->f.drawn?640:520;case 7->f.drawn?585:430;default->throw new IllegalStateException();};
                        h.assertTrue(Math.abs(f.target.getHealth()-expected)<.05,"Exact dual-form damage, node="+f.node+" drawn="+f.drawn+" expected="+expected+" actual="+f.target.getHealth());
                        if(f.node==2||f.node==4||f.node==6)h.assertTrue(p.position().distanceTo(f.end)<.001,"Stance-specific bounded motion distance, node="+f.node+" drawn="+f.drawn);
                        if(f.node==2||f.node==7||f.node==6&&!f.drawn)h.assertTrue(!ShuoCombat.drawn(p),"Resheathing forms finish sheathed");
                        if(f.node==4||f.node==5||f.node==6&&f.drawn)h.assertTrue(ShuoCombat.drawn(p),"Exposed forms retain the drawn blade");
                        h.assertTrue(Math.abs(d.getMana()-(700-skill.getManaCost()))<.001,"No follow-up pays mana twice");
                    }
                }
                if(age==32)h.assertTrue(Math.abs(cancelTarget.getHealth()-920)<.05,"Brief CC cancels the drawn second slash permanently");
                if(age==55){done[0]=true;cleanup(all,cancelPlayer,cancelTarget);System.out.println("[MYRIAD-SHUO-FORMS] PASS: 12 production starting forms, exact costs/damage/motion, locked manual combo, shared CD and brief-control follow-up cancellation");}
            }catch(Throwable e){done[0]=true;cleanup(all,cancelPlayer,cancelTarget);throw e;}
        });}
        for(int node=2;node<8;node++){
            var s=FirstGroupSkills.node("shuo",node);
            h.assertTrue(s.description().contains("【收鞘")&&s.description().contains("【出鞘"),"All six active modules document both forms");
            h.assertTrue(!ShuoForms.name(s.getId(),false,false).equals(ShuoForms.name(s.getId(),true,false)),"UI shows a distinct current form name");
            String evaluated=AbilityFormulaText.evaluate(s.description(),1,100);
            h.assertTrue(!evaluated.contains("%S")&&!evaluated.contains("(r－1)"),"Both form damage formulas evaluate in tooltips");
        }
    }
    private static void cleanup(List<Fixture> all,ServerPlayer p,LivingEntity t){
        all.forEach(f->{ShuoWorldVfxChecks.cleanup(f.observer);AbilityRuntime.clear(f.p);f.p.discard();f.target.discard();});AbilityRuntime.clear(p);p.discard();t.discard();
    }
}
