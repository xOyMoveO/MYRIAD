package com.myriad.client;

import net.minecraft.client.Minecraft;

import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.network.NetworkSetup.PlayerDataPayload;

/**
 * 客户端持有的最新同步数据（由网络包更新，HUD 读取）。
 */
public final class ClientDataHolder {
    private ClientDataHolder() {}

    private static volatile PlayerDataPayload data = null;
    /** buff 快照到达时的客户端游戏刻（本地递减显示用） */
    private static int buffSnapshotTick = 0;
    /** 技能冷却快照到达时的客户端游戏刻；所有技能栏数字均逐 tick 推算。 */
    private static int skillCdSnapshotTick = 0;

    /** 普攻冷却快照到达时的客户端游戏刻；HUD 据此逐 tick 平滑刷新。 */
    private static int basicAttackSnapshotTick = 0;
    private static int magicBulletCooldown = 0;
    private static int magicBulletCooldownMax = 1;
    private static int magicBulletSnapshotTick = 0;
    /** 开局选人阶段最新状态（null = 无选人阶段） */
    private static volatile com.myriad.network.NetworkSetup.PickStatePayload pickState = null;

    public static void update(PlayerDataPayload payload) {
        if(data!=null&&(data.modEnabled()!=payload.modEnabled()||data.phaseId()!=payload.phaseId()
                ||!java.util.Objects.equals(data.characterId(),payload.characterId())))
            {com.myriad.client.particle.AbilityMeshParticle.reset();SkillChargeClient.reset();}
        data = payload;
        if (!payload.modEnabled()) {
            pickState = null;
            RespawnSpectatorView.reset();
            KillAnnouncementOverlay.clear();
            DashController.start(0, 0, 0, 0);
            com.myriad.client.FogOfWarClient.update(false, java.util.List.of());
            var mc = Minecraft.getInstance();
            if (mc.gui.screen() != null && mc.gui.screen().getClass().getPackageName().equals("com.myriad.client.gui"))
                mc.setScreenAndShow(null);
        }
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        buffSnapshotTick = p == null ? 0 : p.tickCount;
        skillCdSnapshotTick = buffSnapshotTick;
        basicAttackSnapshotTick = buffSnapshotTick;
    }

    /** Connection-local snapshots must never carry rendering rules into another world/server. */
    public static void resetConnection() {
        com.myriad.client.firstperson.NightDaggerView.reset();
        com.myriad.client.firstperson.FlameDaoView.reset();
        com.myriad.client.firstperson.ShuoView.reset();
        SkillChargeClient.reset();
        com.myriad.client.particle.AbilityMeshParticle.reset();
        data = null;
        pickState = null;
        CombatStatsPanel.reset();
        MatchResultsStore.clear();
        FogOfWarClient.update(false, java.util.List.of());
        DashController.start(0, 0, 0, 0);
        RespawnSpectatorView.reset();
        KillAnnouncementOverlay.clear();
        magicBulletCooldown = 0;
    }

    public static void updatePickState(com.myriad.network.NetworkSetup.PickStatePayload payload) {
        pickState = payload;
    }

    public static com.myriad.network.NetworkSetup.PickStatePayload pickState() {
        return pickState;
    }

    /** 是否处于开局选人阶段（选人界面需保持打开） */
    public static boolean pickActive() {
        return pickState != null && pickState.state() == 0;
    }

    public static PlayerDataPayload get() {
        return data;
    }

    public static boolean inGame() {
        return data != null && Minecraft.getInstance().player != null;
    }

    public static String factionName() {
        if (data == null) {
            return "-";
        }
        return switch (data.factionId()) {
            case 1 -> net.minecraft.network.chat.Component.translatable(
                    "faction.myriad.xuanxiao").getString();
            case 2 -> net.minecraft.network.chat.Component.translatable(
                    "faction.myriad.youming").getString();
            default -> "-";
        };
    }

    public static Faction faction() {
        if (data == null) {
            return null;
        }
        return switch (data.factionId()) {
            case 1 -> Faction.XUANXIAO;
            case 2 -> Faction.YOUMING;
            default -> null;
        };
    }

    public static GameManager.Phase phase() {
        if (data == null) {
            return GameManager.Phase.WAITING;
        }
        return switch (data.phaseId()) {
            case 1 -> GameManager.Phase.PLAYING;
            case 2 -> GameManager.Phase.ENDED;
            default -> GameManager.Phase.WAITING;
        };
    }

    /** 角色 id（未选择为空串） */
    public static String characterId() {
        return data == null ? "" : data.characterId();
    }

