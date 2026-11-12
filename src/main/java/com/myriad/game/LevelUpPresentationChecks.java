package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.network.AbilityVfxPayload;
import com.myriad.player.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** Actual XP transition + packets captured at real level-player recipients. Not a client test. */
final class LevelUpPresentationChecks {
    private record Viewer(ServerPlayer player,List<net.minecraft.network.protocol.Packet<?>> packets){
        long beams(){return packets.stream().filter(p->p instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket c
                &&c.payload() instanceof AbilityVfxPayload v&&LevelUpBeamGeometry.owns(v)).count();}
        long sounds(){return packets.stream().filter(p->p instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket).count();}
        void clear(){packets.clear();}
    }
    static void verify(GameTestHelper h){
        boolean mod=ModState.isEnabled(),training=TrainingMode.isEnabled();var viewers=new ArrayList<Viewer>();
        try{
            ModState.setEnabled(true);TrainingMode.setEnabled(true);
            var actor=viewer(h,Faction.XUANXIAO,0);viewers.add(actor);
            var ally=viewer(h,Faction.XUANXIAO,3);viewers.add(ally);
            var enemy=viewer(h,Faction.YOUMING,3);viewers.add(enemy);
            var unassigned=viewer(h,null,3);viewers.add(unassigned);
            var distant=viewer(h,Faction.XUANXIAO,90);viewers.add(distant);
            h.assertTrue(h.getLevel().players().contains(actor.player),"The subject participates in the actual level player list");
            var d=PlayerDataProvider.get(actor.player);d.setCharacterId("yingxiao");d.setLevel(1);d.setXp(0);d.refundAllSkillPointsForTraining();
            CharacterType.applyInitialAttributes(actor.player,CharacterType.YINGXIAO);
            com.myriad.character.rework.FirstGroupGrowth.initialize(actor.player,d);
            actor.player.setHealth(100);d.setMana(123);
            viewers.forEach(Viewer::clear);
            LevelSystem.addXp(actor.player,d,LevelSystem.xpToNext(1)+LevelSystem.xpToNext(2));
            h.assertTrue(d.getLevel()==3&&d.getSkillPoints()==3,"Multi-level XP transition retains exact skill point awards");
            h.assertTrue(actor.player.getHealth()==100&&d.getMana()==123,"Presentation never restores life or mana");
            h.assertTrue(actor.beams()==1&&ally.beams()==1&&actor.sounds()==2&&ally.sounds()==2,"One beam and two sound layers per teammate, not per crossed rank");
            for(var v:List.of(enemy,unassigned,distant))h.assertTrue(v.beams()==0&&v.sounds()==0,"Enemy/unassigned/out-of-range player receives no position or sound packet");
            viewers.forEach(Viewer::clear);LevelSystem.addXp(actor.player,d,1);
            h.assertTrue(viewers.stream().allMatch(v->v.beams()==0&&v.sounds()==0),"Ordinary XP without a new level makes no effect");
            d.setLevel(20);LevelSystem.addXp(actor.player,d,5000);
            h.assertTrue(viewers.stream().allMatch(v->v.beams()==0&&v.sounds()==0),"XP at level cap makes no false beam");
            ModState.setEnabled(false);d.setLevel(1);d.setXp(0);LevelSystem.addXp(actor.player,d,5000);
            h.assertTrue(d.getLevel()==1&&viewers.stream().allMatch(v->v.beams()==0&&v.sounds()==0),"Mod off is untouched");
            ModState.setEnabled(true);d.setFaction(null);viewers.forEach(Viewer::clear);LevelUpPresentation.play(actor.player);
            h.assertTrue(actor.beams()==1&&ally.beams()==0&&enemy.beams()==0,"Unassigned training actor can see own effect but never treats null factions as allies");
            for(Faction own:Faction.values())for(Faction other:Faction.values())
                h.assertTrue(LevelUpPresentation.teammate(false,own,other)==(own==other),"Both teams use the same audience rule");
            var recipe=LevelUpPresentation.recipe(actor.player);
            h.assertTrue(recipe.valid()&&recipe.life()==24&&recipe.anchor()==actor.player.getId(),"Existing payload is finite and follows the leveled actor");
            for(float age:new float[]{0,.5f,3,8,17,23.9f}){
                var high=AbilityVfxGeometry.build(recipe,age,false);var low=AbilityVfxGeometry.build(recipe,age,true);
                h.assertTrue(!high.isEmpty()&&high.size()<=192&&!low.isEmpty()&&low.size()<=80&&low.size()<=high.size(),"Finite full/low beam budgets");
                for(var q:high){
                    h.assertTrue(q.alpha()>0&&q.alpha()<=1,"Bounded translucent layers");
                    for(var v:List.of(q.a(),q.b(),q.c(),q.d()))
                        h.assertTrue(Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)
                                &&com.myriad.character.skills.yingxiao.rework.NightVfxGeometry.bounds(recipe).contains(v),"Finite beam/rings stay inside actual culling bounds");
                }
            }
            h.assertTrue(AbilityVfxGeometry.build(recipe,24,false).isEmpty(),"No pillar or particles survive expiry");
        }finally{
            for(var v:viewers){com.myriad.character.ability.AbilityRuntime.clear(v.player);
                h.getLevel().removePlayerImmediately(v.player,Entity.RemovalReason.DISCARDED);}
            ModState.setEnabled(mod);TrainingMode.setEnabled(training);
        }
        System.out.println("[MYRIAD-LEVEL-UP-BEAM] PASS: actual XP -> one beam/two sounds to self+ally only; enemy/unassigned/distant excluded, multi-rank/no false triggers, HP/mana preserved, follow/budgets/expiry");
    }
    private static Viewer viewer(GameTestHelper h,Faction faction,int offset){
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);var packets=new ArrayList<net.minecraft.network.protocol.Packet<?>>();
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){packets.add(packet);}
        };
        p.connection.markClientLoaded();p.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(3,4,3))).add(offset,0,0));
        PlayerDataProvider.get(p).setFaction(faction);h.getLevel().addNewPlayer(p);return new Viewer(p,packets);
    }
}
