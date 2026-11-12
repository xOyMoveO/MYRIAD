package com.myriad.character.skills.fenjue;

import com.mojang.math.Transformation;
import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 火部·焚诀 R 大招【焚天火海】：通过修改玩家的 motion（服务端 setDeltaMovement +
 * 客户端 DashPayload/DashController 协同）使玩家跃向空中（约 0.5 秒升至 10 格），
 * 随后自然重力加速下坠约 1 秒；砸地瞬间将周围方块以 block_display 形式击飞、
 * 地面岩浆喷涌；对落地半径 8 格敌人造成 50+30%攻击力 物理伤害；落地后攻击力 +20%（5 秒）。
 */
public class FenTianHuoHaiSkill extends Skill {
   private static final double RADIUS = 8.0;
   private static final int RISE_TICKS = 10;
   private static final int FALL_TICKS = 20;
   private static final int BURN_TICKS = 80;
   private static final int BLOCK_DISPLAY_LIFETIME = 60;
   private static final int FLYING_BLOCK_COUNT = 12;
   private static final double RISE_SPEED = 1.0;
   private static final int AD_BUFF_TICKS = 100;
   private static final Identifier AD_MOD = Identifier.parse("myriad:fentianhuohai_ad_buff");
   private static final Map<UUID, Long> AD_UNTIL = new HashMap<>();
   private static final Map<UUID, FenTianHuoHaiSkill.LeapInfo> LEAP = new HashMap<>();
   private static final Map<UUID, List<FenTianHuoHaiSkill.FlyingBlock>> FLYING = new HashMap<>();
   private static final Map<UUID, List<FenTianHuoHaiSkill.Geyser>> GEYSERS = new HashMap<>();

   public FenTianHuoHaiSkill() {
      super("fentianhuohai", 1400, 252.0F);
   }

   @Override
   public int getCooldownTicks(int level) {
      return Math.max(20, 1400 - Math.max(0, level - 1) * 120);
   }

