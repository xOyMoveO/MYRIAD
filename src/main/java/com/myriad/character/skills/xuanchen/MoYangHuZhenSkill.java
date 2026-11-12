package com.myriad.character.skills.xuanchen;

import com.myriad.character.Skill;
import com.myriad.game.Faction;
import com.myriad.game.PlacementUtil;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 玄阵·墨尘 E 技能【墨灵护阵】：布阵模式（准星预览半径 4 格圆），左键释放/右键取消；
 * 确认后召唤护阵，持续数秒，范围内己方获得减伤/护盾等增益。
 */
public class MoYangHuZhenSkill extends Skill {
   public static final double RADIUS = 6.0;
   public static final int DURATION_TICKS = 100;
   public static final double PLACE_RANGE = 20.0;
   private static final double PLACE_STEP = 0.25;
   private static final double VERTICAL_RANGE = 3.0;
   private static final double ALLY_SPEED_BONUS = 0.15;
   private static final int CAST_TICKS = 20;
   private static final int FALL_TICKS = 20;
   private static final Map<UUID, MoYangHuZhenSkill.Zone> ZONES = new HashMap<>();
   private static final Map<UUID, MoYangHuZhenSkill.PendingCast> PENDING = new HashMap<>();
   private static final Map<UUID, UUID> SPEED_APPLIED = new HashMap<>();

   public MoYangHuZhenSkill() {
      super("moyanghuzhen", 500, 216.0F);
   }

   @Override
   public int getCooldownTicks(int level) {
      return Math.max(20, 500 - Math.max(0, level - 1) * 40);
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：布阵半径6格，持续5秒；友方减伤30%+移速15%，敌方减速30%";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 1) {
         cancel(player, data);
      }