    /** 技能槽位剩余冷却（刻），0 表示就绪 */
    public static int cooldownTicks(int slot) {
        if (data == null) {
            return 0;
        }
        int snapshot = switch (slot) {
            case 0 -> data.cdQ();
            case 1 -> data.cdW();
            case 2 -> data.cdE();
            case 3 -> data.cdR();
            default -> data.cdF();
        };
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        int elapsed = p == null ? 0 : Math.max(0, p.tickCount - skillCdSnapshotTick);
        return Math.max(0, snapshot - elapsed);
    }

    /** 训练模式（无限法力/金币/无冷却） */
    public static boolean isTraining() {
        return data != null && data.training();
    }

    public static boolean noCooldown() {
        return data != null && data.noCooldown();
    }

    public static boolean noGoldCost() {
        return data != null && data.noGoldCost();
    }

    public static boolean noMana() {
        return data != null && data.noMana();
    }

    /** 技能附加效果剩余时长（技能 id -> 剩余刻），本地按流逝 tick 递减，显示连续变化 */
    public static java.util.Map<String, Integer> buffs() {
        if (data == null) {
            return java.util.Map.of();
        }
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        int elapsed = p == null ? 0 : p.tickCount - buffSnapshotTick;
        java.util.Map<String, Integer> result = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Integer> e : data.buffs().entrySet()) {
            // 多充能同步计数只供技能栏使用，不属于右上角的持续 Buff。
            if (e.getKey().startsWith("skill_charge_")) {
                continue;
            }

            int remain = e.getValue() - elapsed;
            if (remain > 0) {
                result.put(e.getKey(), remain);
            }
        }
        return result;
    }

    /** 单个持续状态的逐 tick 剩余时间；同步计数器请使用 {@link #syncedCounter(String)}。 */
    public static int buffTicks(String key) {
        var values=buffs();int remaining=values.getOrDefault(key,0);
        if(key.equals("g1_root"))for(var entry:values.entrySet())if(entry.getKey().startsWith("g1_root/"))remaining=Math.max(remaining,entry.getValue());
        return remaining;
    }

    /** 不随客户端时间递减的同步计数器，例如技能充能层数。 */
    public static int syncedCounter(String key) {
        return data == null ? 0 : data.buffs().getOrDefault(key, 0);
    }

    /** 各角色是否已被其他玩家选择（职业选择界面展示） */
    public static java.util.Map<String, Boolean> takenCharacters() {
        return data == null ? java.util.Map.of() : data.taken();
    }

    /** 是否正在汲取灵源 */
    public static boolean isChanneling() {
        return data != null && data.channeling();
    }

    /** 汲取进度（刻数） */
    public static int channelTicks() {
        return data == null ? 0 : data.channelTicks();
    }

    /** 模组总开关（/myriad mod off 关闭；新连接未同步时默认关闭） */
    public static boolean modEnabled() {
        return data != null && data.modEnabled();
    }

    /** 对局剩余刻数（-1 = 无对局） */
    public static int remainTicks() {
        return data == null ? -1 : data.remainTicks();
    }

    /** 装备槽（6 个装备 id，空串 = 空） */
    public static java.util.List<String> equipment() {
        return data == null ? java.util.List.of() : data.equipment();
    }

    /** 服务端属性快照值（descriptionId -> 值；未同步返回 NaN 表示用本地读） */
    public static double attrValue(String descriptionId) {
        if (data == null) {
            return Double.NaN;
        }
        Double v = data.attrs().get(descriptionId);
        return v == null ? Double.NaN : v;
    }

    /** 墨尘布阵阶段类型（0=无，1=墨灵护阵，2=传送阵；预览圆跟随准星，左键确认/右键取消） */
    public static int placementType() {
        return data == null ? 0 : data.placementType();
    }

    /** 普攻剩余冷却（刻）。服务端仅在开始冷却时同步一次，客户端每 tick 自行递减显示。 */
    public static int basicAttackCooldownNow() {
        if (data == null) {
            return 0;
        }
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        if(p!=null&&modEnabled()&&com.myriad.combat.VanillaMeleeAttack.usesVanilla(
                com.myriad.character.CharacterType.byId(data.characterId())))
            return Math.max(0,(int)Math.ceil((1-p.getAttackStrengthScale(0))*p.getCurrentItemAttackStrengthDelay()));
        int elapsed = p == null ? 0 : Math.max(0, p.tickCount - basicAttackSnapshotTick);
        return Math.max(0, data.basicAttackCooldown() - elapsed);
    }

    public static void startMagicBulletCooldown(int cooldown, int total) {
        magicBulletCooldown = Math.max(0, cooldown);
        magicBulletCooldownMax = Math.max(1, total);
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        magicBulletSnapshotTick = p == null ? 0 : p.tickCount;
    }

    public static int magicBulletCooldownNow() {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        int elapsed = p == null ? 0 : Math.max(0, p.tickCount - magicBulletSnapshotTick);
        return Math.max(0, magicBulletCooldown - elapsed);
    }

    public static int magicBulletCooldownMax() { return magicBulletCooldownMax; }
}
