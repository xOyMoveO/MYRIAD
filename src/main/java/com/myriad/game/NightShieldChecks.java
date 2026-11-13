package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.SkillSystem;
import com.myriad.character.ability.*;
import com.myriad.character.rework.*;
import com.myriad.combat.SilenceSystem;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import java.util.*;

/** Calls the actual public cast entry, including payment, selected branch and control gates. */
final class NightShieldChecks {
    static void verify(GameTestHelper h){
        boolean mod=ModState.isEnabled(),training=TrainingMode.isEnabled(),noCd=TrainingMode.isNoCooldown(),noMana=TrainingMode.isNoMana();
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        p.connection.markClientLoaded();
        var d=PlayerDataProvider.get(p);var skill=FirstGroupSkills.node("yingxiao",7);
        try{
            ModState.setEnabled(true);TrainingMode.setEnabled(true);TrainingMode.setNoCooldown(false);TrainingMode.setNoMana(false);
            d.setCharacterId("yingxiao");d.setLevel(20);CharacterType.applyInitialAttributes(p,CharacterType.YINGXIAO);
            FirstGroupGrowth.initialize(p,d);
            h.assertTrue(skill.canCastWhileControlled()&&FirstGroupSkills.ROLES.stream().flatMap(r->FirstGroupSkills.candidates(r).stream())
                    .filter(AbilitySkill::canCastWhileControlled).count()==1,"Only Night shield opts into controlled casting");
            p.getAttribute(ModAttributes.COOLDOWN_REDUCTION).removeModifiers();p.getAttribute(ModAttributes.COOLDOWN_REDUCTION).setBaseValue(0);
            for(int rank=1;rank<=3;rank++){
                AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);resetRanks(d);d.setSkillLevel(skill.getId(),rank);d.setMana(700);d.setCooldown(skill.getId(),0);
                p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(30+rank*40);
                var context=AbilityRuntime.context(p,skill);
                float expected=context.value(180,70,.9f)*1.7f;
                // The slow modifier and a beneficial haste use different sources.
                AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",2)).slow(p,.4f,60);
                AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",3)).haste(.1f,60);
                control(p);
                p.setDeltaMovement(.4,.6,.3);
                p.addEffect(new MobEffectInstance(MobEffects.POISON,80,0));
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION,80,0));
                ModShieldSystem.grant(p,"other_shield",50,100);
                double mana=d.getMana();
                SkillSystem.cast(p,CharacterType.SLOT_R);
                h.assertTrue(d.getBuff("g1_stun")==0&&d.getBuff("g1_root")==0&&!SilenceSystem.isSilenced(p),"Public slot entry casts while stunned/rooted/silenced and cleanses immediately");
                for(var effect:List.of(MobEffects.SLOWNESS,MobEffects.LEVITATION,MobEffects.BLINDNESS,MobEffects.DARKNESS,MobEffects.MINING_FATIGUE))
                    h.assertTrue(!p.hasEffect(effect),"Existing crowd control potion removed");
                h.assertTrue(!AbilityRuntime.controlled(p)&&p.getAttributeValue(Attributes.MOVEMENT_SPEED)>p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(),"Slow removed, positive haste preserved and new haste applied");
                h.assertTrue(p.getDeltaMovement().lengthSqr()==0,"Existing upward/forced momentum stops without teleporting");
                h.assertTrue(p.hasEffect(MobEffects.POISON)&&p.hasEffect(MobEffects.REGENERATION),"DoT and unrelated positive buffs are not erased");
                h.assertTrue(Math.abs(ModShieldSystem.value(p)-50-expected)<.001,"Exact x1.7 full shield formula at every rank/strength; no multiplication of other shields");
                h.assertTrue(Math.abs(d.getMana()-(mana-150))<.01&&d.getCooldown(skill.getId())==skill.getCooldownTicks(rank),"One normal mana payment and unchanged rank cooldown");
                h.assertTrue(AbilityFormulaText.evaluate(skill.description(),rank,context.strength)
                        .contains(String.format(java.util.Locale.ROOT,"%.1f",expected)),"Tooltip resolves the updated actual shield amount");
                // A second, invalid request cannot cleanse for free while on cooldown.
                AbilityRuntime.clear(p);d.setBuff("g1_stun",40);double remainingMana=d.getMana();float shield=ModShieldSystem.value(p);
                SkillSystem.cast(p,CharacterType.SLOT_R);
                h.assertTrue(d.getBuff("g1_stun")==40&&d.getMana()==remainingMana&&ModShieldSystem.value(p)==shield,"Cooldown failure cannot cleanse, spend twice or stack shields");
                p.removeEffect(MobEffects.POISON);p.removeEffect(MobEffects.REGENERATION);
            }
            // A control-break is reactive even immediately after another accepted skill.
            AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);resetRanks(d);d.setSkillLevel(skill.getId(),1);
            var dagger=FirstGroupSkills.node("yingxiao",2);d.setSkillLevel(dagger.getId(),1);d.setCooldown(dagger.getId(),0);
            d.setCooldown(skill.getId(),0);d.setMana(700);SkillSystem.cast(p,CharacterType.SLOT_Q);
            h.assertTrue(d.getCooldown(dagger.getId())>0,"Real preceding skill is committed");
            d.setBuff("g1_stun",30);SkillSystem.cast(p,CharacterType.SLOT_R);
            h.assertTrue(d.getBuff("g1_stun")==0&&ModShieldSystem.value(p)>0,"Control-break is not delayed by the generic three-tick cast spacing");
            // Reset the shared anti-spam timer, then test independent failed request paths.
            AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);d.setCooldown(skill.getId(),0);d.setMana(0);d.setBuff("g1_stun",40);
            SkillSystem.cast(p,CharacterType.SLOT_R);
            h.assertTrue(d.getBuff("g1_stun")==40&&d.getCooldown(skill.getId())==0&&ModShieldSystem.value(p)==0,"Insufficient mana never cleanses");
            d.setMana(700);resetRanks(d);d.setSkillLevel(FirstGroupSkills.node("yingxiao",6).getId(),1);
            AbilityRuntime.request(p,d,skill);
            h.assertTrue(d.getBuff("g1_stun")==40&&ModShieldSystem.value(p)==0&&d.getMana()==700,"Cannot spoof the unselected ultimate branch");
            resetRanks(d);d.setSkillLevel(FirstGroupSkills.node("yingxiao",2).getId(),1);
            d.setCooldown(FirstGroupSkills.node("yingxiao",2).getId(),0);
            SkillSystem.cast(p,CharacterType.SLOT_Q);
            h.assertTrue(d.getCooldown(FirstGroupSkills.node("yingxiao",2).getId())==0&&d.getMana()==700,"Other skills remain blocked by stun");
            d.setBuff("g1_stun",0);silence(p);SkillSystem.cast(p,CharacterType.SLOT_Q);
            h.assertTrue(d.getMana()==700&&SilenceSystem.isSilenced(p),"Other skills remain blocked by silence");
            resetRanks(d);d.setSkillLevel(skill.getId(),1);ModState.setEnabled(false);SkillSystem.cast(p,CharacterType.SLOT_R);
            h.assertTrue(SilenceSystem.isSilenced(p)&&ModShieldSystem.value(p)==0,"Mod-off request does not cleanse or grant a shield");
            // A successful cleanse does not grant control immunity afterward.
            ModState.setEnabled(true);SilenceSystem.cleanse(p);d.setBuff("g1_stun",30);
            h.assertTrue(AbilityRuntime.controlled(p),"New control can apply normally after the one-time cleanse");
        }finally{
            SilenceSystem.cleanse(p);AbilityRuntime.clear(p);ModShieldSystem.clearFor(p);p.discard();
            ModState.setEnabled(mod);TrainingMode.setEnabled(training);TrainingMode.setNoCooldown(noCd);TrainingMode.setNoMana(noMana);
        }
        System.out.println("[MYRIAD-NIGHT-SHIELD] PASS: real public R cast under CC, immediate cleanse; exact x1.7 ranks/strength and tooltip, other shield/buff preservation, normal cost/CD, invalid requests and no immunity");
    }
    private static void control(ServerPlayer p){
        var d=PlayerDataProvider.get(p);d.setBuff("g1_stun",60);d.setBuff("g1_root",60);silence(p);
        for(var effect:List.of(MobEffects.SLOWNESS,MobEffects.LEVITATION,MobEffects.BLINDNESS,MobEffects.DARKNESS,MobEffects.MINING_FATIGUE))
            p.addEffect(new MobEffectInstance(effect,60,1));
    }
    private static void resetRanks(PlayerData d){for(String id:d.getSkillLevels().keySet())d.setSkillLevel(id,0);}
    private static void silence(ServerPlayer p) { SilenceSystem.apply(p, 60); }
}
