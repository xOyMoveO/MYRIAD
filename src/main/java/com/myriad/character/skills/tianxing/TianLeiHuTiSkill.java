package com.myriad.character.skills.tianxing;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * 雷部·天刑 E 技能【天雷护体】：获得持续 5 秒的雷盾，期间每 1 秒对自身周围
 * 5 格敌人造成雷电物理伤害。
 */
public class TianLeiHuTiSkill extends Skill {
   private static final int DURATION = 100;
   private static final double AOE_RADIUS = 5.0;
   private static final int EXPAND_TICKS = 20;
   private static final int RING_PARTICLES = 56;
   private static final Map<UUID, Long> PULSE_START = new HashMap<>();

   public TianLeiHuTiSkill() {
      super("tianleihuti", 360, 144.0F);
   }

   public String levelEffects(int level) {
      int dmg = 13 + 5 * (level - 1);
      return "Lv." + level + "：护体5秒减伤20%，每秒脉冲伤害 " + dmg + "+20%攻击力（半径5格）";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      ServerLevel level = player.level();
      data.setBuff("tianleihuti", 100);
      PULSE_START.put(player.getUUID(), player.level().getGameTime());
      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.0F);
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      if (data.getBuff("tianleihuti") <= 0) {
         PULSE_START.remove(player.getUUID());
      } else {
         ServerLevel level = player.level();
         long now = level.getGameTime();
         long start = PULSE_START.getOrDefault(player.getUUID(), now);
         if (now - start >= 20L) {
            PULSE_START.put(player.getUUID(), now);
            int lv = data.skillLevel("tianleihuti");
            float ad = (float)player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
            float dmg = 52.0F + 20.0F * (lv - 1) + 0.8F * ad;
            AABB box = new AABB(player.getX() - 5.0, player.getY() - 2.0, player.getZ() - 5.0, player.getX() + 5.0, player.getY() + 4.0, player.getZ() + 5.0);

            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != player && ex.isAlive() && !isAlly(player, ex))) {
               DamageCalc.dealSkillDamage(player, e, dmg, Kind.MAGIC, 2);
               level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 18, 0.4, 0.4, 0.4, 0.1);
               level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.3, 0.3, 0.04);
            }

            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5F, 1.1F);
         } else {
            double progress = (now - start) / 20.0;
            double radius = 5.0 * progress;
            double x = player.getX();
            double y = player.getY() + 0.15;
            double z = player.getZ();

            for (int i = 0; i < 56; i++) {
               double a = (Math.PI * 2) * i / 56.0;
               double px = x + Math.cos(a) * radius;
               double pz = z + Math.sin(a) * radius;
               level.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, y, pz, 1, 0.0, 0.0, 0.0, 0.0);
               if (i % 4 == 0) {
                  level.sendParticles(ParticleTypes.CRIT, px, y + 0.3, pz, 1, 0.0, 0.02, 0.0, 0.01);
               }

               if (i % 7 == 0) {
                  level.sendParticles(ParticleTypes.END_ROD, px, y + 0.1, pz, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }

            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 2, 0.3, 0.3, 0.3, 0.02);
         }
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
}
