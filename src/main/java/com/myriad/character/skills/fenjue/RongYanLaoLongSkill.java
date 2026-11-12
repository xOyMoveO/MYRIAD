package com.myriad.character.skills.fenjue;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.PlacementUtil;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 火部·焚诀 W 技能【熔岩牢笼】：布阵模式（准星预览半径 4 格圆），左键释放/右键取消；
 * 确认后从自身射出一团火焰呈抛物线飞向释放点，落地召唤熔岩火墙，持续 5 秒：
 * 敌人触碰火墙受到 13+70%攻击力起步的物理伤害并被弹回（不可穿越）；
 * 内部敌人每秒受到 1% 最大生命值的物理伤害。
 */
public class RongYanLaoLongSkill extends Skill {
   public static final int PLACEMENT_TYPE = 3;
   private static final double RADIUS = 4.0;
   private static final double TOUCH_BAND = 4.75;
   private static final int DURATION = 100;
   private static final double PLACE_RANGE = 20.0;
   private static final double PLACE_STEP = 0.25;
   private static final Map<UUID, RongYanLaoLongSkill.Zone> ZONES = new HashMap<>();
   private static final Map<UUID, RongYanLaoLongSkill.FlyingBlob> FLYING = new HashMap<>();

   public RongYanLaoLongSkill() {
      super("rongyanlaolong", 440, 180.0F);
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：准星选点布阵（半径4格），火焰抛射召唤火墙，触碰伤害 "
              + (13 + 3 * (level - 1)) + "+" + (70 + 3 * (level - 1))
              + "%攻击力 物理并弹回；内部每秒受1%最大生命值物理伤害；持续5秒";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 3) {
         cancel(player, data);
      }

