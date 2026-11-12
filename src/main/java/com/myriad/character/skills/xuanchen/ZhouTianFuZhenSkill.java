package com.myriad.character.skills.xuanchen;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.network.NetworkSetup.ZhouTianChannelPayload;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 玄阵·墨尘 R 大招【周天覆阵】：布下大范围法阵，持续数秒，
 * 范围内己方持续获得增益效果。
 */
public class ZhouTianFuZhenSkill extends Skill {
   public static final double RADIUS = 8.0;
   public static final int BASE_DURATION = 120;
   public static final int DURATION_PER_LEVEL = 20;
   private static final double VERTICAL_RANGE = 3.0;
   private static final int CHANNEL_TICKS = 40;
   private static final int SPIRAL_TICKS = 30;
   private static final int FOLD_TICKS = 10;
   private static final int SPIRAL_POINTS = 12;
   private static final Map<UUID, ZhouTianFuZhenSkill.Zone> ZONES = new HashMap<>();
   private static final Map<UUID, ZhouTianFuZhenSkill.PendingCast> CASTING = new HashMap<>();

   public ZhouTianFuZhenSkill() {
      super("zhoutianfuzhen", 1600, 288.0F);
   }

   @Override
   public int getCooldownTicks(int level) {
      return Math.max(20, 1600 - Math.max(0, level - 1) * 260);
   }

   public boolean isUltimate() {
      return true;
   }

