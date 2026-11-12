package com.myriad.character.skills.yingxiao;

import com.myriad.character.Skill;
import com.myriad.player.PlayerData;
import com.myriad.player.ModShieldSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.myriad.registry.ModAttributes;

/**
 * 影刹·夜影 R 大招【灵盾】：获得高额护盾（560+210/级+150%物攻），
 * 携带灵源时护盾 +50%。
 */
public class LingDunSkill extends Skill {
   private static final int DURATION = 80;
   private static final double SHIELD_BASE = 560.0;
   private static final double SHIELD_AD_RATIO = 1.5;
   private static final double CARRY_BONUS = 1.5;

   public LingDunSkill() {
      super("lingdun", 1000, 144.0F);
   }


   public boolean isUltimate() {
      return true;
   }

   public String levelEffects(int level) {
      int shield = 560 + 210 * (level - 1);
      return "Lv." + level + "：护盾 " + shield + "+150%物攻（携带灵源+50%），持续4秒";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      int lv = data.skillLevel("lingdun");
      double shield = SHIELD_BASE + 210.0 * (lv - 1)
              + SHIELD_AD_RATIO * player.getAttributeValue(ModAttributes.PHYSICAL_ATTACK);
      boolean carrying = data.getCarrying() > 0;
      if (carrying) {
         shield *= 1.5;
         player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0, false, false));
      }

      ModShieldSystem.grant(player, "yingxiao_lingdun", (float)shield, DURATION);
      data.setBuff("lingdun", 80);
      ServerLevel level = player.level();
      level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 1.0, 0.5, 0.1);
      level.sendParticles(ParticleTypes.GLOW_SQUID_INK, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.4, 0.5, 0.4, 0.02);
      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
   }
}