      data.setPlacementType(3);
      PlayerDataProvider.syncToClient(player);
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.2F);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      long now = level.getGameTime();
      if (data.getPlacementType() == 3) {
         if (player.tickCount % 4 == 0) {
            Vec3 center = placementPoint(player);
            if (center != null) {
               drawRingTo(level, player, center, 4.0, 36, ParticleTypes.FLAME);
               drawRingTo(level, player, center, 2.4, 18, ParticleTypes.LAVA);
               double rot = now * 0.08;
               int n = 10;

               for (int i = 0; i < n; i++) {
                  double a = rot + i * Math.PI * 2.0 / n;
                  double rr = 4.0 * (0.8 + Math.sin(now * 0.1 + i) * 0.2);
                  level.sendParticles(
                     player,
                     ParticleTypes.END_ROD,
                     false,
                     false,
                     center.x + Math.cos(a) * rr,
                     center.y + 0.6,
                     center.z + Math.sin(a) * rr,
                     1,
                     0.0,
                     0.0,
                     0.0,
                     0.0
                  );
               }
            }
         }
      } else {
         RongYanLaoLongSkill.FlyingBlob fb = FLYING.get(player.getUUID());
         if (fb != null) {
            if (!player.isAlive()) {
               FLYING.remove(player.getUUID());
            } else {
               tickFlight(level, player, fb, now);
            }
         } else {
            RongYanLaoLongSkill.Zone z = ZONES.get(player.getUUID());
            if (z != null) {
               if (now >= z.until) {
                  ZONES.remove(player.getUUID());
                  data.setBuff("rongyanlaolong", 0);
               } else {
                  AABB box = new AABB(z.center.x - 4.75, z.center.y - 2.0, z.center.z - 4.75, z.center.x + 4.75, z.center.y + 5.0, z.center.z + 4.75);

                  for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex))) {
                     double dx = e.getX() - z.center.x;
                     double dz = e.getZ() - z.center.z;
                     double dist = Math.sqrt(dx * dx + dz * dz);
                     if (dist <= 4.0) {
                        pushOut(e, dx, dz, dist);
                        if (now - z.lastTick >= 20L) {
                           DamageCalc.dealPercentMaxHealthDamage(player, e, .01F, Kind.PHYSICAL, 1);
                           level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 0.8, e.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
                        }
                     } else if (dist <= 4.75) {
                        Long last = z.touchCd.get(e.getUUID());
                        if (last == null || now - last >= 20L) {
                           z.touchCd.put(e.getUUID(), now);
                           float dmg = 52.0F + 12.0F * (z.level - 1)
                                   + (2.80F + 0.12F * (z.level - 1)) * z.ad;
                           DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 1);
                           knockBack(e, dx, dz, 1.1);
                        }
                     }
                  }

                  if (now - z.lastTick >= 20L) {
                     z.lastTick = now;
                  }

                  if (player.tickCount % 4 == 0) {
                     spawnRing(level, z.center, 4);
                  }
               }
            }
         }
      }
   }

   private static void tickFlight(ServerLevel level, ServerPlayer player, RongYanLaoLongSkill.FlyingBlob fb, long now) {
      long elapsed = now - fb.startTick;
      if (elapsed >= fb.totalTicks) {
         FLYING.remove(player.getUUID());
         createZone(player, fb.target.x, fb.target.y, fb.target.z);
      } else {
         double t = (double)elapsed / fb.totalTicks;
         Vec3 pos = fb.start.lerp(fb.target, t);
         double arcH = Math.max(2.0, fb.start.distanceTo(fb.target) * 0.25);
         pos = pos.add(0.0, Math.sin(t * Math.PI) * arcH, 0.0);
         level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 8, 0.35, 0.35, 0.35, 0.05);
         level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 3, 0.2, 0.2, 0.2, 0.02);
         level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.2, pos.z, 2, 0.25, 0.25, 0.25, 0.02);

         for (int i = 0; i < 6; i++) {
            double tt = Math.max(0.0, t - (i + 1) * 0.05);
            Vec3 tp = fb.start.lerp(fb.target, tt).add(0.0, Math.sin(tt * Math.PI) * arcH, 0.0);
            level.sendParticles(ParticleTypes.FLAME, tp.x, tp.y, tp.z, 1, 0.1, 0.1, 0.1, 0.01);
         }

         if (elapsed % 4L == 0L) {
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.2F);
         }
      }
   }

   private static void createZone(ServerPlayer player, double x, double y, double z) {
      RongYanLaoLongSkill.Zone zone = new RongYanLaoLongSkill.Zone();
      zone.center = new Vec3(x, y, z);
      zone.until = player.level().getGameTime() + 100L;
      zone.lastTick = player.level().getGameTime();
      zone.ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
      zone.level = Math.max(1, PlayerDataProvider.get(player).skillLevel("rongyanlaolong"));
      ZONES.put(player.getUUID(), zone);
      PlayerData data = PlayerDataProvider.get(player);
      if (data != null) {
         data.setBuff("rongyanlaolong", 100);
      }

      ServerLevel level = player.level();
      level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 40, 0.8, 0.4, 0.8, 0.08);
      level.sendParticles(ParticleTypes.LAVA, x, y + 0.5, z, 20, 0.6, 0.4, 0.6, 0.12);

      for (int ring = 0; ring < 3; ring++) {
         spawnRing(level, zone.center, 12 + ring * 4);
      }

      level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
      level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 0.7F);
      level.playSound(null, x, y, z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.9F, 0.9F);
   }

   private static Vec3 placementPoint(ServerPlayer player) {
      return PlacementUtil.pointOnGround(player, 20.0);
   }

   public static void confirm(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 3) {
         Vec3 center = placementPoint(player);
         if (center != null) {
            ServerLevel level = player.level();
            RongYanLaoLongSkill.FlyingBlob fb = new RongYanLaoLongSkill.FlyingBlob();
            fb.start = player.getEyePosition();
            fb.target = center;
            fb.startTick = level.getGameTime();
            double dist = fb.start.distanceTo(center);
            fb.totalTicks = Math.max(8, Math.min(20, (int)Math.ceil(dist * 0.9)));
            FLYING.put(player.getUUID(), fb);
            data.setPlacementType(0);
            PlayerDataProvider.syncToClient(player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.0F);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.3F);
         }
      }
   }

   public static void cancel(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 3) {
         data.setPlacementType(0);
         data.setCooldown("rongyanlaolong", 0);
         PlayerDataProvider.syncToClient(player);
      }
   }

   private static void spawnRing(ServerLevel level, Vec3 center, int density) {
      long t = level.getGameTime();

      for (int i = 0; i < density; i++) {
         double a = (Math.PI * 2) * i / density;
         double x = center.x + Math.cos(a) * 4.0;
         double z = center.z + Math.sin(a) * 4.0;
         level.sendParticles(ParticleTypes.FLAME, x, center.y + 0.3, z, 2, 0.0, 0.2, 0.0, 0.01);
         level.sendParticles(ParticleTypes.FLAME, x, center.y + 1.1, z, 2, 0.0, 0.2, 0.0, 0.008);
         level.sendParticles(ParticleTypes.FLAME, x, center.y + 1.9, z, 1, 0.0, 0.2, 0.0, 0.005);
         level.sendParticles(ParticleTypes.LAVA, x, center.y + 0.08, z, 1, 0.05, 0.0, 0.05, 0.0);
         if (i % 3 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, x, center.y + 2.3, z, 1, 0.05, 0.3, 0.05, 0.008);
         }
      }

      double rot = t * 0.06;
      int n = 12;

      for (int i = 0; i < n; i++) {
         double a = rot + i * Math.PI * 2.0 / n;
         double rr = 3.3;
         level.sendParticles(
            ParticleTypes.END_ROD,
            center.x + Math.cos(a) * rr,
            center.y + 0.7 + Math.sin(rot * 0.5 + i) * 0.3,
            center.z + Math.sin(a) * rr,
            1,
            0.0,
            0.0,
            0.0,
            0.0
         );
      }

      for (int i = 0; i < 4; i++) {
         double a = level.getRandom().nextDouble() * Math.PI * 2.0;
         double rr = level.getRandom().nextDouble() * 3.2;
         level.sendParticles(
            ParticleTypes.FLAME,
            center.x + Math.cos(a) * rr,
            center.y + 0.4 + level.getRandom().nextDouble() * 0.8,
            center.z + Math.sin(a) * rr,
            1,
            0.0,
            0.3,
            0.0,
            0.01
         );
      }
   }

   private static void pushOut(LivingEntity e, double dx, double dz, double dist) {
      double strength = 0.12 + 0.06 * Math.max(0.0, 4.0 - dist);
      double nx = dist < 0.05 ? 1.0 : dx / dist;
      double nz = dist < 0.05 ? 0.0 : dz / dist;
      com.myriad.combat.KnockbackBuffer.apply(e,
         new Vec3(nx * strength, Math.max(-0.1, e.getDeltaMovement().y), nz * strength));
   }

   private static void knockBack(LivingEntity e, double dx, double dz, double strength) {
      double dist = Math.sqrt(dx * dx + dz * dz);
      double nx = dist < 0.05 ? 1.0 : dx / dist;
      double nz = dist < 0.05 ? 0.0 : dz / dist;
      com.myriad.combat.KnockbackBuffer.apply(e, new Vec3(nx * strength, 0.3, nz * strength));
   }

   public static void clearFor(ServerPlayer player) {
      UUID id = player.getUUID();
      FLYING.remove(id);
      ZONES.remove(id);
      PlayerData data = PlayerDataProvider.get(player);
      if (data != null && data.getPlacementType() == 3) {
         data.setPlacementType(0);
      }
   }

   private static void drawRing(ServerLevel level, Vec3 center, double radius, int count, ParticleOptions particle) {
      for (int i = 0; i < count; i++) {
         double a = (Math.PI * 2) * i / count;
         level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.05, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static void drawRingTo(ServerLevel level, ServerPlayer player, Vec3 center, double radius, int count, ParticleOptions particle) {
      for (int i = 0; i < count; i++) {
         double a = (Math.PI * 2) * i / count;
         level.sendParticles(
            player, particle, false, false, center.x + Math.cos(a) * radius, center.y + 0.05, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0
         );
      }
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

   private static final class FlyingBlob {
      Vec3 start;
      Vec3 target;
      long startTick;
      int totalTicks;
   }

   private static final class Zone {
      Vec3 center;
      long until;
      long lastTick;
      float ad;
      int level;
      final Map<UUID, Long> touchCd = new HashMap<>();
   }
}
