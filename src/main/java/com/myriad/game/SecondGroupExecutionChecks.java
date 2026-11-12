package com.myriad.game;
import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real tick progression of three simultaneous independent fields and a stationary windup. */
final class SecondGroupExecutionChecks {
 static void run(GameTestHelper h,Runnable restore){
  List<ServerPlayer> casters=new ArrayList<>();
  var target=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new net.minecraft.core.BlockPos(4,6,4));
  target.setNoAi(true);target.setNoGravity(true);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);
  Vec3 center=target.position();
  for(String role:List.of("lingyuan","hanze","yunsheng")){
   var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
   p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
    net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
     @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
    };
   p.connection.markClientLoaded();p.setPos(center.add(0,0,-3));p.setYRot(0);p.setXRot(0);
   var data=PlayerDataProvider.get(p);data.setCharacterId(role);data.setSkillLevel("g2_"+role+"_ua",1);
   p.getAttribute(com.myriad.registry.ModAttributes.SPELL_POWER).setBaseValue(0);
   var c=AbilityRuntime.context(p,FirstGroupSkills.node(role,6));c.point=center;c.skill.release(c);c.skill.present(c);casters.add(p);
  }
  h.assertTrue(SkillChargeSystem.active(casters.get(2)),"Yunsheng's actual ultimate begins with stationary windup");
  boolean[] ended={false};
  Runnable cleanup=()->{casters.forEach(AbilityRuntime::clear);target.discard();restore.run();};
  for(int tick=1;tick<=140;tick++){
   final int elapsed=tick;
   h.runAfterDelay(tick,()->{
    if(ended[0])return;
    try{
     casters.forEach(AbilityRuntime::tick);
     if(elapsed==19)h.assertTrue(SkillChargeSystem.active(casters.get(2)),"Charge does not release early");
     if(elapsed==21)h.assertTrue(!SkillChargeSystem.active(casters.get(2)),"Natural completion releases the charge lock");
     if(elapsed==140){
      // S=0, rank=1: six sword pulses (55), six ice pulses (45), four chorus pulses (45).
      h.assertTrue(Math.abs(target.getHealth()-220)<.05,"Whole-window damage is 6x55 + 6x45 + 4x45 exactly once; actual HP="+target.getHealth());
      ended[0]=true;cleanup.run();System.out.println("[MYRIAD-G2-EXECUTION] PASS: real 140 ticks, sword 6 / ice 6 / chorus 4, exact aggregate damage, windup unlock");h.succeed();
     }
    }catch(Throwable failure){ended[0]=true;cleanup.run();throw failure;}
   });
  }
 }
}
