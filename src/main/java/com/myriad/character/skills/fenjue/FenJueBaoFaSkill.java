package com.myriad.character.skills.fenjue;

import com.myriad.character.Skill;
import com.myriad.combat.DamageCalc;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 火部·焚诀 E 技能【焚诀爆发】：蓄力 1 秒（火焰环绕周身汇聚，期间可缓慢移动），
 * 随后向四周释放爆发，对自身周围 8 格造成 30+100%攻击力 物理伤害；
 * 自身生命越低伤害越高（每损失 1% 生命提升 0.5%，最高 +50%）。
 */
public class FenJueBaoFaSkill extends Skill {
   private static final double RADIUS = 8.0;
   private static final int CHANNEL_TICKS = 20;
   private static final Map<UUID, Long> CHANNEL = new HashMap<>();

   public FenJueBaoFaSkill() {
      super("fenjuebaofa", 900, 216.0F);
   }

   @Override
   public int getCooldownTicks(int level) {
      return Math.max(20, 900 - Math.max(0, level - 1) * 100);
   }

   public String levelEffects(int level) {
      return "Lv." + level + "：蓄力1秒后向四周爆发，伤害 30+"
              + (100 + 5 * (level - 1))
              + "%攻击力 物理；生命越低伤害越高（每损失1%提升0.5%，最高+50%）";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      CHANNEL.put(player.getUUID(), player.level().getGameTime());
      data.setBuff("fenjuebaofa", 20);
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.9F);
      player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5F, 0.8F);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      Long start = CHANNEL.get(player.getUUID());
      if (start != null) {
         ServerLevel level = player.level();
         long now = level.getGameTime();
         if (!player.isAlive()) {
            CHANNEL.remove(player.getUUID());
            data.setBuff("fenjuebaofa", 0);
         } else {
            long elapsed = now - start;
            if (elapsed >= 20L) {
               CHANNEL.remove(player.getUUID());
               data.setBuff("fenjuebaofa", 0);
               release(player, data);
            } else {
               player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 1, false, false, false));
               Vec3 pos = player.position();
               double progress = elapsed / 20.0;
               if (player.tickCount % 2 == 0) {
                  double ang = now * 0.18;

                  for (int i = 0; i < 10; i++) {
                     double a = ang + i * Math.PI * 2.0 / 10.0;
                     double r = 1.6 + Math.sin(now * 0.3 + i * 1.3) * 0.35;
                     double h = 1.1 + Math.sin(now * 0.4 + i * 2.1) * 0.35;
                     level.sendParticles(ParticleTypes.FLAME, pos.x + Math.cos(a) * r, pos.y + h, pos.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
                  }

                  for (int i = 0; i < 6; i++) {
                     double a = -now * 0.4 + i * Math.PI * 2.0 / 6.0;
                     double r = 0.9 + Math.sin(now * 0.5 + i) * 0.15;
                     level.sendParticles(ParticleTypes.LAVA, pos.x + Math.cos(a) * r, pos.y + 1.0, pos.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
                  }

                  level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.2, pos.z, 3, 0.3, 0.4, 0.3, 0.03);
               }

               if (player.tickCount % 10 == 0) {
                  level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.0, pos.z, 14, 0.3, 0.2, 0.3, -0.1);
                  level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.0F + (float)progress);
               }

               if (player.tickCount % 5 == 0) {
                  level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.8F + (float)progress * 0.7F);
               }

               if (player.tickCount % 10 == 0) {
                  level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.7F, 0.8F + (float)progress * 0.6F);
               }

               if (elapsed == 10L) {
                  level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
                  level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.9F, 0.6F);
               }
            }
         }
      }
   }

   private static void release(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
      int lv = Math.max(1, data.skillLevel("fenjuebaofa"));
      float dmg = 120.0F + (4.0F + 0.20F * (lv - 1)) * ad;
      double maxHp = player.getAttributeValue(Attributes.MAX_HEALTH);
      double missingPct = maxHp <= 0.0 ? 0.0 : Math.max(0.0, (maxHp - player.getHealth()) / maxHp * 100.0);
      double bonus = Math.min(50.0, missingPct * 0.5);
      dmg *= (float)(1.0 + bonus / 100.0);
      Vec3 pos = player.position();

      for (int ring = 0; ring < 6; ring++) {
         double rr = 1.2 + ring * 6.8 / 5.0;
         int n = (int)(20.0 + rr * 5.0);
         double h = Math.sqrt(Math.max(0.0, 1.0 - rr / 8.0 * (rr / 8.0))) * 5.6;

         for (int i = 0; i < n; i++) {
            double a = (Math.PI * 2) * i / n;
            level.sendParticles(ParticleTypes.FLAME, pos.x + Math.cos(a) * rr, pos.y + 0.15, pos.z + Math.sin(a) * rr, 1, 0.0, 0.05, 0.0, 0.02);
            level.sendParticles(ParticleTypes.FLAME, pos.x + Math.cos(a) * rr, pos.y + 0.5 + h, pos.z + Math.sin(a) * rr, 1, 0.0, 0.02, 0.0, 0.01);
            if (i % 5 == 0) {
               level.sendParticles(ParticleTypes.SMOKE, pos.x + Math.cos(a) * rr, pos.y + 0.8 + h * 0.5, pos.z + Math.sin(a) * rr, 1, 0.0, 0.3, 0.0, 0.02);
            }
         }
      }

      for (int i = 0; i < 30; i++) {
         double h = level.getRandom().nextDouble() * 6.0;
         level.sendParticles(
            ParticleTypes.FLAME,
            pos.x + (level.getRandom().nextDouble() - 0.5) * 0.8,
            pos.y + 0.3 + h,
            pos.z + (level.getRandom().nextDouble() - 0.5) * 0.8,
            2,
            0.1,
            0.15,
            0.1,
            0.06
         );
      }

      level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.5, pos.z, 16, 6.4, 0.3, 6.4, 0.08);
      level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.8F);
      level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.9F, 1.1F);
      level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.9F, 0.7F);
      AABB box = new AABB(pos.x - 8.0, pos.y - 2.0, pos.z - 8.0, pos.x + 8.0, pos.y + 4.0, pos.z + 8.0);

      for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex))) {
         DamageCalc.dealSkillDamage(player, e, dmg, Kind.PHYSICAL, 2);
         level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 14, 0.3, 0.3, 0.3, 0.06);
      }
   }

   public static void clearFor(UUID playerId) {
      CHANNEL.remove(playerId);
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
}
