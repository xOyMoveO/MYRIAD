package com.myriad.character.skills.fenjue;

import com.myriad.character.Skill;
import com.myriad.player.PlayerData;
import com.myriad.registry.ModAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;

/**
 * 火部·焚诀 被动【焚天战意】：生命不高于 20% 时
 * 攻击力 +35%，且每秒回复 2 点法力。
 */
public class FenJuePassiveSkill extends Skill {
   private static final Identifier AD_MOD = Identifier.parse("myriad:fenjue_passive_ad");
   private static final Map<UUID, Long> LAST_MP_TICK = new HashMap<>();

   public FenJuePassiveSkill() {
      super("fenjue_passive", 0, 0.0F);
   }

   public boolean isPassive() {
      return true;
   }

   public void cast(ServerPlayer player, PlayerData data) {
   }

   public static void tick(ServerPlayer player, PlayerData data) {
      boolean near = "fenjue".equals(data.getCharacterId()) && player.isAlive() && !player.isSpectator()
              && player.getHealth() <= player.getMaxHealth() * .2F;
      AttributeInstance ad = player.getAttribute(ModAttributes.PHYSICAL_ATTACK);
      if (ad != null) {
         if (near && !ad.hasModifier(AD_MOD)) {
            ad.addTransientModifier(new AttributeModifier(AD_MOD,
                    .35, Operation.ADD_MULTIPLIED_TOTAL));
         } else if (!near && ad.hasModifier(AD_MOD)) {
            ad.removeModifier(AD_MOD);
         }
      }

      long now = player.level().getGameTime();
      if (near) {
         long last = LAST_MP_TICK.getOrDefault(player.getUUID(), 0L);
         if (now - last >= 20L) {
            LAST_MP_TICK.put(player.getUUID(), now);
            double max = player.getAttributeValue(ModAttributes.MAX_MANA);
            data.setMana((float)Math.min(max, data.getMana() + 2.0F));
         }

         data.setBuff("fenjue_passive", 20);
      } else {
         data.setBuff("fenjue_passive", 0);
      }
   }

   public static void clearFor(UUID playerId) {
      LAST_MP_TICK.remove(playerId);
   }
}
