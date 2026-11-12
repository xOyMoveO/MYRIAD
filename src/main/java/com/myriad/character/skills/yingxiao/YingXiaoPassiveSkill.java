package com.myriad.character.skills.yingxiao;

import com.myriad.character.Skill;
import com.myriad.player.PlayerData;
import net.minecraft.server.level.ServerPlayer;

/**
 * 影刹·夜影 被动【暗影猎手】：从敌人背后造成的普攻及技能伤害提高30%。
 * 实际伤害修正在 PlayerDataProvider 的服务端统一伤害入口完成。
 */
public class YingXiaoPassiveSkill extends Skill {
   public YingXiaoPassiveSkill() {
      super("yingxiao_passive", 0, 0.0F);
   }

   public boolean isPassive() {
      return true;
   }

   public void cast(ServerPlayer player, PlayerData data) {
   }
}
