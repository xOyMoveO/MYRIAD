package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.player.ModShieldSystem;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Accepted HP, pure-shield and independent-pool transactions share the preserved three-tick cooldown. */
final class AcceptedDamageCooldownChecks {
    static void verify(GameTestHelper h,ServerPlayer actor,ServerPlayer target){
        var cast=AbilityRuntime.context(actor,FirstGroupSkills.node("yunsheng",2));
        target.damageCooldownTime=0;float hp=target.getHealth();
        h.assertTrue(cast.damage(target,100,DamageCalc.Kind.PHYSICAL)&&target.damageCooldownTime==3,"Actual vanilla HP loss enters the common three-tick cooldown");
        h.assertTrue(cast.damage(target,60,DamageCalc.Kind.PHYSICAL)&&Math.abs(hp-target.getHealth()-160)<.01,"A second accepted HP hit retains its full amount");
        ModShieldSystem.grant(target,"accepted-damage-fixture",1000,100);target.damageCooldownTime=0;hp=target.getHealth();
        h.assertTrue(cast.damage(target,100,DamageCalc.Kind.PHYSICAL)&&target.damageCooldownTime==3&&target.getHealth()==hp,"Pure shield absorption counts as accepted without inventing HP damage");
        h.assertTrue(cast.damage(target,60,DamageCalc.Kind.PHYSICAL)&&Math.abs(ModShieldSystem.value(target)-840)<.01&&target.getHealth()==hp,"Pure shield hits consume exactly 100+60, without vanilla lastHurt difference damage");
        ModShieldSystem.clearFor(target);
        JiarenManager.create(actor);
        var dummy=h.getLevel().getEntitiesOfClass(Villager.class,actor.getBoundingBox().inflate(5),v->JiarenManager.isJiaren(v.getUUID())).getFirst();
        try {
            dummy.setNoGravity(true);dummy.damageCooldownTime=0;float independent=JiarenManager.hp(dummy.getUUID());
            h.assertTrue(cast.damage(dummy,100,DamageCalc.Kind.PHYSICAL)&&dummy.damageCooldownTime==3,"Independent training HP accepts damage with the same cooldown");
            h.assertTrue(cast.damage(dummy,60,DamageCalc.Kind.PHYSICAL)&&Math.abs(independent-JiarenManager.hp(dummy.getUUID())-160)<.01&&dummy.getHealth()==dummy.getMaxHealth(),"Independent HP loses 100+60 while vanilla HP remains full");
        } finally {dummy.discard();}
        target.damageCooldownTime=17;target.setPermanentlyInvulnerable(true);hp=target.getHealth();
        h.assertTrue(!cast.damage(target,100,DamageCalc.Kind.PHYSICAL)&&target.getHealth()==hp&&target.damageCooldownTime==17,"An invulnerable rejected hit neither deals damage nor changes the cooldown");
        target.setPermanentlyInvulnerable(false);
        h.assertTrue(!cast.damage(target,0,DamageCalc.Kind.PHYSICAL)&&target.damageCooldownTime==17,"A zero-amount rejected hit leaves cooldown untouched");
        target.damageCooldownTime=0;
        System.out.println("[MYRIAD-ACCEPTED-DAMAGE] PASS: vanilla HP 100+60, pure shield 100+60, independent HP 100+60, invulnerable/zero rejection, exact cooldown 3/unchanged17");
    }
}
