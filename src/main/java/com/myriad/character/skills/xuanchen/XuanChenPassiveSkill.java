package com.myriad.character.skills.xuanchen;

import com.myriad.character.Skill;
import com.myriad.game.Faction;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * 玄阵·墨尘 被动：与队友结阵，技能冷却缩短并为附近友军回蓝。
 */
public class XuanChenPassiveSkill extends Skill {
   public static final double MANA_BURST_RANGE = 8.0;
   public static final float MANA_PER_CAST = 5.0F;

   public XuanChenPassiveSkill() {
      super("xuanchen_passive", 0, 0.0F);
   }

   public boolean isPassive() {
      return true;
   }

   public void cast(ServerPlayer player, PlayerData data) {
   }

   public static boolean nearAlly(ServerPlayer player) {
      Faction faction = PlayerDataProvider.get(player).getFaction();
      if (faction == null) return false;
      return player.level().players().stream().anyMatch(other -> other != player && other.isAlive()
              && !other.isSpectator() && PlayerDataProvider.get(other).getFaction() == faction
              && player.distanceToSqr(other) <= 64 && player.hasLineOfSight(other));
   }

   public static void onSkillCast(ServerPlayer player) {
      if (player.level() instanceof ServerLevel level) {
         PlayerData self = PlayerDataProvider.get(player);
         if (self != null) {
            Faction my = self.getFaction();
            AABB box = new AABB(player.getX() - 8.0, player.getY() - 3.0, player.getZ() - 8.0, player.getX() + 8.0, player.getY() + 5.0, player.getZ() + 8.0);

            for (Player p : level.getEntitiesOfClass(Player.class, box, px -> px.isAlive() && !px.isSpectator()
                    && px != player && player.distanceToSqr(px) <= 64)) {
               if (p instanceof ServerPlayer sp) {
                  PlayerData pd = PlayerDataProvider.get(sp);
                  if (pd != null && pd.getFaction() != null && my != null && pd.getFaction() == my) {
                     double max = sp.getAttributeValue(ModAttributes.MAX_MANA);
                     pd.setMana((float)Math.min(max, pd.getMana() + 5.0F));
                  }
               }
            }

            double maxSelf = player.getAttributeValue(ModAttributes.MAX_MANA);
            self.setMana((float)Math.min(maxSelf, self.getMana() + 5.0F));
            level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.5, 0.3, 0.5, 0.02);
         }
      }
   }
}
