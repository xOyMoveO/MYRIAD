package com.myriad.character.skills.yingxiao;

import com.mojang.math.Transformation;
import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.character.skills.sulan.FengQiangSkill;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import com.myriad.registry.ModItems;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Display.BillboardConstraints;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 影刹·夜影 E 技能【影杀】：掷出飞刀（物品展示实体），命中敌人造成物理伤害。
 */
public class YingShaSkill extends Skill {
   private static final double FLIGHT_SPEED = 2.0;
   private static final double MAX_RANGE = 15.0;
   private static final double HIT_RADIUS = 1.05;
   private static final int ROOT_TICKS = 60;
   private static final double BLOCK_SAMPLE_STEP = 0.25;
   private static final Map<UUID, List<YingShaSkill.Dagger>> DAGGERS = new HashMap<>();

   public YingShaSkill() {
      super("yingsha", 140, 90.0F);
   }

   // The dagger is spawned immediately: the first tick is the release, followed by follow-through.

   public String levelEffects(int level) {
      int dmg = 12 + 10 * (level - 1);
      return "Lv." + level + "：伤害 " + dmg + "+60%物攻；定身3秒";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      Vec3 dir = player.getLookAngle().normalize();
      if (!(dir.lengthSqr() < 1.0E-4)) {
         Vec3 pos = player.position().add(0.0, player.getBbHeight() * 0.8, 0.0).add(dir.scale(0.6));
         YingShaSkill.Dagger dagger = new YingShaSkill.Dagger();
         dagger.pos = pos;
         dagger.dir = dir;
         dagger.level = data.skillLevel("yingsha");
         dagger.ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
         ServerLevel level = player.level();
         dagger.visual = spawnSwordVisual(level, pos, dir);
         DAGGERS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(dagger);
         level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.8F, 1.2F);
         level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 8, 0.06, 0.06, 0.06, 0.05);
         level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 4, 0.04, 0.04, 0.04, 0.02);
      }
   }

   private static ItemDisplay spawnSwordVisual(ServerLevel level, Vec3 pos, Vec3 dir) {
      if (EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof ItemDisplay display) {
         display.getSlot(0).set(new ItemStack(ModItems.YINGCI.get()));
         display.setItemTransform(ItemDisplayContext.FIXED);
         display.setBillboardConstraints(BillboardConstraints.FIXED);
         orientDagger(display, dir);
         display.setGlowColorOverride(-11141121);
         display.setPos(pos.x, pos.y, pos.z);
         level.addFreshEntity(com.myriad.game.ModEntityTracker.markGenerated(display));
         return display;
      } else {
         return null;
      }
   }

   /** 影刺剑柄位于模型低 Y 端，剑尖沿本地 +Y；直接将该轴旋转到真实飞行方向。 */
   private static void orientDagger(ItemDisplay display, Vec3 dir) {
      if (dir.lengthSqr() < 1.0E-6) {
         return;
      }
      Vec3 normalized = dir.normalize();
      Quaternionf aim = new Quaternionf().rotationTo(
         0.0F, 1.0F, 0.0F,
         (float)normalized.x, (float)normalized.y, (float)normalized.z
      );
      display.setYRot(0.0F);
      display.setXRot(0.0F);
      display.setTransformation(new Transformation(
         new Vector3f(0.0F, 0.0F, 0.0F), aim,
         new Vector3f(2.0F, 2.0F, 2.0F), new Quaternionf()
      ));
   }

   public static void tickDagger(ServerPlayer player, PlayerData data) {
      List<YingShaSkill.Dagger> list = DAGGERS.get(player.getUUID());
      if (list != null && !list.isEmpty()) {
         ServerLevel level = player.level();
         list.removeIf(dagger -> tickOne(level, player, dagger));
         if (list.isEmpty()) {
            DAGGERS.remove(player.getUUID());
         }
      }
   }

   private static boolean tickOne(ServerLevel level, ServerPlayer player, YingShaSkill.Dagger dagger) {
      Vec3 next = dagger.pos.add(dagger.dir.scale(2.0));
      if (FengQiangSkill.blocksProjectile(level, dagger.pos, next)
              || com.myriad.character.skills.feiyu.FeiYuSkills.blocksProjectile(level, player, dagger.pos, next)) {
         removeVisual(dagger);
         return true;
      }
      LivingEntity target = findFirstEnemy(level, player, dagger.pos, next);
      if (target != null) {
         float dmg = 48.0F + 40.0F * (dagger.level - 1) + 2.4F * dagger.ad;
         DamageCalc.dealSkillDamage(player, target, dmg, Kind.PHYSICAL, 2);
         target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 7, false, false));
         level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 20, 0.3, 0.3, 0.3, 0.1);
         level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
         level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
         removeVisual(dagger);
         return true;
      }

      Vec3 blockHit = firstBlockHit(level, dagger.pos, next);
      if (blockHit != null) {
         level.sendParticles(ParticleTypes.END_ROD, blockHit.x, blockHit.y, blockHit.z, 14, 0.2, 0.2, 0.2, 0.05);
         level.sendParticles(ParticleTypes.CRIT, blockHit.x, blockHit.y, blockHit.z, 10, 0.2, 0.2, 0.2, 0.05);
         level.playSound(null, blockHit.x, blockHit.y, blockHit.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8F, 1.1F);
         removeVisual(dagger);
         return true;
      }

      Vec3 origin = player.position().add(0.0, player.getBbHeight() * 0.8, 0.0);
      if (next.distanceToSqr(origin) >= 225.0) {
         removeVisual(dagger);
         return true;
      }

      if (dagger.visual != null && dagger.visual.isAlive()) {
         dagger.visual.setPos(next.x, next.y, next.z);
         orientDagger(dagger.visual, dagger.dir);
      }

      level.sendParticles(ParticleTypes.END_ROD, next.x, next.y, next.z, 2, 0.02, 0.02, 0.02, 0.02);
      level.sendParticles(ParticleTypes.CRIT, next.x, next.y, next.z, 1, 0.02, 0.02, 0.02, 0.02);
      dagger.pos = next;
      return false;
   }

   private static void removeVisual(YingShaSkill.Dagger dagger) {
      if (dagger.visual != null && dagger.visual.isAlive()) {
         dagger.visual.discard();
      }

      dagger.visual = null;
   }

   public static void clearFor(UUID playerId) {
      List<YingShaSkill.Dagger> list = DAGGERS.remove(playerId);
      if (list != null) {
         for (YingShaSkill.Dagger dagger : list) {
            removeVisual(dagger);
         }
      }
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

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != caster && ex.isAlive() && !isAlly(caster, ex))) {
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

   private static final class Dagger {
      Vec3 pos;
      Vec3 dir;
      int level;
      float ad;
      ItemDisplay visual;
   }
}