      PENDING.remove(player.getUUID());
      data.setPlacementType(1);
      PlayerDataProvider.syncToClient(player);
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, 0.6F, 1.2F);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      long now = level.getGameTime();
      if (data.getPlacementType() == 1) {
         if (player.tickCount % 4 == 0) {
            Vec3 center = placementPoint(player);
            if (center != null) {
               drawRingTo(level, player, center, 6.0, 36, ParticleTypes.SCULK_SOUL);
               drawRingTo(level, player, center, 3.5999999999999996, 18, ParticleTypes.END_ROD);
            }
         }
      } else {
         MoYangHuZhenSkill.PendingCast pending = PENDING.get(player.getUUID());
         if (pending != null) {
            if (!player.isAlive()) {
               PENDING.remove(player.getUUID());
            } else {
               tickPending(level, player, data, pending, now);
            }
         } else {
            MoYangHuZhenSkill.Zone zone = ZONES.get(player.getUUID());
            if (zone != null) {
               if (now >= zone.expireTick) {
                  removeZone(level, zone);
               } else {
                  if (player.tickCount % 10 == 0) {
                     drawRing(level, new Vec3(zone.x, zone.y, zone.z), 6.0, 40, ParticleTypes.SCULK_SOUL);
                     drawRing(level, new Vec3(zone.x, zone.y, zone.z), 4.199999999999999, 24, ParticleTypes.END_ROD);

                     for (LivingEntity e : entitiesInZone(level, zone)) {
                        if (isEnemyOfOwner(zone, e)) {
                           e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 1, false, false));
                        }
                     }
                  }

                  if (player.tickCount % 20 == 0) {
                     updateAllySpeed(level, zone);
                  }
               }
            }
         }
      }
   }

   private static void tickPending(
      ServerLevel level, ServerPlayer player, PlayerData data, MoYangHuZhenSkill.PendingCast p, long now
   ) {
      long elapsed = now - p.startTick;
      if (elapsed < 20L) {
         player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 10, false, false, false));
         Vec3 from = player.position();
         level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y + 1.0, from.z, 6, 0.2, 0.05, 0.2, 0.3);
         level.sendParticles(ParticleTypes.SOUL, from.x, from.y + 1.5, from.z, 2, 0.1, 0.1, 0.1, 0.2);
         if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y + 1.2, from.z, 18, 0.15, 0.04, 0.15, 0.35);
            level.playSound(null, from.x, from.y, from.z, com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, 0.4F, 1.4F);
         }
      } else if (elapsed < 40L) {
         double progress = (elapsed - 20L) / 20.0;
         double height = 18.0 * (1.0 - progress);
         level.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y + Math.max(1.0, height), p.z, 8, 0.25, 0.1, 0.25, -0.18);
         level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + Math.max(1.0, height), p.z, 3, 0.2, 0.1, 0.2, -0.1);
         level.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y + 1.0, p.z, 4, 0.3, 0.05, 0.3, 0.02);
         drawRing(level, new Vec3(p.x, p.y, p.z), 6.0 * (1.0 - progress), 28, ParticleTypes.SCULK_SOUL);
      } else {
         PENDING.remove(player.getUUID());
         createZone(player, data, p.x, p.y, p.z);
      }
   }

   private static void createZone(ServerPlayer player, PlayerData data, double x, double y, double z) {
      MoYangHuZhenSkill.Zone zone = new MoYangHuZhenSkill.Zone();
      zone.owner = player.getUUID();
      zone.ownerFaction = data.getFaction();
      zone.x = x;
      zone.y = y;
      zone.z = z;
      zone.expireTick = player.level().getGameTime() + 100L;
      ZONES.put(player.getUUID(), zone);
      ServerLevel level = player.level();
      level.playSound(null, x, y, z, com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, 0.8F, 1.0F);
      level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.1F);
      for (int i = 0; i < 3; i++) {
         drawRing(level, new Vec3(x, y, z), 2.0 + i * 2.0, 24, ParticleTypes.SOUL);
      }

      drawRing(level, new Vec3(x, y, z), 3.0, 30, ParticleTypes.END_ROD);
      drawRing(level, new Vec3(x, y, z), 6.0, 48, ParticleTypes.SCULK_SOUL);
      drawRing(level, new Vec3(x, y, z), 4.199999999999999, 28, ParticleTypes.END_ROD);
      level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 30, 0.3, 0.4, 0.3, 0.06);
   }

   private static Vec3 placementPoint(ServerPlayer player) {
      return PlacementUtil.pointOnGround(player, 20.0);
   }

   public static void confirm(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 1) {
         Vec3 center = placementPoint(player);
         if (center != null) {
            ServerLevel level = player.level();
            PENDING.put(player.getUUID(), new MoYangHuZhenSkill.PendingCast(center.x, center.y, center.z, level.getGameTime()));
            data.setPlacementType(0);
            PlayerDataProvider.syncToClient(player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, 0.6F, 1.2F);
         }
      }
   }

   public static void cancel(ServerPlayer player, PlayerData data) {
      if (data.getPlacementType() == 1) {
         data.setPlacementType(0);
         // 初次按键由通用技能入口已写入冷却；未确认落点不应消耗本次技能。
         data.setCooldown("moyanghuzhen", 0);
         PlayerDataProvider.syncToClient(player);
      }
   }

   private static void removeZone(ServerLevel level, MoYangHuZhenSkill.Zone zone) {
      ZONES.remove(zone.owner);
      Iterator<Entry<UUID, UUID>> it = SPEED_APPLIED.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, UUID> e = it.next();
         if (e.getValue().equals(zone.owner)) {
            removeSpeedModifier(level, e.getKey(), zone.owner);
            it.remove();
         }
      }
   }

   private static boolean isAllyOfOwner(MoYangHuZhenSkill.Zone zone, ServerPlayer p) {
      PlayerData pd = PlayerDataProvider.get(p);
      return pd != null && pd.getFaction() != null && zone.ownerFaction != null ? pd.getFaction() == zone.ownerFaction : true;
   }

   private static void updateAllySpeed(ServerLevel level, MoYangHuZhenSkill.Zone zone) {
      AABB box = new AABB(zone.x - 6.0, zone.y - 3.0, zone.z - 6.0, zone.x + 6.0, zone.y + 3.0, zone.z + 6.0);
      Set<UUID> inside = new HashSet<>();

      for (Player p : level.getEntitiesOfClass(Player.class, box, px -> px.isAlive())) {
         if (p instanceof ServerPlayer sp && isAllyOfOwner(zone, sp)) {
            inside.add(sp.getUUID());
            if (!SPEED_APPLIED.containsKey(sp.getUUID())) {
               applySpeedModifier(sp, zone.owner);
            }
         }
      }

      Iterator<Entry<UUID, UUID>> it = SPEED_APPLIED.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, UUID> e = it.next();
         if (e.getValue().equals(zone.owner) && !inside.contains(e.getKey())) {
            removeSpeedModifier(level, e.getKey(), zone.owner);
            it.remove();
         }
      }
   }

   private static void applySpeedModifier(ServerPlayer player, UUID owner) {
      AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
      if (speed != null) {
         Identifier id = Identifier.parse("myriad:moyang_speed_" + owner);
         speed.addTransientModifier(new AttributeModifier(id, 0.15, Operation.ADD_MULTIPLIED_TOTAL));
         SPEED_APPLIED.put(player.getUUID(), owner);
      }
   }

   private static void removeSpeedModifier(ServerLevel level, UUID playerId, UUID owner) {
      if (level.getPlayerByUUID(playerId) instanceof ServerPlayer p) {
         AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
         if (speed != null) {
            speed.removeModifier(Identifier.parse("myriad:moyang_speed_" + owner));
         }
      }
   }

   private static List<LivingEntity> entitiesInZone(ServerLevel level, MoYangHuZhenSkill.Zone zone) {
      AABB box = new AABB(zone.x - 6.0, zone.y - 3.0, zone.z - 6.0, zone.x + 6.0, zone.y + 3.0, zone.z + 6.0);
      return level.getEntitiesOfClass(LivingEntity.class, box, e -> com.myriad.combat.SkillTargets.alive(e) && !(e instanceof ArmorStand));
   }

   private static boolean isEnemyOfOwner(MoYangHuZhenSkill.Zone zone, LivingEntity e) {
      if (e.getUUID().equals(zone.owner)) {
         return false;
      } else {
         return e instanceof ServerPlayer p ? !isAllyOfOwner(zone, p) : true;
      }
   }

   public static boolean inAllyZone(LivingEntity victim) {
      if (victim instanceof ServerPlayer vp) {
         for (MoYangHuZhenSkill.Zone zone : ZONES.values()) {
            if (isAllyOfOwner(zone, vp) && inRadius(zone, victim)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean inRadius(MoYangHuZhenSkill.Zone zone, LivingEntity e) {
      double dx = e.getX() - zone.x;
      double dz = e.getZ() - zone.z;
      double dy = e.getY() - zone.y;
      return dx * dx + dz * dz <= 36.0 && Math.abs(dy) <= 3.0;
   }

   public static void clearFor(ServerPlayer ownerPlayer) {
      UUID owner = ownerPlayer.getUUID();
      PENDING.remove(owner);
      MoYangHuZhenSkill.Zone zone = ZONES.remove(owner);
      if (zone != null && ownerPlayer.level() instanceof ServerLevel level) {
         Iterator<Entry<UUID, UUID>> it = SPEED_APPLIED.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, UUID> e = it.next();
            if (e.getValue().equals(owner)) {
               removeSpeedModifier(level, e.getKey(), owner);
               it.remove();
            }
         }
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

   private static final class PendingCast {
      double x;
      double y;
      double z;
      long startTick;

      PendingCast(double x, double y, double z, long startTick) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.startTick = startTick;
      }
   }

   private static final class Zone {
      UUID owner;
      Faction ownerFaction;
      double x;
      double y;
      double z;
      long expireTick;
   }
}
