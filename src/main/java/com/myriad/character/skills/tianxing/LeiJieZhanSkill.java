package com.myriad.character.skills.tianxing;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 雷部·天刑 W 技能【雷劫斩】：短暂蓄力后向前劈出新月形雷劫（直线贯穿最远 18 格），
 * 命中敌人造成物理伤害并减速。
 */
public class LeiJieZhanSkill extends Skill {
   private static final double LINE_RANGE = 18.0;
   private static final double LINE_WIDTH = 1.5;
   private static final int CHARGE_TICKS = 10;
   private static final double SLASH_SPEED = 0.75;
   private static final int SLASH_TICKS = 24;
   private static final double CRESCENT_HALF = 1.5;
   private static final double CRESCENT_BULGE = 0.9;
   private static final Map<UUID, Long> CHARGE_UNTIL = new HashMap<>();
   private static final Map<UUID, LeiJieZhanSkill.Slash> SLASHES = new HashMap<>();

   public LeiJieZhanSkill() {
      super("leijiezhan", 200, 108.0F);
   }

   public String levelEffects(int level) {
      int dmg = 20 + 5 * (level - 1);
      return "Lv." + level + "：伤害 " + dmg + "+" + (80 + 10 * (level - 1))
              + "%攻击力；月牙剑气15格/秒飞行1.2秒（共18格），减速30%持续2秒";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      CHARGE_UNTIL.put(player.getUUID(), player.level().getGameTime() + 10L);
      data.setBuff("leijiezhan", 10);
      level.playSound(null, player.getX(), player.getY(), player.getZ(), com.myriad.registry.ModSounds.foley("thunder"), SoundSource.PLAYERS, 0.6F, 0.8F);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      long now = level.getGameTime();
      Long end = CHARGE_UNTIL.get(player.getUUID());
      if (end != null) {
         if (now < end) {
            Vec3 dir = player.getLookAngle().normalize();
            Vec3 gather = player.getEyePosition().add(dir.scale(2.0));

            for (int i = 0; i < 12; i++) {
               double ang = player.getRandom().nextDouble() * Math.PI * 2.0;
               double r = 1.0 + player.getRandom().nextDouble() * 3.5;
               double sx = player.getX() + Math.cos(ang) * r;
               double sy = player.getY() + player.getRandom().nextDouble() * 2.6;
               double sz = player.getZ() + Math.sin(ang) * r;
               Vec3 v = gather.subtract(sx, sy, sz).normalize().scale(0.18);
               level.sendParticles(ParticleTypes.ELECTRIC_SPARK, sx, sy, sz, 1, v.x, v.y, v.z, 0.02);
               if (i % 3 == 0) {
                  level.sendParticles(ParticleTypes.END_ROD, sx, sy, sz, 1, v.x, v.y, v.z, 0.02);
               }
            }

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, gather.x, gather.y, gather.z, 6, 0.35, 0.35, 0.35, 0.03);
            level.sendParticles(ParticleTypes.END_ROD, gather.x, gather.y, gather.z, 3, 0.2, 0.2, 0.2, 0.02);
            return;
         }

         CHARGE_UNTIL.remove(player.getUUID());
         data.setBuff("leijiezhan", 0);
         LeiJieZhanSkill.Slash slash = new LeiJieZhanSkill.Slash();
         slash.level = data.skillLevel("leijiezhan");
         slash.ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
         slash.dir = player.getLookAngle().normalize();
         slash.from = player.position().add(0.0, 1.0, 0.0);
         SLASHES.put(player.getUUID(), slash);
         Vec3 from = slash.from;
         level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.0F);
         level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 0.9F);
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 28, 1.2, 0.9, 1.2, 0.12);
         level.sendParticles(ParticleTypes.CRIT, from.x, from.y, from.z, 14, 0.9, 0.7, 0.9, 0.06);
      }

      LeiJieZhanSkill.Slash slash = SLASHES.get(player.getUUID());
      if (slash != null) {
         slash.traveled += 0.75;
         drawCrescent(level, slash.from, slash.dir, slash.traveled);
         hitEnemies(level, player, slash.from, slash.dir, slash);
         if (slash.traveled >= 18.0) {
            SLASHES.remove(player.getUUID());
         }
      }
   }

   private static void drawCrescent(ServerLevel level, Vec3 from, Vec3 dir, double traveled) {
      Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      Vec3 center = from.add(dir.scale(traveled));

      for (double u = -1.5; u <= 1.501; u += 0.1) {
         double bulge = 0.9 * (1.0 - u / 1.5 * (u / 1.5));
         Vec3 p = center.add(right.scale(u)).add(dir.scale(bulge));
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
         if (Math.abs(u - Math.round(u)) < 0.001) {
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.01, 0.01, 0.01, 0.01);
         }
      }
   }

   private static void hitEnemies(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 dir, LeiJieZhanSkill.Slash slash) {
      Vec3 center = from.add(dir.scale(slash.traveled));
      AABB box = new AABB(center.x - 1.5, center.y - 1.5, center.z - 1.5, center.x + 1.5, center.y + 2.5, center.z + 1.5).expandTowards(dir.scale(0.3));

      for (LivingEntity e : level.getEntitiesOfClass(
         LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex) && !slash.hit.contains(ex.getUUID())
      )) {
         slash.hit.add(e.getUUID());
         float dmg = 80.0F + 20.0F * (slash.level - 1)
                 + (3.2F + 0.4F * (slash.level - 1)) * slash.ad;
         DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 1);
         e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 18, 0.3, 0.3, 0.3, 0.1);
         level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
      }
   }

   public static void clearFor(UUID playerId) {
      CHARGE_UNTIL.remove(playerId);
      SLASHES.remove(playerId);
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

   private static final class Slash {
      double traveled = 0.0;
      Vec3 dir;
      Vec3 from;
      int level;
      float ad;
      final Set<UUID> hit = new HashSet<>();
   }
}
