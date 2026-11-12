package com.myriad.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import com.myriad.game.DefenseTowerEntity;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.game.JiarenManager;
import com.myriad.game.LaneSystem;

/** Shared ability target filter, also excludes buildings from secondary effects and hit counts. */
public final class SkillTargets {
    private SkillTargets() { }
    public static boolean alive(Entity entity) {
        return entity != null && entity.isAlive() && !(entity instanceof DefenseTowerEntity);
    }

    /** 玩家、友军英雄、友军兵线单位与友方训练假人使用同一阵营判定。 */
    public static boolean ally(ServerPlayer caster, Entity target) {
        if (caster == null || target == null) return false;
        if (target == caster || target instanceof LivingEntity living
                && JiarenManager.isFriendlyDummy(caster, living)) return true;
        GameManager manager = GameManager.get();
        Faction own = manager == null ? null : manager.factionOf(caster);
        Faction other = LaneSystem.factionOf(target);
        return own != null && own == other;
    }

    public static boolean enemy(ServerPlayer caster, Entity target) {
        return alive(target) && !ally(caster, target);
    }
}
