package com.myriad.character.skills.xuanchen;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 玄阵·墨尘 Q 技能【灵缚】：射出灵缚锁链（直线飞射，最远 20 格），
 * 命中首个敌人造成法术伤害并强力束缚减速（1.5 秒）。
 */
public class LingFuSkill extends Skill {
   private static final double SPEED = 2.5;
   private static final double MAX_RANGE = 20.0;
   private static final double HIT_RADIUS = 1.05;
   private static final double BLOCK_SAMPLE_STEP = 0.25;
   private static final int ROOT_TICKS = 30;
   private static final Map<UUID, List<LingFuSkill.Chain>> CHAINS = new HashMap<>();

   public LingFuSkill() {
      super("lingfu", 200, 90.0F);
   }

   @Override
   public int getCooldownTicks(int level) {
      return Math.max(20, 200 - Math.max(0, level - 1) * 20);
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：伤害 " + (10 + 10 * (level - 1)) + "+"
              + (50 + 5 * (level - 1)) + "%法术强度；禁锢1.5秒；冷却 "
              + (10 - Math.max(0, level - 1)) + " 秒";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      Vec3 dir = player.getLookAngle().normalize();
      if (!(dir.lengthSqr() < 1.0E-4)) {
         Vec3 pos = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0).add(dir.scale(0.6));
         LingFuSkill.Chain chain = new LingFuSkill.Chain();
         chain.pos = pos;
         chain.dir = dir;
         chain.level = data.skillLevel("lingfu");
         chain.sp = (float)player.getAttributeValue(ModAttributes.SPELL_POWER);
         ServerLevel level = player.level();
         CHAINS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(chain);
         level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.7F, 1.2F);
         level.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y, pos.z, 10, 0.1, 0.1, 0.1, 0.03);
      }
   }

   public static void tickChain(ServerPlayer player, PlayerData data) {
      List<LingFuSkill.Chain> list = CHAINS.get(player.getUUID());
      if (list != null && !list.isEmpty()) {
         ServerLevel level = player.level();
         list.removeIf(c -> tickOne(level, player, c));
         if (list.isEmpty()) {
            CHAINS.remove(player.getUUID());
         }
      }
   }

   private static boolean tickOne(ServerLevel level, ServerPlayer player, LingFuSkill.Chain c) {
      Vec3 next = c.pos.add(c.dir.scale(2.5));
      LivingEntity target = findFirstEnemy(level, player, c.pos, next);
      if (target != null) {
         float dmg = 40.0F + 40.0F * (c.level - 1)
                 + (2.0F + 0.20F * (c.level - 1)) * c.sp;
         DamageCalc.dealSkillDamage(player, target, dmg, Kind.MAGIC, 0);
         target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 7, false, false));
         level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 22, 0.3, 0.4, 0.3, 0.06);
         level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, 0.2, 0.3, 0.2, 0.04);
         level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
         return true;
      }

      Vec3 blockHit = firstBlockHit(level, c.pos, next);
      if (blockHit != null) {
         level.sendParticles(ParticleTypes.SCULK_SOUL, blockHit.x, blockHit.y, blockHit.z, 12, 0.2, 0.2, 0.2, 0.04);
         return true;
      }

      Vec3 origin = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
      if (next.distanceToSqr(origin) >= 400.0) {
         return true;
      }

      level.sendParticles(ParticleTypes.SCULK_SOUL, next.x, next.y, next.z, 2, 0.03, 0.03, 0.03, 0.02);
      level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 1, 0.02, 0.02, 0.02, 0.02);
      if (player.tickCount % 4 == 0) {
         level.playSound(null, next.x, next.y, next.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.3F, 1.6F);
      }

      c.pos = next;
      return false;
   }

   private static Vec3 firstBlockHit(ServerLevel level, Vec3 from, Vec3 to) {
      double dist = from.distanceTo(to);
      int steps = Math.max(1, (int)Math.ceil(dist / 0.25));

      for (int i = 1; i <= steps; i++) {
         Vec3 p = from.lerp(to, (double)i / steps);
         BlockPos bp = BlockPos.containing(p.x, p.y, p.z);
         if (level.getBlockState(bp).isSolid()) {
            return p;
         }
      }

      return null;
   }

   private static LivingEntity findFirstEnemy(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 to) {
      AABB box = new AABB(
         Math.min(from.x, to.x) - 0.8,
         Math.min(from.y, to.y) - 0.8,
         Math.min(from.z, to.z) - 0.8,
         Math.max(from.x, to.x) + 0.8,
         Math.max(from.y, to.y) + 0.8,
         Math.max(from.z, to.z) + 0.8
      );
      Vec3 delta = to.subtract(from);
      double lenSqr = delta.lengthSqr();
      LivingEntity best = null;
      double bestT = Double.MAX_VALUE;

      for (LivingEntity e : level.getEntitiesOfClass(
         LivingEntity.class, box, ex -> ex != caster && ex.isAlive() && !(ex instanceof ArmorStand) && !isAlly(caster, ex)
      )) {
         Vec3 rel = e.getBoundingBox().getCenter().subtract(from);
         double t = rel.dot(delta) / lenSqr;
         if (!(t < 0.0) && !(t > 1.0)) {
            Vec3 closest = from.add(delta.scale(t));
            double radius = 0.8 + Math.max(e.getBbWidth(), e.getBbHeight()) * 0.3;
            if (closest.distanceToSqr(e.getBoundingBox().getCenter()) <= radius * radius && t < bestT) {
               bestT = t;
               best = e;
            }
         }
      }

      return best;
   }

   private static boolean isAlly(ServerPlayer caster, LivingEntity other) {
      return com.myriad.combat.SkillTargets.ally(caster, other);
   }

   public static void clearFor(UUID playerId) {
      CHAINS.remove(playerId);
   }

   private static final class Chain {
      Vec3 pos;
      Vec3 dir;
      int level;
      float sp;
   }
}
