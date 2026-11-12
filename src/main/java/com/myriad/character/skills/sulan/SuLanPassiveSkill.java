package com.myriad.character.skills.sulan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 风部·素岚 被动【风行者】：永久获得 15% 移速 + 50% 跳跃高度；
 * 每移动 100 格，下次普攻附带 12 + 成长攻击力倍率的风系伤害。
 */
public class SuLanPassiveSkill extends Skill {
    private static final double MS_BONUS = 0.15;
    private static final double JUMP_BONUS = 0.50;
    private static final double TRIGGER_DIST = 100.0;
    private static final Identifier MS_MOD = Identifier.parse("myriad:sulan_passive_ms");
    private static final Identifier JUMP_MOD = Identifier.parse("myriad:sulan_passive_jump");

    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Double> ACC = new HashMap<>();
    private static final Map<UUID, Boolean> READY = new HashMap<>();

    public SuLanPassiveSkill() {
        super("sulan_passive", 0, 0.0F);
    }

    @Override
    public boolean isPassive() {
        return true;
    }

    @Override
    public void cast(ServerPlayer player, PlayerData data) {
    }

    /** 每 tick：移速/跳跃常驻 + 移动距离累计（PlayerDataProvider 调用，全角色） */
    public static void tick(ServerPlayer player, PlayerData data) {
        boolean isSulan = CharacterType.byId(data.getCharacterId()) == CharacterType.SULAN;
        if (!isSulan) {
            removeModifiers(player);
            LAST_POS.remove(player.getUUID());
            ACC.remove(player.getUUID());
            READY.remove(player.getUUID());
            updateReady(player, data, false);
            return;
        }
        applyModifiers(player);
        Vec3 pos = player.position();
        Vec3 last = LAST_POS.get(player.getUUID());
        if (last != null) {
            double dx = pos.x - last.x;
            double dz = pos.z - last.z;
            double moved = Math.sqrt(dx * dx + dz * dz);
            double acc = ACC.getOrDefault(player.getUUID(), 0.0) + moved;
            if (acc >= TRIGGER_DIST) {
                acc = 0.0;
                READY.put(player.getUUID(), Boolean.TRUE);
                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        14, 0.4, 0.5, 0.4, 0.03);
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1.2, player.getZ(),
                        6, 0.2, 0.4, 0.2, 0.02);
            }
            ACC.put(player.getUUID(), acc);
        }
        LAST_POS.put(player.getUUID(), pos);
        updateReady(player, data, READY.getOrDefault(player.getUUID(), false));
    }

    private static void updateReady(ServerPlayer player, PlayerData data, boolean ready) {
        int value = ready ? 1 : 0;
        if (data.getBuff("sulan_passive_ready") != value) {
            data.setBuff("sulan_passive_ready", value);
            PlayerDataProvider.syncToClient(player);
        }
    }

    /** 消耗就绪的风刃（普攻命中时调用）：返回 true 表示触发并附带风系伤害 */
    public static boolean consumeWindStrike(ServerPlayer player) {
        if (READY.getOrDefault(player.getUUID(), Boolean.FALSE)) {
            READY.remove(player.getUUID());
            updateReady(player, PlayerDataProvider.get(player), false);
            return true;
        }
        return false;
    }

    private static void applyModifiers(ServerPlayer player) {
        AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null && !ms.hasModifier(MS_MOD)) {
            ms.addTransientModifier(new AttributeModifier(MS_MOD, MS_BONUS, Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(JUMP_MOD)) {
            jump.addTransientModifier(new AttributeModifier(JUMP_MOD, JUMP_BONUS, Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifiers(ServerPlayer player) {
        AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null) {
            ms.removeModifier(MS_MOD);
        }
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(JUMP_MOD);
        }
    }

    public static void clearFor(UUID playerId) {
        LAST_POS.remove(playerId);
        ACC.remove(playerId);
        READY.remove(playerId);
    }
}
