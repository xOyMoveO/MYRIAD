package com.myriad.game;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.SkillChargeSystem;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.network.SkillChargePayload;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

final class SkillChargeChecks {
    static void verify(GameTestHelper h,ServerPlayer p){
        var data=PlayerDataProvider.get(p);data.setCharacterId("yingxiao");
        double speed=p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        boolean[] fired={false};p.setDeltaMovement(1,.4,1);
        var c=AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",6));
        c.later(7,true,()->fired[0]=true);
        h.assertTrue(SkillChargeSystem.active(p)&&AbilityRuntime.channeling(p),"Windup is tracked on the server");
        h.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED)==0&&p.getDeltaMovement().equals(Vec3.ZERO),
                "Charging blocks walking and jump momentum without a teleport");
        com.myriad.combat.KnockbackBuffer.clear(p.getUUID());
        Vec3 forced=new Vec3(.4,.2,0);com.myriad.combat.KnockbackBuffer.apply(p,forced);
        SkillChargeSystem.beforeMove(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(p));
        h.assertTrue(p.getDeltaMovement().equals(forced),"Explicit buffered knockback is not erased by stationary casting");
        com.myriad.combat.KnockbackBuffer.clear(p.getUUID());p.setDeltaMovement(Vec3.ZERO);
        data.setBuff("g1_stun",4);AbilityRuntime.tick(p);
        h.assertTrue(!SkillChargeSystem.active(p)&&!AbilityRuntime.channeling(p)&&!fired[0],"Stun still cancels the real pending cast");
        h.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1E-8,"Interrupt removes only the charge speed modifier");
        data.setBuff("g1_stun",0);c.later(7,true,()->fired[0]=true);AbilityRuntime.clear(p);
        h.assertTrue(!SkillChargeSystem.active(p)&&!fired[0]&&data.getBuff("g1_channel")==0,"Reset releases movement and removes pending action");
        SkillChargeSystem.start(p,"g1_yingxiao_ua",7);data.setCharacterId("sulan");
        h.assertTrue(!SkillChargeSystem.active(p),"Role changes cannot carry a charge lock into another role");
        SkillChargeSystem.clear(p);data.setCharacterId("yingxiao");
        Vec3 start=p.position();
        c=AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",6));
        c.later(7,true,()->{});p.setPos(start.add(0,-1.3,0));AbilityRuntime.tick(p);
        h.assertTrue(AbilityRuntime.channeling(p)&&SkillChargeSystem.active(p),"Landing more than 0.8m below an airborne cast no longer cancels it");
        p.setPos(start.add(0,.4,0));AbilityRuntime.tick(p);
        h.assertTrue(AbilityRuntime.channeling(p),"Pure natural Y changes do not count as leaving the stationary footprint");
        p.setPos(start.add(1,.4,0));AbilityRuntime.tick(p);
        h.assertTrue(!AbilityRuntime.channeling(p)&&!SkillChargeSystem.active(p),"Real horizontal displacement still cancels stationary casting");
        AbilityRuntime.clear(p);p.setPos(start);com.myriad.combat.KnockbackBuffer.clear(p.getUUID());
        c=AbilityRuntime.context(p,FirstGroupSkills.node("yingxiao",6));c.later(7,true,()->{});
        com.myriad.combat.KnockbackBuffer.apply(p,new Vec3(0,.5,0));AbilityRuntime.tick(p);
        h.assertTrue(!AbilityRuntime.channeling(p),"A new explicit vertical knockup still interrupts before movement");
        AbilityRuntime.clear(p);com.myriad.combat.KnockbackBuffer.clear(p.getUUID());p.setDeltaMovement(Vec3.ZERO);p.setPos(start);
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{var payload=new SkillChargePayload("g1_yingxiao_ua",7,100);
            SkillChargePayload.CODEC.encode(buffer,payload);h.assertTrue(payload.equals(SkillChargePayload.CODEC.decode(buffer)),"Charge HUD start payload roundtrip");
        }finally{buffer.release();}
        System.out.println("[MYRIAD-CHARGE] PASS: start, motion lock, natural Y/landing preserved, horizontal displacement/new vertical knockup interrupt, stun, reset, role change, wire");
    }
}
