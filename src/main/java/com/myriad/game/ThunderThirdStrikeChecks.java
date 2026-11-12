package com.myriad.game;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import java.util.*;

/** Real post-hit passive dispatch and actual health loss, not a duplicate formula-only test. */
final class ThunderThirdStrikeChecks {
    static void verify(GameTestHelper h){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),connection,p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };p.connection.markClientLoaded();p.setNoGravity(true);
        var at=net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(8,12,4)));
        p.setPos(at);var d=PlayerDataProvider.get(p);d.setCharacterId("tianxing");d.setLevel(20);
        p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(100);
        var targets=new ArrayList<LivingEntity>();
        try{
            for(int i=0;i<4;i++){
                // Add at the final section: relocating a fresh entity has not updated the query index on tick 0.
                var pig=h.spawn(EntityTypes.PIG,new BlockPos(8+(i==0?0:i-2),12,8+(i==0?0:1)));
                pig.setNoAi(true);pig.setNoGravity(true);pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);targets.add(pig);
            }
            var skill=FirstGroupSkills.node("tianxing",0);var main=targets.getFirst();
            for(int rank=1;rank<=5;rank++)for(float strength:new float[]{40,125}){
                AbilityRuntime.clear(p);d.setSkillLevel(skill.getId(),rank);p.getAttribute(ModAttributes.SPELL_POWER).setBaseValue(strength);
                targets.forEach(t->{t.setHealth(1000);t.invulnerableTime=0;});
                AbilityRuntime.hit(p,main,1,false,"skill",true,0);AbilityRuntime.hit(p,main,1,true,"derived",false,0);
                h.assertTrue(AbilityRuntime.context(p,skill).memory().count==0,"Non-basic and derived damage do not count");
                hit(p,main);hit(p,main);h.assertTrue(main.getHealth()==1000,"No bonus on first two successful basics");hit(p,main);
                float expected=4*(.06f+.02f*(rank-1))*strength;
                h.assertTrue(Math.abs(1000-main.getHealth()-expected)<.001,"Exactly 4x old true damage at rank="+rank+" S="+strength+" actual="+(1000-main.getHealth()));
                int splashes=0;float splash=20+8*(rank-1)+.2f*strength;
                for(int i=1;i<4;i++){float loss=1000-targets.get(i).getHealth();if(loss>0){splashes++;h.assertTrue(Math.abs(loss-splash)<.001,"Lightning splash is unchanged and not critical");}}
                h.assertTrue(splashes==2&&AbilityRuntime.context(p,skill).memory().count==0,"Exactly two splashes, no recursive passive stacks: splashes="+splashes+" count="+AbilityRuntime.context(p,skill).memory().count);
                String value=Math.abs(expected-Math.rint(expected))<.001?String.format(Locale.ROOT,"%.0f",expected):String.format(Locale.ROOT,"%.1f",expected);
                h.assertTrue(AbilityFormulaText.evaluate(skill.description(),rank,strength).startsWith("同一目标第三次成功普攻追加"+value+"真实伤害"),"Tooltip resolves current true damage");
            }
            AbilityRuntime.clear(p);hit(p,main);hit(p,main);
            var memory=AbilityRuntime.context(p,skill).memory();memory.last-=81;hit(p,main);
            h.assertTrue(memory.count==1,"Four-second miss timeout restarts count");
            hit(p,targets.get(1));h.assertTrue(memory.count==1,"Switching target restarts count");
            System.out.println("[MYRIAD-THUNDER-THIRDSTRIKE] PASS: actual post-hit dispatch/health, all 5 ranks/two S values, exact x4 TRUE, unchanged two-target magic splash, no crit/recursion, timeout/switch/UI");
        }finally{AbilityRuntime.clear(p);targets.forEach(Entity::discard);p.discard();}
    }
    private static void hit(ServerPlayer p,LivingEntity target){AbilityRuntime.hit(p,target,1,true,"",true,0);}
}
