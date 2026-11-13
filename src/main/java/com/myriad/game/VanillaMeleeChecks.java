package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.combat.*;
import com.myriad.player.*;
import com.myriad.registry.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Calls actual Player.attack and the real NeoForge events, not a replacement cone. */
final class VanillaMeleeChecks {
    static void verify(GameTestHelper h){
        boolean mod=ModState.isEnabled(),training=TrainingMode.isEnabled();
        var p=(ServerPlayer)h.makeMockServerPlayer(GameType.SURVIVAL);
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(h.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p,
                net.minecraft.server.network.CommonListenerCookie.createInitial(p.getGameProfile(),false)){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        p.connection.markClientLoaded();
        var target=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new net.minecraft.core.BlockPos(3,6,3));
        var neighbour=h.spawn(net.minecraft.world.entity.EntityTypes.PIG,new net.minecraft.core.BlockPos(3,6,4));
        try{
            ModState.setEnabled(true);TrainingMode.setEnabled(true);
            var d=PlayerDataProvider.get(p);d.setCharacterId("lingyuan");
            CharacterType.applyInitialAttributes(p,CharacterType.LINGYUAN);
            com.myriad.character.rework.FirstGroupGrowth.initialize(p,d);
            var weapon=new net.minecraft.world.item.ItemStack(ModItems.LINGYUAN_SWORD.get());
            weapon.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,net.minecraft.util.Unit.INSTANCE);
            p.setItemSlot(EquipmentSlot.MAINHAND,weapon);
            p.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(1);
            p.getAttribute(ModAttributes.CRIT_CHANCE).removeModifiers();p.getAttribute(ModAttributes.CRIT_CHANCE).setBaseValue(0);
            p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(2,6,3))));p.setYRot(-90);p.setXRot(0);
            p.setNoGravity(true);p.fallDistance=0;
            for(var pig:java.util.List.of(target,neighbour)){
                pig.setNoAi(true);pig.setNoGravity(true);pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);
            }
            // Test-only controlled ticker. Gameplay never writes or fakes vanilla recovery.
            var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);
            ticker.setInt(p,200);target.invulnerableTime=0;
            p.attack(target);
            float full=1000-target.getHealth();
            h.assertTrue(full>55&&full<75,"Real vanilla Player.attack immediately deals the role's fully charged A damage, not a post-reset weak hit");
            h.assertTrue(neighbour.getHealth()==1000,"Point-selected attack does not use the retired forward cone");
            h.assertTrue(BasicAttackSystem.cooldownRemaining(p)==20&&BasicAttackSystem.cooldownTotal(p)==20,"Server HUD recovery uses the real vanilla ticker and speed");
            target.setHealth(1000);target.invulnerableTime=0;ticker.setInt(p,0);
            p.attack(target);
            h.assertTrue(target.getHealth()==1000&&ticker.getInt(p)==0,"Incomplete recovery causes no weak attack and does not reset the charging clock");
            ticker.setInt(p,15);p.attack(target);
            h.assertTrue(target.getHealth()==1000&&ticker.getInt(p)==15,"The old 75% window is not an attack and does not postpone full recovery");
            target.setHealth(1000);target.invulnerableTime=0;ticker.setInt(p,200);
            BasicAttackSystem.tryAttack(p,target.getId());
            h.assertTrue(target.getHealth()==1000,"Stale/spoofed custom melee packets cannot deal a second attack");
            d.setBuff("g1_stun",20);p.attack(target);
            h.assertTrue(target.getHealth()==1000,"Vanilla entry still enforces stun");d.setBuff("g1_stun",0);
            d.setPlacementType(1);p.attack(target);
            h.assertTrue(target.getHealth()==1000,"Placement click cannot also attack");d.setPlacementType(0);
            h.assertTrue(!VanillaMeleeAttack.prepare(p,p),"No self-hit");
            for(var role:new CharacterType[]{CharacterType.XUANCHEN,CharacterType.HANZE,CharacterType.QINGLUO,CharacterType.YUNSHENG}){
                h.assertTrue(!VanillaMeleeAttack.usesVanilla(role),"Dedicated ranged attacks remain custom: "+role);
            }
            d.setCharacterId("xuanchen");var remoteAttack=new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(p,target);
            PlayerDataProvider.onPlayerAttack(remoteAttack);h.assertTrue(remoteAttack.isCanceled(),"Ranged mage cannot bypass shooting with a vanilla melee packet");
            ModState.setEnabled(false);var vanilla=new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(p,target);
            PlayerDataProvider.onPlayerAttack(vanilla);h.assertTrue(!vanilla.isCanceled(),"Mod off returns untouched vanilla behavior");
            for(int total:new int[]{1,4,20,29,100}){
                h.assertTrue(!BasicAttackSystem.isEarlyReady(99,100,total)&&BasicAttackSystem.isEarlyReady(100,100,total),"All dedicated ranged attacks wait for complete cooldown");
                h.assertTrue(BasicAttackSystem.earlyReadyRemaining(total)==0&&BasicAttackSystem.recoveryDamageMultiplier(99,100)==1,"No remaining weak window or reduced release multiplier");
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        finally{
            AbilityRuntime.clear(p);BasicAttackSystem.clearFor(p.getUUID());
            target.discard();neighbour.discard();p.discard();ModState.setEnabled(mod);TrainingMode.setEnabled(training);
        }
        System.out.println("[MYRIAD-VANILLA-MELEE] PASS: real Player.attack, full charge only / no weak hit, point target, vanilla cooldown, old packet rejected, stun/placement, ranged full-CD routing and mod-off");
    }
}
