package com.myriad.character.skills.yingxiao;

import com.myriad.character.Skill;
import com.myriad.player.PlayerData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 影刹·夜影 W 技能【花影】：隐匿，获得隐身 + 加速（4~8 秒），携带灵源时持续更久。
 */
public class HuaYingSkill extends Skill {
   private static final int BASE_DURATION = 80;
   private static final int CARRY_DURATION = 160;

   public HuaYingSkill() {
      super("huaying", 280, 108.0F);
   }


   public String levelEffects(int level) {
      double secs = 4.0 + 0.5 * (level - 1);
      String s = String.format("%.1f", secs);
      return "Lv." + level + "：隐身 " + s + "s（携带灵源" + String.format("%.1f", secs * 2.0) + "s），移速+20%";
   }

   public void cast(ServerPlayer player, PlayerData data) {
      int duration = data.getCarrying() > 0 ? 160 : 80;
      int lv = data.skillLevel("huaying");
      duration += 10 * (lv - 1);
      player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false));
      player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 1, false, false));
      data.setStealthBonus(true);
      data.setBuff("huaying", duration);
      ServerLevel level = player.level();
      level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.4, 0.6, 0.4, 0.03);
      level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
      level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 1.0F);
   }
}