   public String levelEffects(int level) {
      int dmg = 10 + 20 * (level - 1);
      int duration = 6 + (level - 1);
      return "Lv." + level + "：引导2秒（12阵点螺旋收回，期间无法移动/传送）后半径8格展开，持续" + duration + "秒；友方减伤25%+每秒回5法力，敌方减速40%且每0.5秒受到 " + dmg + "+25%法术强度 伤害";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      ZhouTianFuZhenSkill.PendingCast pending = new ZhouTianFuZhenSkill.PendingCast();
      pending.x = player.getX();
      pending.y = player.getY();
      pending.z = player.getZ();
      pending.startTick = level.getGameTime();
      pending.level = data.skillLevel("zhoutianfuzhen");
      pending.sp = (float)player.getAttributeValue(ModAttributes.SPELL_POWER);
      CASTING.put(player.getUUID(), pending);
      player.sendSystemMessage(Component.translatable("message.myriad.zhoutian_casting"));
      level.playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, 0.5F, 0.7F);
      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 0.8F);
      PacketDistributor.sendToPlayer(player, new ZhouTianChannelPayload(40), new CustomPacketPayload[0]);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      ZhouTianFuZhenSkill.PendingCast pending = CASTING.get(player.getUUID());
      if (pending != null) {
         tickChannel(player, data, pending);
      } else {
         ZhouTianFuZhenSkill.Zone zone = ZONES.get(player.getUUID());
         if (zone != null) {
            ServerLevel level = player.level();
            long now = level.getGameTime();
            if (now >= zone.expireTick) {
               ZONES.remove(player.getUUID());
               data.setBuff("zhoutianfuzhen", 0);
            } else {
               if (player.tickCount % 10 == 0) {
                  tickZoneVisuals(level, player, zone);

                  for (LivingEntity e : entitiesInZone(level, zone)) {
                     if (isEnemyOfOwner(zone, e)) {
                        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2, false, false));
                        if (level.getPlayerByUUID(zone.owner) instanceof ServerPlayer owner) {
                           float dmg = 40.0F + 80.0F * (zone.level - 1) + 1.0F * zone.sp;
                           DamageCalc.dealSkillDamage(owner, e, dmg, Kind.MAGIC, 3);
                           level.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.2, 0.2, 0.2, 0.04);
                        }
                     }
                  }
               }

               if (player.tickCount % 20 == 0) {
                  for (Player p : level.getEntitiesOfClass(
                     Player.class,
                     new AABB(zone.x - 8.0, zone.y - 3.0, zone.z - 8.0, zone.x + 8.0, zone.y + 3.0, zone.z + 8.0),
                     px -> px.isAlive() && px instanceof ServerPlayer
                  )) {
                     ServerPlayer sp = (ServerPlayer)p;
                     if (isAllyOfOwner(zone, sp)) {
                        PlayerData pd = PlayerDataProvider.get(sp);
                        if (pd != null) {
                           double max = p.getAttributeValue(ModAttributes.MAX_MANA);
                           pd.setMana((float)Math.min(max, pd.getMana() + 5.0F));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void tickChannel(ServerPlayer player, PlayerData data, ZhouTianFuZhenSkill.PendingCast p) {
      ServerLevel level = player.level();
      if (!player.isAlive()) {
         cancelChannel(player);
      } else {
         long elapsed = level.getGameTime() - p.startTick;
         if (elapsed < 40L) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 10, false, false, false));
         }

         Vec3 c = new Vec3(p.x, p.y, p.z);
         if (elapsed < 30L) {
            double prog = elapsed / 30.0;
            double rot = prog * Math.PI * 3.0;
            double radius = 8.0 * (1.0 - prog);

            for (int i = 0; i < 12; i++) {
               ParticleOptions pt = i % 2 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.END_ROD;
               double yOff = (i % 3 - 1) * 0.25;

               for (int k = 1; k <= 2; k++) {
                  double pk = Math.max(0.0, prog - k * 0.03);
                  double rk = 8.0 * (1.0 - pk);
                  double ak = (Math.PI * 2) * i / 12.0 + pk * Math.PI * 3.0;
                  level.sendParticles(pt, c.x + Math.cos(ak) * rk, c.y + 1.0 + yOff * (1.0 - pk * 0.5), c.z + Math.sin(ak) * rk, 1, 0.0, 0.0, 0.0, 0.0);
               }

               double a = (Math.PI * 2) * i / 12.0 + rot;
               level.sendParticles(pt, c.x + Math.cos(a) * radius, c.y + 1.0 + yOff, c.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
            }

            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 1, 0.05, 0.05, 0.05, 0.01);
            if (player.tickCount % 10 == 0) {
               level.playSound(null, c.x, c.y, c.z, com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, 0.3F, 1.3F);
            }
         } else if (elapsed < 40L) {
            double prog = (elapsed - 30L) / 10.0;
            double r = 8.0 * prog;
            drawRing(level, c, r, 40, ParticleTypes.SCULK_SOUL);
            drawRing(level, c, r * 0.75, 24, ParticleTypes.END_ROD);

            for (int i = 0; i < 8; i++) {
               level.sendParticles(ParticleTypes.SOUL, c.x, c.y + 1.0 + i * 1.5, c.z, 1, 0.15, 0.0, 0.15, 0.02);
            }

            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 3, 0.3, 0.2, 0.3, 0.01);
         } else {
            CASTING.remove(player.getUUID());
            createZone(player, data, p);
         }
      }
   }

   private static void createZone(ServerPlayer player, PlayerData data, ZhouTianFuZhenSkill.PendingCast p) {
      ServerLevel level = player.level();
      ZhouTianFuZhenSkill.Zone zone = new ZhouTianFuZhenSkill.Zone();
      zone.owner = player.getUUID();
      zone.ownerFaction = data.getFaction();
      zone.x = p.x;
      zone.y = p.y;
      zone.z = p.z;
      zone.level = p.level;
      zone.sp = p.sp;
      int duration = 120 + 20 * (p.level - 1);
      zone.expireTick = level.getGameTime() + duration;
      ZONES.put(player.getUUID(), zone);
      data.setBuff("zhoutianfuzhen", duration);
      Vec3 c = new Vec3(p.x, p.y + 0.1, p.z);
      level.playSound(null, p.x, p.y, p.z, com.myriad.registry.ModSounds.foley("wood"), SoundSource.PLAYERS, 1.0F, 0.8F);
      level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);

      for (int i = 0; i < 3; i++) {
         double r = 4.0 + i * 2.0;
         drawRing(level, c, r, 48, ParticleTypes.SOUL);
      }

      drawRing(level, c, 8.0, 64, ParticleTypes.SCULK_SOUL);
      drawRing(level, c, 5.6, 40, ParticleTypes.END_ROD);

      for (int i = 0; i < 24; i++) {
         level.sendParticles(ParticleTypes.SOUL, p.x, p.y + 1.0 + i * 1.2, p.z, 1, 0.1, 0.05, 0.1, 0.02);
      }

      level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1.0, p.z, 40, 0.5, 0.3, 0.5, 0.05);
      level.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y + 1.0, p.z, 30, 0.6, 0.4, 0.6, 0.04);
   }

   private static void tickZoneVisuals(ServerLevel level, ServerPlayer player, ZhouTianFuZhenSkill.Zone zone) {
      long time = level.getGameTime();
      double rot = time % 120L / 120.0 * Math.PI * 2.0;
      Vec3 c = new Vec3(zone.x, zone.y + 0.1, zone.z);
      drawRing(level, c, 8.0, 56, ParticleTypes.SCULK_SOUL);
      drawRing(level, c, 6.0, 32, ParticleTypes.END_ROD);
      drawArc(level, c, 7.04, rot, Math.PI * 6.0 / 5.0, 18, ParticleTypes.SOUL);

      for (int i = 0; i < 8; i++) {
         double a = rot + (Math.PI * 2) * i / 8.0;
         level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * 8.0 * 0.6, zone.y + 0.3, c.z + Math.sin(a) * 8.0 * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
         level.sendParticles(ParticleTypes.SOUL, c.x + Math.cos(-a) * 8.0 * 0.85, zone.y + 1.8, c.z + Math.sin(-a) * 8.0 * 0.85, 1, 0.0, 0.0, 0.0, 0.0);
      }

      for (int i = 0; i < 6; i++) {
         level.sendParticles(ParticleTypes.SOUL, zone.x, zone.y + 0.6 + i * 1.2, zone.z, 1, 0.08, 0.1, 0.08, 0.02);
      }

      if (player.tickCount % 20 == 0) {
         double pr = (time % 20L + 1L) / 20.0 * 8.0;
         drawRing(level, c, pr, 32, ParticleTypes.END_ROD);
      }
   }

   public static void cancelChannel(ServerPlayer player) {
      if (CASTING.remove(player.getUUID()) != null) {
         PacketDistributor.sendToPlayer(player, new ZhouTianChannelPayload(0), new CustomPacketPayload[0]);
      }
   }

   public static boolean isChanneling(ServerPlayer player) {
      return CASTING.containsKey(player.getUUID());
   }

   private static List<LivingEntity> entitiesInZone(ServerLevel level, ZhouTianFuZhenSkill.Zone zone) {
      AABB box = new AABB(zone.x - 8.0, zone.y - 3.0, zone.z - 8.0, zone.x + 8.0, zone.y + 3.0, zone.z + 8.0);
      return level.getEntitiesOfClass(LivingEntity.class, box, e -> com.myriad.combat.SkillTargets.alive(e) && !(e instanceof ArmorStand));
   }

   private static boolean isAllyOfOwner(ZhouTianFuZhenSkill.Zone zone, ServerPlayer p) {
      PlayerData pd = PlayerDataProvider.get(p);
      return pd != null && pd.getFaction() != null && zone.ownerFaction != null ? pd.getFaction() == zone.ownerFaction : true;
   }

   private static boolean isEnemyOfOwner(ZhouTianFuZhenSkill.Zone zone, LivingEntity e) {
      if (e.getUUID().equals(zone.owner)) {
         return false;
      } else {
         return e instanceof ServerPlayer p ? !isAllyOfOwner(zone, p) : true;
      }
   }

   public static boolean inAllyZone(LivingEntity victim) {
      if (victim instanceof ServerPlayer vp) {
         for (ZhouTianFuZhenSkill.Zone zone : ZONES.values()) {
            if (isAllyOfOwner(zone, vp) && inRadius(zone, victim)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean inRadius(ZhouTianFuZhenSkill.Zone zone, LivingEntity e) {
      double dx = e.getX() - zone.x;
      double dz = e.getZ() - zone.z;
      double dy = e.getY() - zone.y;
      return dx * dx + dz * dz <= 64.0 && Math.abs(dy) <= 3.0;
   }

   public static void clearFor(ServerPlayer ownerPlayer) {
      CASTING.remove(ownerPlayer.getUUID());
      ZONES.remove(ownerPlayer.getUUID());
   }

   private static void drawRing(ServerLevel level, Vec3 center, double radius, int count, ParticleOptions particle) {
      for (int i = 0; i < count; i++) {
         double a = (Math.PI * 2) * i / count;
         level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static void drawArc(ServerLevel level, Vec3 center, double radius, double startAngle, double sweep, int count, ParticleOptions particle) {
      for (int i = 0; i < count; i++) {
         double a = startAngle + sweep * i / count;
         level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private static final class PendingCast {
      double x;
      double y;
      double z;
      long startTick;
      int level;
      float sp;
   }

   private static final class Zone {
      UUID owner;
      Faction ownerFaction;
      double x;
      double y;
      double z;
      long expireTick;
      int level;
      float sp;
   }
}