   public boolean isUltimate() {
      return true;
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：0.5秒飞升至空中10格，1秒后猛砸地面并击飞周围方块，对半径8格造成 "
              + (50 + 20 * (level - 1)) + "+" + (30 + 10 * (level - 1))
              + "%攻击力 物理伤害并附加灼烧（每0.5秒造成10%物攻真实伤害，4秒）；落地后攻击力+20%（5秒）";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      LEAP.remove(player.getUUID());
      discardFlyingBlocks(player.getUUID());
      FenTianHuoHaiSkill.LeapInfo info = new FenTianHuoHaiSkill.LeapInfo();
      info.start = level.getGameTime();
      info.startY = player.getY();
      info.level = Math.max(1, data.skillLevel("fentianhuohai"));
      LEAP.put(player.getUUID(), info);
      data.setBuff("fentianhuohai", 30);
      player.setDeltaMovement(0.0, 1.0, 0.0);
      player.resetFallDistance();
      PacketDistributor.sendToPlayer(player, new DashPayload(0.0F, 1.0F, 0.0F, 10), new CustomPacketPayload[0]);
      double x = player.getX();
      double z = player.getZ();
      level.sendParticles(ParticleTypes.FLAME, x, info.startY - 1.0, z, 30, 0.5, 0.2, 0.5, 0.12);
      level.sendParticles(ParticleTypes.LAVA, x, info.startY - 0.5, z, 14, 0.4, 0.2, 0.4, 0.1);
      level.sendParticles(ParticleTypes.SMOKE, x, info.startY - 0.8, z, 10, 0.5, 0.3, 0.5, 0.08);
      level.playSound(null, x, info.startY, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
      level.playSound(null, x, info.startY, z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 1.0F, 0.7F);
      level.playSound(null, x, info.startY, z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.9F, 1.2F);
      level.playSound(null, x, info.startY, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.6F);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      advanceFlyingBlocks(player);
      advanceGeysers(player);
      expireAdBuff(player, data);
      FenTianHuoHaiSkill.LeapInfo info = LEAP.get(player.getUUID());
      if (info != null) {
         ServerLevel level = player.level();
         long now = level.getGameTime();
         if (!player.isAlive()) {
            LEAP.remove(player.getUUID());
            data.setBuff("fentianhuohai", 0);
         } else {
            long elapsed = now - info.start;
            double x = player.getX();
            double z = player.getZ();
            if (elapsed < 10L) {
               player.setDeltaMovement(0.0, 1.0, 0.0);
               player.resetFallDistance();
               player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 10, false, false, false));
               double y = player.getY();
               level.sendParticles(ParticleTypes.FLAME, x, y - 1.2, z, 8, 0.4, 0.1, 0.4, 0.1);
               level.sendParticles(ParticleTypes.FLAME, x, y - 0.6, z, 4, 0.2, 0.1, 0.2, 0.05);
               if (elapsed % 4L == 0L) {
                  level.sendParticles(ParticleTypes.LAVA, x, y - 1.0, z, 6, 0.4, 0.2, 0.4, 0.08);
               }

               if (elapsed % 3L == 0L) {
                  level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.9F + (float)elapsed * 0.06F);
               }

               if (elapsed % 6L == 0L) {
                  level.playSound(null, x, y, z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.7F, 1.0F + (float)elapsed * 0.05F);
               }
            } else if (elapsed < 30L) {
               player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 10, false, false, false));
               double y = player.getY();
               level.sendParticles(ParticleTypes.FLAME, x, y + 0.4, z, 3, 0.2, 0.05, 0.2, 0.04);
               if (player.onGround() || y <= info.startY - 0.5) {
                  doSlam(player, data);
                  return;
               }
            } else if (!info.impacted) {
               doSlam(player, data);
            }
         }
      }
   }

   private static void doSlam(ServerPlayer player, PlayerData data) {
      FenTianHuoHaiSkill.LeapInfo info = LEAP.get(player.getUUID());
      if (info != null && !info.impacted) {
         info.impacted = true;
         LEAP.remove(player.getUUID());
         data.setBuff("fentianhuohai", 0);
         slam(player, data, info.level);
      }
   }

   private static void slam(ServerPlayer player, PlayerData data, int skillLevel) {
      ServerLevel level = player.level();
      double x = player.getX();
      double z = player.getZ();
      double gy = player.getY();

      for (int i = 0; i < 30; i++) {
         gy -= 0.5;
         BlockPos feet = BlockPos.containing(x, gy, z);
         if (level.getBlockState(feet).isSolid()) {
            gy += 0.5;
            break;
         }
      }

      player.setDeltaMovement(0.0, 0.0, 0.0);
      player.resetFallDistance();
      spawnFlyingBlocks(level, player, x, gy, z);
      spawnGeysers(level, player, x, gy, z);

      for (int ring = 0; ring < 5; ring++) {
         double rr = 1.5 + ring * 1.5;
         int n = (int)(24.0 + rr * 3.0);

         for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2) * i / n;
            level.sendParticles(ParticleTypes.FLAME, x + Math.cos(a) * rr, gy + 0.3, z + Math.sin(a) * rr, 1, 0.0, 0.35, 0.0, 0.06);
            if (i % 4 == 0) {
               level.sendParticles(ParticleTypes.SMOKE, x + Math.cos(a) * rr, gy + 0.8, z + Math.sin(a) * rr, 1, 0.0, 0.4, 0.0, 0.04);
            }
         }
      }

      for (int i = 0; i < 40; i++) {
         double a = level.getRandom().nextDouble() * Math.PI * 2.0;
         double r = level.getRandom().nextDouble() * 8.0;
         double px = x + Math.cos(a) * r;
         double pz = z + Math.sin(a) * r;
         level.sendParticles(ParticleTypes.FLAME, px, gy + 0.15, pz, 1, 0.08, 0.05, 0.08, 0.01);
         if (i % 3 == 0) {
            level.sendParticles(ParticleTypes.LAVA, px, gy + 0.2, pz, 1, 0.1, 0.2, 0.1, 0.06);
         }
      }

      level.sendParticles(ParticleTypes.LAVA, x, gy + 0.5, z, 30, 1.0, 0.4, 1.0, 0.12);
      level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, gy + 1.0, z, 20, 0.8, 0.5, 0.8, 0.05);
      float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
      float dmg = 200.0F + 80.0F * (skillLevel - 1)
              + (1.20F + 0.40F * (skillLevel - 1)) * ad;
      AABB box = new AABB(x - 8.0, gy - 2.0, z - 8.0, x + 8.0, gy + 4.0, z + 8.0);

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, exx -> exx != player && exx.isAlive() && !isAlly(player, exx))) {
         DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 3);
         LieYanZhanSkill.applyBurn(player, e, 80);
         double ex = e.getX();
         double ey = e.getY() + e.getBbHeight() * 0.5;
         double ez = e.getZ();
         level.sendParticles(ParticleTypes.FLAME, ex, ey, ez, 18, 0.35, 0.4, 0.35, 0.1);
         level.sendParticles(ParticleTypes.LAVA, ex, ey, ez, 8, 0.3, 0.3, 0.3, 0.08);
      }

      level.playSound(null, x, gy, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.9F);
      level.playSound(null, x, gy, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
      level.playSound(null, x, gy, z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.9F, 0.7F);
      applyAdBuff(player, data);
   }

   private static void applyAdBuff(ServerPlayer player, PlayerData data) {
      AttributeInstance ad = player.getAttribute(ModAttributes.PHYSICAL_ATTACK);
      if (ad != null) {
         ad.removeModifier(AD_MOD);
         ad.addTransientModifier(new AttributeModifier(AD_MOD,
                 1.4, Operation.ADD_MULTIPLIED_TOTAL));
      }

      AD_UNTIL.put(player.getUUID(), player.level().getGameTime() + 100L);
      data.setBuff("fentianhuohai_ad", 100);
   }

   private static void expireAdBuff(ServerPlayer player, PlayerData data) {
      Long until = AD_UNTIL.get(player.getUUID());
      if (until != null) {
         if (player.level().getGameTime() >= until) {
            AD_UNTIL.remove(player.getUUID());
            AttributeInstance ad = player.getAttribute(ModAttributes.PHYSICAL_ATTACK);
            if (ad != null) {
               ad.removeModifier(AD_MOD);
            }

            data.setBuff("fentianhuohai_ad", 0);
         }
      }
   }

   private static void spawnFlyingBlocks(ServerLevel level, ServerPlayer player, double x, double gy, double z) {
      discardFlyingBlocks(player.getUUID());
      long now = level.getGameTime();
      List<FenTianHuoHaiSkill.FlyingBlock> list = new ArrayList<>();

      for (int i = 0; i < 12; i++) {
         double a = level.getRandom().nextDouble() * Math.PI * 2.0;
         double r = 1.0 + level.getRandom().nextDouble() * 4.0;
         BlockPos bp = BlockPos.containing(x + Math.cos(a) * r, gy - 0.5, z + Math.sin(a) * r);
         if (level.isLoaded(bp)) {
            BlockState bs = level.getBlockState(bp);
            if (!bs.isAir() && !bs.getCollisionShape(level, bp).isEmpty() && !bs.getBlock().asItem().equals(Items.AIR)) {
               ItemDisplay d = (ItemDisplay)EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED);
               if (d != null) {
                  d.getSlot(0).set(new ItemStack(bs.getBlock()));
                  d.setItemTransform(ItemDisplayContext.ON_SHELF);
                  d.setBillboardConstraints(BillboardConstraints.FIXED);
                  d.setPos(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                  level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(d));
                  FenTianHuoHaiSkill.FlyingBlock fb = new FenTianHuoHaiSkill.FlyingBlock();
                  fb.display = d;
                  fb.pos = new Vec3(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
                  fb.spawnTick = now;
                  Vec3 dir = new Vec3(bp.getX() + 0.5 - x, 0.0, bp.getZ() + 0.5 - z);
                  if (dir.lengthSqr() < 0.01) {
                     dir = new Vec3(0.0, 0.0, 1.0);
                  } else {
                     dir = dir.normalize();
                  }

                  fb.vel = dir.scale(0.6 + level.getRandom().nextDouble() * 0.8).add(0.0, 0.7 + level.getRandom().nextDouble() * 0.7, 0.0);
                  fb.rotVX = (float)((level.getRandom().nextDouble() - 0.5) * 0.3);
                  fb.rotVY = (float)((level.getRandom().nextDouble() - 0.5) * 0.35);
                  fb.rotVZ = (float)((level.getRandom().nextDouble() - 0.5) * 0.3);
                  list.add(fb);
               }
            }
         }
      }

      if (!list.isEmpty()) {
         FLYING.put(player.getUUID(), list);
      }
   }

   private static void advanceFlyingBlocks(ServerPlayer player) {
      List<FenTianHuoHaiSkill.FlyingBlock> list = FLYING.get(player.getUUID());
      if (list != null && !list.isEmpty()) {
         ServerLevel level = player.level();
         long now = level.getGameTime();
         Iterator<FenTianHuoHaiSkill.FlyingBlock> it = list.iterator();

         while (it.hasNext()) {
            FenTianHuoHaiSkill.FlyingBlock fb = it.next();
            fb.age++;
            if (fb.age < 60 && now - fb.spawnTick < 60L && fb.display.isAlive()) {
               fb.vel = fb.vel.scale(0.99).add(0.0, -0.05, 0.0);
               fb.pos = fb.pos.add(fb.vel);
               fb.display.setPos(fb.pos.x, fb.pos.y, fb.pos.z);
               fb.rotX = fb.rotX + fb.rotVX;
               fb.rotY = fb.rotY + fb.rotVY;
               fb.rotZ = fb.rotZ + fb.rotVZ;
               fb.display
                  .setTransformation(
                     new Transformation(
                        new Vector3f(0.0F, 0.0F, 0.0F),
                        new Quaternionf().rotateXYZ(fb.rotX, fb.rotY, fb.rotZ),
                        new Vector3f(1.0F, 1.0F, 1.0F),
                        new Quaternionf()
                     )
                  );
            } else {
               if (fb.display.isAlive()) {
                  fb.display.discard();
               }

               it.remove();
            }
         }

         if (list.isEmpty()) {
            FLYING.remove(player.getUUID());
         }
      }
   }

   private static void spawnGeysers(ServerLevel level, ServerPlayer player, double x, double gy, double z) {
      List<FenTianHuoHaiSkill.Geyser> list = new ArrayList<>();

      for (int i = 0; i < 7; i++) {
         double a = level.getRandom().nextDouble() * Math.PI * 2.0;
         double r = 1.5 + level.getRandom().nextDouble() * 5.5;
         FenTianHuoHaiSkill.Geyser g = new FenTianHuoHaiSkill.Geyser();
         g.x = x + Math.cos(a) * r;
         g.y = gy;
         g.z = z + Math.sin(a) * r;
         g.remaining = 40;
         list.add(g);
      }

      GEYSERS.put(player.getUUID(), list);
   }

   private static void advanceGeysers(ServerPlayer player) {
      List<FenTianHuoHaiSkill.Geyser> list = GEYSERS.get(player.getUUID());
      if (list != null && !list.isEmpty()) {
         ServerLevel level = player.level();
         Iterator<FenTianHuoHaiSkill.Geyser> it = list.iterator();

         while (it.hasNext()) {
            FenTianHuoHaiSkill.Geyser g = it.next();
            g.remaining--;
            if (g.remaining <= 0) {
               it.remove();
            } else if (player.tickCount % 2 == 0) {
               level.sendParticles(
                  ParticleTypes.LAVA,
                  g.x + (level.getRandom().nextDouble() - 0.5) * 0.3,
                  g.y + 0.3,
                  g.z + (level.getRandom().nextDouble() - 0.5) * 0.3,
                  4,
                  0.12,
                  0.5,
                  0.12,
                  0.3
               );
               level.sendParticles(ParticleTypes.FLAME, g.x, g.y + 0.15, g.z, 3, 0.15, 0.1, 0.15, 0.04);
               if (level.getRandom().nextInt(3) == 0) {
                  level.sendParticles(ParticleTypes.SMOKE, g.x, g.y + 0.4, g.z, 1, 0.1, 0.3, 0.1, 0.03);
                  level.sendParticles(
                     ParticleTypes.LAVA,
                     g.x + (level.getRandom().nextDouble() - 0.5) * 0.6,
                     g.y + 0.2,
                     g.z + (level.getRandom().nextDouble() - 0.5) * 0.6,
                     1,
                     0.2,
                     0.4,
                     0.2,
                     0.1
                  );
               }
            }
         }

         if (list.isEmpty()) {
            GEYSERS.remove(player.getUUID());
         }
      }
   }

   private static void discardFlyingBlocks(UUID playerId) {
      List<FenTianHuoHaiSkill.FlyingBlock> list = FLYING.remove(playerId);
      if (list != null) {
         for (FenTianHuoHaiSkill.FlyingBlock fb : list) {
            if (fb.display.isAlive()) {
               fb.display.discard();
            }
         }
      }
   }

   public static void clearFor(UUID playerId) {
      LEAP.remove(playerId);
      discardFlyingBlocks(playerId);
      GEYSERS.remove(playerId);
      AD_UNTIL.remove(playerId);
   }

   private static boolean isAlly(ServerPlayer caster, LivingEntity other) {
      if (other instanceof ServerPlayer otherPlayer) {
         GameManager manager = GameManager.get();
         if (manager == null) {
            return false;
         }

         Faction a = manager.factionOf(caster);
         Faction b = manager.factionOf(otherPlayer);
         return a != null && a == b;
      } else {
         return false;
      }
   }

   private static final class FlyingBlock {
      ItemDisplay display;
      Vec3 pos;
      Vec3 vel;
      float rotX;
      float rotY;
      float rotZ;
      float rotVX;
      float rotVY;
      float rotVZ;
      int age;
      long spawnTick;
   }

   private static final class Geyser {
      double x;
      double y;
      double z;
      int remaining;
   }

   private static final class LeapInfo {
      long start;
      double startY;
      boolean impacted;
      int level;
   }
}
