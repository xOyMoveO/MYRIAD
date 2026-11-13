package com.myriad.character.skills.sulan;

import com.myriad.character.Skill;
import com.myriad.combat.WindWallSystem;
import com.myriad.player.PlayerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Historical skill adapter; shared wall ownership lives outside character modules. */
public class FengQiangSkill extends Skill {
    public FengQiangSkill() { super("fengqiang", 400, 126.0F); }
    @Override public String levelEffects(int level) {
        return "Lv." + level + "：生成6×4风墙（长6宽0.6），从身前飞出2格，持续4秒，阻挡投射物；穿过减速50%";
    }
    @Override public void cast(ServerPlayer player, PlayerData data) { createWall(player, data, 80, true); }
    public static void createWall(ServerPlayer player, PlayerData data, int duration, boolean slowEnemies) {
        WindWallSystem.createWall(player, data, duration, slowEnemies);
    }
    public static void tick(ServerPlayer player, PlayerData data) { WindWallSystem.tick(player, data); }
    public static Vec3 firstEnemyHit(ServerPlayer caster, Vec3 from, Vec3 to) {
        return WindWallSystem.firstEnemyHit(caster, from, to);
    }
    public static boolean blocksProjectile(ServerLevel level, Vec3 from, Vec3 to) {
        return WindWallSystem.blocksProjectile(level, from, to);
    }
    public static void clearFor(UUID playerId) { WindWallSystem.clearFor(playerId); }
}
