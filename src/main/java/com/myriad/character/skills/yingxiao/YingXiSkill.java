package com.myriad.character.skills.yingxiao;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.network.NetworkSetup.DashPayload;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 影刹·夜影 Q 技能【影袭】：朝视角方向高速冲刺（含垂直方向），
 * 穿透沿途敌人造成物理伤害；首个命中若为背击则伤害翻倍并眩晕。
 */
public class YingXiSkill extends Skill {
   private static final double DASH_RANGE = 5.0;
   private static final double DASH_SPEED = 1.0;
   private static final int DASH_TICKS = 6;
   private static final int STUN_TICKS = 20;
   private static final Map<UUID, YingXiSkill.Dash> DASHES = new HashMap<>();

   public YingXiSkill() {
      super("yingxi", 120, 72.0F);
   }


   public String levelEffects(int level) {
      int dmg = 15 + 8 * (level - 1);
      return "Lv." + level + "：伤害 " + dmg + "+" + (20 + 5 * (level - 1))
              + "%物攻；背后命中伤害翻倍并眩晕1秒";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      Vec3 dir = player.getLookAngle().normalize();
      if (!(dir.lengthSqr() < 1.0E-4)) {
         YingXiSkill.Dash dash = new YingXiSkill.Dash();
         dash.dir = dir;
         dash.lastPos = player.position();
         DASHES.put(player.getUUID(), dash);
         PacketDistributor.sendToPlayer(player, new DashPayload((float)dir.x, (float)dir.y, (float)dir.z, 6), new CustomPacketPayload[0]);
         player.setDeltaMovement(dir.x * 1.0, dir.y * 1.0, dir.z * 1.0);
         dash.remainingTicks--;
         ServerLevel level = player.level();
         level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.6F, 1.6F);
         level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.4, player.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
      }
   }

   public static void tickDash(ServerPlayer player, PlayerData data) {
      YingXiSkill.Dash dash = DASHES.get(player.getUUID());
      if (dash != null) {
         ServerLevel level = player.level();
         Vec3 previous = dash.lastPos;
         double movedNow = player.position().distanceTo(dash.lastPos);
         if (movedNow < 0.05 && dash.moved > 0.0) {
            finish(player, dash, level);
         } else {
            dash.moved += movedNow;
            dash.lastPos = player.position();
            level.sendParticles(
               ParticleTypes.CRIT, player.getX() + dash.dir.x * 1.2, player.getY() + 1.0, player.getZ() + dash.dir.z * 1.2, 2, 0.1, 0.1, 0.1, 0.05
            );
            for (LivingEntity target : com.myriad.combat.DashHitbox.targets(
                    player, previous, player.position(), 1.2, dash.hitTargets)) {
               boolean first = dash.hitTargets.isEmpty();
               dash.hitTargets.add(target.getUUID());
               boolean behind = isBehind(player, target);
               float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
               int lv = data.skillLevel("yingxi");
               float base = 60.0F + 32.0F * (lv - 1) + (0.8F + 0.2F * (lv - 1)) * ad;
               if (!first) {
                  base *= 0.5F;
               }

               if (behind && first) {
                  base *= 2.0F;
                  target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 7, false, false));
               }

               DamageCalc.dealSkillDamage(player, target, base, Kind.PHYSICAL, 0);
               level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 18, 0.3, 0.3, 0.3, 0.1);
               level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
            }

            if (dash.remainingTicks > 0) {
               player.setDeltaMovement(dash.dir.x * 1.0, dash.dir.y * 1.0, dash.dir.z * 1.0);
               dash.remainingTicks--;
            } else {
               finish(player, dash, level);
            }
         }
      }
   }

   private static void finish(ServerPlayer player, YingXiSkill.Dash dash, ServerLevel level) {
      DASHES.remove(player.getUUID());
      player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
      level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.4, player.getZ(), 12, 0.2, 0.2, 0.2, 0.02);
   }

   private static LivingEntity findEnemyAt(ServerLevel level, ServerPlayer caster, Vec3 pos, double eyeY) {
      AABB box = new AABB(pos.x - 0.75, eyeY - 0.5, pos.z - 0.75, pos.x + 0.75, eyeY + 1.5, pos.z + 0.75);
      Iterator var6 = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != caster && com.myriad.combat.SkillTargets.alive(e) && !isAlly(caster, e)).iterator();
      return var6.hasNext() ? (LivingEntity)var6.next() : null;
   }

   private static boolean isBehind(LivingEntity attacker, LivingEntity target) {
      Vec3 toAttacker = attacker.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
      double dist = toAttacker.horizontalDistanceSqr();
      if (dist < 1.0E-4) {
         return false;
      }

      Vec3 look = target.getLookAngle().multiply(1.0, 0.0, 1.0);
      return look.lengthSqr() > 1.0E-4 && look.normalize().dot(toAttacker.normalize()) < 0.0;
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

   private static final class Dash {
      Vec3 dir;
      double moved = 0.0;
      Vec3 lastPos;
      int remainingTicks = 6;
      Set<UUID> hitTargets = new HashSet<>();
   }
}
