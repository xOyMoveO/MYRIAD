package com.myriad.character.skills.tianxing;

import com.myriad.character.Skill;
import com.myriad.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 雷部·天刑 被动（占位）。
 */
public class TianXingPassiveSkill extends Skill {
   public TianXingPassiveSkill() {
      super("tianxing_passive", 0, 0.0F);
   }

   public boolean isPassive() {
      return true;
   }

   public void cast(ServerPlayer player, PlayerData data) {
   }
}
