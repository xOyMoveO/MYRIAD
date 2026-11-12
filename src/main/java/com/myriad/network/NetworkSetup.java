package com.myriad.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import com.myriad.Myriad;
import com.myriad.character.CharacterType;
import com.myriad.character.Skill;
import com.myriad.character.SkillSystem;
import com.myriad.client.ClientDataHolder;
import com.myriad.game.Faction;
import com.myriad.game.GameManager;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 网络同步：玩家成长/灵源/对局数据 -> 客户端（HUD 展示）；
 * 技能施放请求 -> 服务端（playToServer）。
 */
public final class NetworkSetup {
    private NetworkSetup() {}
    public record RecallPayload() implements CustomPacketPayload {
        public static final Type<RecallPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID,"recall"));
        public static final StreamCodec<FriendlyByteBuf,RecallPayload> CODEC=StreamCodec.unit(new RecallPayload());
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    /** 玩家与对局状态同步包 */
    public record PlayerDataPayload(
            int level,
            int xp,
            int xpToNext,
            int gold,
            int mana,
            int carrying,
            int factionId,
            int xuanxiaoScore,
            int youmingScore,
            int phaseId,
            String characterId,
            int cdQ,
            int cdW,
            int cdE,
            int cdR,
            int cdF,
            boolean training,
            boolean noCooldown,
            boolean noGoldCost,
            boolean noMana,
            java.util.Map<String, Integer> buffs,
            java.util.Map<String, Boolean> taken,
            int skillPoints,
            int placementType,
            java.util.Map<String, Integer> skillLevels,
            boolean channeling,
            int channelTicks,
            boolean modEnabled,
            int remainTicks,
            java.util.List<String> equipment,
            java.util.Map<String, Double> attrs,
            int swordQi,
            int flyingSwords,
            int basicAttackCooldown,
            int basicAttackCooldownMax,
            float health,
            float maxHealth,
            float shield) implements CustomPacketPayload {

        public static final Type<PlayerDataPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "player_data"));

        public static final StreamCodec<FriendlyByteBuf, PlayerDataPayload> STREAM_CODEC =
                StreamCodec.ofMember(PlayerDataPayload::write, PlayerDataPayload::decode);

        private static void write(PlayerDataPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.level());
            buf.writeInt(payload.xp());
            buf.writeInt(payload.xpToNext());
            buf.writeInt(payload.gold());
            buf.writeInt(payload.mana());
            buf.writeInt(payload.carrying());
            buf.writeInt(payload.factionId());
            buf.writeInt(payload.xuanxiaoScore());
            buf.writeInt(payload.youmingScore());
            buf.writeInt(payload.phaseId());
            buf.writeUtf(payload.characterId());
            buf.writeInt(payload.cdQ());
            buf.writeInt(payload.cdW());
            buf.writeInt(payload.cdE());
            buf.writeInt(payload.cdR());
            buf.writeInt(payload.cdF());
            buf.writeBoolean(payload.training());
            buf.writeBoolean(payload.noCooldown());
            buf.writeBoolean(payload.noGoldCost());
            buf.writeBoolean(payload.noMana());
            buf.writeMap(payload.buffs(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
            buf.writeMap(payload.taken(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBoolean);
            buf.writeInt(payload.skillPoints());
            buf.writeInt(payload.placementType());
            buf.writeMap(payload.skillLevels(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
            buf.writeBoolean(payload.channeling());
            buf.writeInt(payload.channelTicks());
            buf.writeBoolean(payload.modEnabled());
            buf.writeInt(payload.remainTicks());
            buf.writeCollection(payload.equipment(), FriendlyByteBuf::writeUtf);
            buf.writeMap(payload.attrs(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeDouble);
            buf.writeInt(payload.swordQi());
            buf.writeInt(payload.flyingSwords());
            buf.writeInt(payload.basicAttackCooldown());
            buf.writeInt(payload.basicAttackCooldownMax());
            buf.writeFloat(payload.health());
            buf.writeFloat(payload.maxHealth());
            buf.writeFloat(payload.shield());
        }

        private static PlayerDataPayload decode(FriendlyByteBuf buf) {
            return new PlayerDataPayload(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt),
                    buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readBoolean),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readList(FriendlyByteBuf::readUtf),
                    buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readDouble),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端请求一次数据同步（打开个人信息界面时发送） */
    public record RequestSyncPayload() implements CustomPacketPayload {
        public static final Type<RequestSyncPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "request_sync"));

        public static final StreamCodec<FriendlyByteBuf, RequestSyncPayload> STREAM_CODEC =
                StreamCodec.ofMember(RequestSyncPayload::write, RequestSyncPayload::decode);

        private static void write(RequestSyncPayload payload, FriendlyByteBuf buf) {
        }

        private static RequestSyncPayload decode(FriendlyByteBuf buf) {
            return new RequestSyncPayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 玩家当前所在灵脉的争夺状态（服务端 -> 客户端 HUD）。 */
    public record VeinStatusPayload(boolean visible, int index, boolean large, int xuanxiaoPlayers,
            int youmingPlayers, int progress, int captureTicks) implements CustomPacketPayload {
        public static final Type<VeinStatusPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "vein_status"));
        public static final StreamCodec<FriendlyByteBuf, VeinStatusPayload> STREAM_CODEC =
                StreamCodec.ofMember(VeinStatusPayload::write, VeinStatusPayload::decode);

        private static void write(VeinStatusPayload payload, FriendlyByteBuf buf) {
            buf.writeBoolean(payload.visible());
            buf.writeInt(payload.index());
            buf.writeBoolean(payload.large());
            buf.writeInt(payload.xuanxiaoPlayers());
            buf.writeInt(payload.youmingPlayers());
            buf.writeInt(payload.progress());
            buf.writeInt(payload.captureTicks());
        }

        private static VeinStatusPayload decode(FriendlyByteBuf buf) {
            return new VeinStatusPayload(buf.readBoolean(), buf.readInt(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 当前观察者可见的玩家实体 ID（服务端 -> 客户端战争迷雾）。 */
    public record FogVisibilityPayload(boolean enabled, java.util.List<Integer> visiblePlayerIds)
            implements CustomPacketPayload {
        public static final Type<FogVisibilityPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "fog_visibility"));
        public static final StreamCodec<FriendlyByteBuf, FogVisibilityPayload> STREAM_CODEC =
                StreamCodec.ofMember(FogVisibilityPayload::write, FogVisibilityPayload::decode);

        private static void write(FogVisibilityPayload payload, FriendlyByteBuf buf) {
            buf.writeBoolean(payload.enabled());
            buf.writeCollection(payload.visiblePlayerIds(), FriendlyByteBuf::writeInt);
        }

        private static FogVisibilityPayload decode(FriendlyByteBuf buf) {
            return new FogVisibilityPayload(buf.readBoolean(), buf.readList(FriendlyByteBuf::readInt));
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 墨尘 E 墨灵护阵：左键确认布阵（客户端 -> 服务端） */
    public record ConfirmPlacementPayload() implements CustomPacketPayload {
        public static final Type<ConfirmPlacementPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "confirm_placement"));

        public static final StreamCodec<FriendlyByteBuf, ConfirmPlacementPayload> STREAM_CODEC =
                StreamCodec.ofMember(ConfirmPlacementPayload::write, ConfirmPlacementPayload::decode);

        private static void write(ConfirmPlacementPayload payload, FriendlyByteBuf buf) {
        }

        private static ConfirmPlacementPayload decode(FriendlyByteBuf buf) {
            return new ConfirmPlacementPayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 墨尘 E 墨灵护阵：右键取消布阵（客户端 -> 服务端） */
    public record CancelPlacementPayload() implements CustomPacketPayload {
        public static final Type<CancelPlacementPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "cancel_placement"));

        public static final StreamCodec<FriendlyByteBuf, CancelPlacementPayload> STREAM_CODEC =
                StreamCodec.ofMember(CancelPlacementPayload::write, CancelPlacementPayload::decode);

        private static void write(CancelPlacementPayload payload, FriendlyByteBuf buf) {
        }

        private static CancelPlacementPayload decode(FriendlyByteBuf buf) {
            return new CancelPlacementPayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 法师魔法弹：手持初始武器右键发射（客户端拦截右键 -> 服务端，避免被实体/方块交互吞掉） */
    public record MagicBulletFirePayload() implements CustomPacketPayload {
        public static final Type<MagicBulletFirePayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "magic_bullet_fire"));

        public static final StreamCodec<FriendlyByteBuf, MagicBulletFirePayload> STREAM_CODEC =
                StreamCodec.ofMember(MagicBulletFirePayload::write, MagicBulletFirePayload::decode);

        private static void write(MagicBulletFirePayload payload, FriendlyByteBuf buf) {
        }

        private static MagicBulletFirePayload decode(FriendlyByteBuf buf) {
            return new MagicBulletFirePayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 左键普攻意图：客户端仅提交实体 ID，命中、伤害和攻速间隔均由服务端验证。 */
    public record BasicAttackPayload(int targetId) implements CustomPacketPayload {
        public static final Type<BasicAttackPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "basic_attack"));
        public static final StreamCodec<FriendlyByteBuf, BasicAttackPayload> STREAM_CODEC =
                StreamCodec.ofMember(BasicAttackPayload::write, BasicAttackPayload::decode);

        private static void write(BasicAttackPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.targetId());
        }

        private static BasicAttackPayload decode(FriendlyByteBuf buf) {
            return new BasicAttackPayload(buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端技能施放请求（槽位 0=Q, 1=W, 2=E） */
    public record CastSkillPayload(int slot, float moveX, float moveZ) implements CustomPacketPayload {
        public static final Type<CastSkillPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "cast_skill"));

        public static final StreamCodec<FriendlyByteBuf, CastSkillPayload> STREAM_CODEC =
                StreamCodec.ofMember(CastSkillPayload::write, CastSkillPayload::decode);

        private static void write(CastSkillPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.slot());
            buf.writeFloat(payload.moveX());
            buf.writeFloat(payload.moveZ());
        }

        private static CastSkillPayload decode(FriendlyByteBuf buf) {
            return new CastSkillPayload(buf.readInt(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端选择职业请求 */
    public record ChooseCharacterPayload(String characterId) implements CustomPacketPayload {
        public static final Type<ChooseCharacterPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "choose_character"));

        public static final StreamCodec<FriendlyByteBuf, ChooseCharacterPayload> STREAM_CODEC =
                StreamCodec.ofMember(ChooseCharacterPayload::write, ChooseCharacterPayload::decode);

        private static void write(ChooseCharacterPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.characterId());
        }

        private static ChooseCharacterPayload decode(FriendlyByteBuf buf) {
            return new ChooseCharacterPayload(buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 开局角色选择：打开选人界面（服务端 -> 客户端，选择期间不可退出） */
    public record OpenPickScreenPayload() implements CustomPacketPayload {
        public static final Type<OpenPickScreenPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "open_pick_screen"));

        public static final StreamCodec<FriendlyByteBuf, OpenPickScreenPayload> STREAM_CODEC =
                StreamCodec.ofMember(OpenPickScreenPayload::write, OpenPickScreenPayload::decode);

        private static void write(OpenPickScreenPayload payload, FriendlyByteBuf buf) {
        }

        private static OpenPickScreenPayload decode(FriendlyByteBuf buf) {
            return new OpenPickScreenPayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 开局角色选择状态（服务端 -> 客户端）：state 0=进行中 1=结束关闭选人界面；countdownTick=全部选完后的开局倒计时截止游戏刻（-1=无）；taken=角色->选择者名；takenUuids=角色->选择者UUID（皮肤加载用） */
    public record PickStatePayload(int state, String myFaction, String currentName, String currentFaction,
            long deadlineTick, long countdownTick, java.util.Map<String, String> taken,
            java.util.Map<String, String> takenUuids) implements CustomPacketPayload {
        public static final Type<PickStatePayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "pick_state"));

        public static final StreamCodec<FriendlyByteBuf, PickStatePayload> STREAM_CODEC =
                StreamCodec.ofMember(PickStatePayload::write, PickStatePayload::decode);

        private static void write(PickStatePayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.state());
            buf.writeUtf(payload.myFaction() == null ? "" : payload.myFaction());
            buf.writeUtf(payload.currentName() == null ? "" : payload.currentName());
            buf.writeUtf(payload.currentFaction() == null ? "" : payload.currentFaction());
            buf.writeLong(payload.deadlineTick());
            buf.writeLong(payload.countdownTick());
            buf.writeVarInt(payload.taken().size());
            for (java.util.Map.Entry<String, String> e : payload.taken().entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeUtf(e.getValue());
            }
            buf.writeVarInt(payload.takenUuids().size());
            for (java.util.Map.Entry<String, String> e : payload.takenUuids().entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeUtf(e.getValue());
            }
        }

        private static PickStatePayload decode(FriendlyByteBuf buf) {
            int state = buf.readInt();
            String myF = buf.readUtf();
            String cur = buf.readUtf();
            String curF = buf.readUtf();
            long dl = buf.readLong();
            long countdown = buf.readLong();
            int n = buf.readVarInt();
            java.util.Map<String, String> taken = new java.util.HashMap<>();
            for (int i = 0; i < n; i++) {
                taken.put(buf.readUtf(), buf.readUtf());
            }
            int m = buf.readVarInt();
            java.util.Map<String, String> takenUuids = new java.util.HashMap<>();
            for (int i = 0; i < m; i++) {
                takenUuids.put(buf.readUtf(), buf.readUtf());
            }
            return new PickStatePayload(state, myF.isEmpty() ? null : myF,
                    cur.isEmpty() ? null : cur, curF.isEmpty() ? null : curF, dl, countdown, taken, takenUuids);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 伤害飘字（服务端 -> 伤害来源玩家）：type 0=物理 1=法术 2=真实；crit 暴击（粗体放大显示） */
    public record DamageFloatPayload(int dmgType, int amount, boolean crit, double x, double y, double z)
            implements CustomPacketPayload {
        public static final Type<DamageFloatPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "damage_float"));

        public static final StreamCodec<FriendlyByteBuf, DamageFloatPayload> STREAM_CODEC =
                StreamCodec.ofMember(DamageFloatPayload::write, DamageFloatPayload::decode);

        private static void write(DamageFloatPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.dmgType());
            buf.writeInt(payload.amount());
            buf.writeBoolean(payload.crit());
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
        }

        private static DamageFloatPayload decode(FriendlyByteBuf buf) {
            return new DamageFloatPayload(buf.readInt(), buf.readInt(), buf.readBoolean(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 玩家实际掉血反馈（服务端 -> 受伤玩家）：用于红屏强度和本地受击声。 */
    public record HurtFeedbackPayload(float healthDamage, float maxHealth) implements CustomPacketPayload {
        public static final Type<HurtFeedbackPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "hurt_feedback"));
        public static final StreamCodec<FriendlyByteBuf, HurtFeedbackPayload> STREAM_CODEC =
                StreamCodec.ofMember(HurtFeedbackPayload::write, HurtFeedbackPayload::decode);

        private static void write(HurtFeedbackPayload payload, FriendlyByteBuf buf) {
            buf.writeFloat(payload.healthDamage());
            buf.writeFloat(payload.maxHealth());
        }

        private static HurtFeedbackPayload decode(FriendlyByteBuf buf) {
            return new HurtFeedbackPayload(buf.readFloat(), buf.readFloat());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 攻击目标生命显示（服务端 -> 攻击者）：entityId 为被攻击实体 */
    public record TargetHealthPayload(int entityId, float physicalDefense, float magicDefense) implements CustomPacketPayload {
        public static final Type<TargetHealthPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "target_health"));

        public static final StreamCodec<FriendlyByteBuf, TargetHealthPayload> STREAM_CODEC =
                StreamCodec.ofMember(TargetHealthPayload::write, TargetHealthPayload::decode);

        private static void write(TargetHealthPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.entityId());
            buf.writeFloat(payload.physicalDefense());
            buf.writeFloat(payload.magicDefense());
        }

        private static TargetHealthPayload decode(FriendlyByteBuf buf) {
            return new TargetHealthPayload(buf.readInt(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 右键训练假人后打开双防设置界面。 */
    public record OpenJiarenSettingsPayload(int entityId, float physicalDefense, float magicDefense)
            implements CustomPacketPayload {
        public static final Type<OpenJiarenSettingsPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "open_jiaren_settings"));
        public static final StreamCodec<FriendlyByteBuf, OpenJiarenSettingsPayload> STREAM_CODEC =
                StreamCodec.ofMember(OpenJiarenSettingsPayload::write, OpenJiarenSettingsPayload::decode);
        private static void write(OpenJiarenSettingsPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.entityId()); buf.writeFloat(payload.physicalDefense()); buf.writeFloat(payload.magicDefense());
        }
        private static OpenJiarenSettingsPayload decode(FriendlyByteBuf buf) {
            return new OpenJiarenSettingsPayload(buf.readInt(), buf.readFloat(), buf.readFloat());
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 客户端提交训练假人双防；服务端重新验证实体、距离与数值范围。 */
    public record SetJiarenDefensesPayload(int entityId, float physicalDefense, float magicDefense)
            implements CustomPacketPayload {
        public static final Type<SetJiarenDefensesPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "set_jiaren_defenses"));
        public static final StreamCodec<FriendlyByteBuf, SetJiarenDefensesPayload> STREAM_CODEC =
                StreamCodec.ofMember(SetJiarenDefensesPayload::write, SetJiarenDefensesPayload::decode);
        private static void write(SetJiarenDefensesPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.entityId()); buf.writeFloat(payload.physicalDefense()); buf.writeFloat(payload.magicDefense());
        }
        private static SetJiarenDefensesPayload decode(FriendlyByteBuf buf) {
            return new SetJiarenDefensesPayload(buf.readInt(), buf.readFloat(), buf.readFloat());
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 假人（训练目标）mod 血量显示（服务端 -> 攻击者）：entityId + 当前/最大 mod 血量 */
    public record JiarenHpPayload(int entityId, float hp, float maxHp) implements CustomPacketPayload {
        public static final Type<JiarenHpPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "jiaren_hp"));

        public static final StreamCodec<FriendlyByteBuf, JiarenHpPayload> STREAM_CODEC =
                StreamCodec.ofMember(JiarenHpPayload::write, JiarenHpPayload::decode);

        private static void write(JiarenHpPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.entityId());
            buf.writeFloat(payload.hp());
            buf.writeFloat(payload.maxHp());
        }

        private static JiarenHpPayload decode(FriendlyByteBuf buf) {
            return new JiarenHpPayload(buf.readInt(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Authoritative teams and world-local arena selection. */
    public record TeamListPayload(java.util.Map<String, Integer> teams, String arenaId, boolean duelAvailable) implements CustomPacketPayload {
        public static final Type<TeamListPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "team_list"));

        public static final StreamCodec<FriendlyByteBuf, TeamListPayload> STREAM_CODEC =
                StreamCodec.ofMember(TeamListPayload::write, TeamListPayload::decode);

        private static void write(TeamListPayload payload, FriendlyByteBuf buf) {
            buf.writeMap(payload.teams(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
            buf.writeUtf(payload.arenaId(), 8);
            buf.writeBoolean(payload.duelAvailable());
        }

        private static TeamListPayload decode(FriendlyByteBuf buf) {
            return new TeamListPayload(buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt), buf.readUtf(8), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 开局管理：请求玩家列表（客户端 -> 服务端） */
    public record RequestTeamListPayload() implements CustomPacketPayload {
        public static final Type<RequestTeamListPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "request_team_list"));

        public static final StreamCodec<FriendlyByteBuf, RequestTeamListPayload> STREAM_CODEC =
                StreamCodec.ofMember(RequestTeamListPayload::write, RequestTeamListPayload::decode);

        private static void write(RequestTeamListPayload payload, FriendlyByteBuf buf) {
        }

        private static RequestTeamListPayload decode(FriendlyByteBuf buf) {
            return new RequestTeamListPayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 开局管理：分配玩家宗门（客户端 -> 服务端）；factionId 0=未分配 1=玄霄宗 2=幽冥宗 */
    public record SetTeamPayload(String playerName, int factionId) implements CustomPacketPayload {
        public static final Type<SetTeamPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "set_team"));

        public static final StreamCodec<FriendlyByteBuf, SetTeamPayload> STREAM_CODEC =
                StreamCodec.ofMember(SetTeamPayload::write, SetTeamPayload::decode);

        private static void write(SetTeamPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.playerName());
            buf.writeInt(payload.factionId());
        }

        private static SetTeamPayload decode(FriendlyByteBuf buf) {
            return new SetTeamPayload(buf.readUtf(), buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 开局管理：开始游戏（客户端 -> 服务端）；mode 0=均衡随机分配后开始，1=按当前手动分配开始 */
    public record StartMatchPayload(int mode, String arenaId) implements CustomPacketPayload {
        public static final Type<StartMatchPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "start_match"));

        public static final StreamCodec<FriendlyByteBuf, StartMatchPayload> STREAM_CODEC =
                StreamCodec.ofMember(StartMatchPayload::write, StartMatchPayload::decode);

        private static void write(StartMatchPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.mode());
            buf.writeUtf(payload.arenaId(), 8);
        }

        private static StartMatchPayload decode(FriendlyByteBuf buf) {
            return new StartMatchPayload(buf.readInt(), buf.readUtf(8));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SelectArenaPayload(String arenaId) implements CustomPacketPayload {
        public static final Type<SelectArenaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "select_arena"));
        public static final StreamCodec<FriendlyByteBuf, SelectArenaPayload> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUtf(value.arenaId(), 8), buf -> new SelectArenaPayload(buf.readUtf(8)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 开局提示（服务端 -> 客户端）：屏幕中央显示「请选择角色」3 秒 */
    public record MatchStartedPayload() implements CustomPacketPayload {
        public static final Type<MatchStartedPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "match_started"));

        public static final StreamCodec<FriendlyByteBuf, MatchStartedPayload> STREAM_CODEC =
                StreamCodec.ofMember(MatchStartedPayload::write, MatchStartedPayload::decode);

        private static void write(MatchStartedPayload payload, FriendlyByteBuf buf) {
        }

        private static MatchStartedPayload decode(FriendlyByteBuf buf) {
            return new MatchStartedPayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 游戏设置同步（服务端 -> 客户端，打开设置 UI 时请求/推送） */
    public record GameSettingsPayload(java.util.Map<String, Integer> settings) implements CustomPacketPayload {
        public static final Type<GameSettingsPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "game_settings"));

        public static final StreamCodec<FriendlyByteBuf, GameSettingsPayload> STREAM_CODEC =
                StreamCodec.ofMember(GameSettingsPayload::write, GameSettingsPayload::decode);

        private static void write(GameSettingsPayload payload, FriendlyByteBuf buf) {
            buf.writeMap(payload.settings(), FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeInt);
        }

        private static GameSettingsPayload decode(FriendlyByteBuf buf) {
            return new GameSettingsPayload(buf.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readInt));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 请求游戏设置（客户端 -> 服务端） */
    public record RequestGameSettingsPayload() implements CustomPacketPayload {
        public static final Type<RequestGameSettingsPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "request_game_settings"));

        public static final StreamCodec<FriendlyByteBuf, RequestGameSettingsPayload> STREAM_CODEC =
                StreamCodec.ofMember(RequestGameSettingsPayload::write, RequestGameSettingsPayload::decode);

        private static void write(RequestGameSettingsPayload payload, FriendlyByteBuf buf) {
        }

        private static RequestGameSettingsPayload decode(FriendlyByteBuf buf) {
            return new RequestGameSettingsPayload();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 修改游戏设置（客户端 -> 服务端）：key=设置项名，value=新值 */
    public record UpdateSettingPayload(String key, int value) implements CustomPacketPayload {
        public static final Type<UpdateSettingPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "update_setting"));

        public static final StreamCodec<FriendlyByteBuf, UpdateSettingPayload> STREAM_CODEC =
                StreamCodec.ofMember(UpdateSettingPayload::write, UpdateSettingPayload::decode);

        private static void write(UpdateSettingPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.key());
            buf.writeInt(payload.value());
        }

        private static UpdateSettingPayload decode(FriendlyByteBuf buf) {
            return new UpdateSettingPayload(buf.readUtf(), buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 地图设施编辑器的一行结构化数据。 */
    public record MapFacilityEntry(String category, String id, String meta,
            boolean firstSet, int x, int y, int z,
            boolean secondSet, int x2, int y2, int z2) {
        private static void write(FriendlyByteBuf buf, MapFacilityEntry entry) {
            buf.writeUtf(entry.category());
            buf.writeUtf(entry.id());
            buf.writeUtf(entry.meta());
            buf.writeBoolean(entry.firstSet());
            buf.writeInt(entry.x());
            buf.writeInt(entry.y());
            buf.writeInt(entry.z());
            buf.writeBoolean(entry.secondSet());
            buf.writeInt(entry.x2());
            buf.writeInt(entry.y2());
            buf.writeInt(entry.z2());
        }

        private static MapFacilityEntry read(FriendlyByteBuf buf) {
            return new MapFacilityEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    /** 地图设施编辑器完整快照（服务端 -> 客户端）。 */
    public record MapFacilitySnapshotPayload(java.util.List<MapFacilityEntry> entries)
            implements CustomPacketPayload {
        public static final Type<MapFacilitySnapshotPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "map_facility_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, MapFacilitySnapshotPayload> STREAM_CODEC =
                StreamCodec.ofMember(MapFacilitySnapshotPayload::write, MapFacilitySnapshotPayload::decode);

        private static void write(MapFacilitySnapshotPayload payload, FriendlyByteBuf buf) {
            buf.writeCollection(payload.entries(), MapFacilityEntry::write);
        }

        private static MapFacilitySnapshotPayload decode(FriendlyByteBuf buf) {
            return new MapFacilitySnapshotPayload(buf.readList(MapFacilityEntry::read));
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 请求打开或刷新地图设施编辑器（客户端 -> 服务端）。 */
    public record RequestMapFacilityEditorPayload() implements CustomPacketPayload {
        public static final Type<RequestMapFacilityEditorPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "request_map_facility_editor"));
        public static final StreamCodec<FriendlyByteBuf, RequestMapFacilityEditorPayload> STREAM_CODEC =
                StreamCodec.ofMember(RequestMapFacilityEditorPayload::write, RequestMapFacilityEditorPayload::decode);
        private static void write(RequestMapFacilityEditorPayload payload, FriendlyByteBuf buf) {}
        private static RequestMapFacilityEditorPayload decode(FriendlyByteBuf buf) {
            return new RequestMapFacilityEditorPayload();
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 地图设施编辑动作（客户端 -> 服务端）；坐标始终由服务端读取玩家当前脚下位置。 */
    public record MapFacilityActionPayload(String category, String id, String meta, String action)
            implements CustomPacketPayload {
        public static final Type<MapFacilityActionPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "map_facility_action"));
        public static final StreamCodec<FriendlyByteBuf, MapFacilityActionPayload> STREAM_CODEC =
                StreamCodec.ofMember(MapFacilityActionPayload::write, MapFacilityActionPayload::decode);

        private static void write(MapFacilityActionPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.category());
            buf.writeUtf(payload.id());
            buf.writeUtf(payload.meta());
            buf.writeUtf(payload.action());
        }

        private static MapFacilityActionPayload decode(FriendlyByteBuf buf) {
            return new MapFacilityActionPayload(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 小地图标记：0=队友，1=自己，2/3/6/7=各类小兵，4=塔，5=水晶，8=营地，9=首领坑，10=泉水。 */
    public record MinimapMarker(int kind, int factionId, float x, float z, java.util.UUID playerId) {
        public static final int CAMP = 8;
        public static final int BOSS = 9;
        public static final int FOUNTAIN = 10;
        private static void write(FriendlyByteBuf buf, MinimapMarker marker) {
            buf.writeByte(marker.kind());
            buf.writeByte(marker.factionId());
            buf.writeFloat(marker.x());
            buf.writeFloat(marker.z());
            buf.writeUUID(marker.playerId());
        }

        private static MinimapMarker read(FriendlyByteBuf buf) {
            return new MinimapMarker(buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readUUID());
        }
    }

    /** 一条兵线上的有序路径点。 */
    public record MinimapPathPoint(int lane, int order, float x, float z) {
        private static void write(FriendlyByteBuf buf, MinimapPathPoint point) {
            buf.writeByte(point.lane());
            buf.writeByte(point.order());
            buf.writeFloat(point.x());
            buf.writeFloat(point.z());
        }

        private static MinimapPathPoint read(FriendlyByteBuf buf) {
            return new MinimapPathPoint(buf.readUnsignedByte(), buf.readUnsignedByte(),
                    buf.readFloat(), buf.readFloat());
        }
    }

    /** 服务端权威小地图快照（服务端 -> 客户端）。 */
    public record MinimapSnapshotPayload(boolean configured,
            float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4,
            java.util.List<MinimapPathPoint> paths,
            java.util.List<MinimapMarker> markers) implements CustomPacketPayload {
        public static final Type<MinimapSnapshotPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "minimap_snapshot"));
        public static final StreamCodec<FriendlyByteBuf, MinimapSnapshotPayload> STREAM_CODEC =
                StreamCodec.ofMember(MinimapSnapshotPayload::write, MinimapSnapshotPayload::decode);

        private static void write(MinimapSnapshotPayload payload, FriendlyByteBuf buf) {
            buf.writeBoolean(payload.configured());
            buf.writeFloat(payload.x1());
            buf.writeFloat(payload.z1());
            buf.writeFloat(payload.x2());
            buf.writeFloat(payload.z2());
            buf.writeFloat(payload.x3());
            buf.writeFloat(payload.z3());
            buf.writeFloat(payload.x4());
            buf.writeFloat(payload.z4());
            buf.writeCollection(payload.paths(), MinimapPathPoint::write);
            buf.writeCollection(payload.markers(), MinimapMarker::write);
        }

        private static MinimapSnapshotPayload decode(FriendlyByteBuf buf) {
            return new MinimapSnapshotPayload(buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readList(MinimapPathPoint::read),
                    buf.readList(MinimapMarker::read));
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 商店购买装备或战术补给（客户端 -> 服务端） */
    public record ShopBuyPayload(String equipId) implements CustomPacketPayload {
        public static final Type<ShopBuyPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "shop_buy"));

        public static final StreamCodec<FriendlyByteBuf, ShopBuyPayload> STREAM_CODEC =
                StreamCodec.ofMember(ShopBuyPayload::write, ShopBuyPayload::decode);

        private static void write(ShopBuyPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.equipId());
        }

        private static ShopBuyPayload decode(FriendlyByteBuf buf) {
            return new ShopBuyPayload(buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 出售装备（客户端 -> 服务端）：slot 为装备槽位 */
    public record SellEquipPayload(int slot) implements CustomPacketPayload {
        public static final Type<SellEquipPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "sell_equip"));

        public static final StreamCodec<FriendlyByteBuf, SellEquipPayload> STREAM_CODEC =
                StreamCodec.ofMember(SellEquipPayload::write, SellEquipPayload::decode);

        private static void write(SellEquipPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.slot());
        }

        private static SellEquipPayload decode(FriendlyByteBuf buf) {
            return new SellEquipPayload(buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 回复飘字（服务端 -> 吸血来源玩家）：生命值上方显示绿色 +N */
    public record HealFloatPayload(int amount) implements CustomPacketPayload {
        public static final Type<HealFloatPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "heal_float"));

        public static final StreamCodec<FriendlyByteBuf, HealFloatPayload> STREAM_CODEC =
                StreamCodec.ofMember(HealFloatPayload::write, HealFloatPayload::decode);

        private static void write(HealFloatPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.amount());
        }

        private static HealFloatPayload decode(FriendlyByteBuf buf) {
            return new HealFloatPayload(buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 本局个人战斗统计增量（服务端 -> 受影响玩家）。 */
    public record CombatTelemetryPayload(int kind, int amount, String source) implements CustomPacketPayload {
        public static final Type<CombatTelemetryPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "combat_telemetry"));
        public static final StreamCodec<FriendlyByteBuf, CombatTelemetryPayload> STREAM_CODEC =
                StreamCodec.ofMember(CombatTelemetryPayload::write, CombatTelemetryPayload::decode);

        private static void write(CombatTelemetryPayload payload, FriendlyByteBuf buf) {
            buf.writeVarInt(payload.kind());
            buf.writeVarInt(payload.amount());
            buf.writeUtf(payload.source(), 32);
        }

        private static CombatTelemetryPayload decode(FriendlyByteBuf buf) {
            return new CombatTelemetryPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(32));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 单名玩家的整局结算数据。 */
    public record MatchResultEntry(String name, String faction, String character, int level,
            int kills, int deaths, int gold, int heroDamage, int damageTaken, int healingReceived) {
        private static void write(FriendlyByteBuf buf, MatchResultEntry entry) {
            buf.writeUtf(entry.name(), 64);
            buf.writeUtf(entry.faction(), 16);
            buf.writeUtf(entry.character(), 32);
            buf.writeVarInt(entry.level());
            buf.writeVarInt(entry.kills());
            buf.writeVarInt(entry.deaths());
            buf.writeVarInt(entry.gold());
            buf.writeVarInt(entry.heroDamage());
            buf.writeVarInt(entry.damageTaken());
            buf.writeVarInt(entry.healingReceived());
        }

        private static MatchResultEntry read(FriendlyByteBuf buf) {
            return new MatchResultEntry(buf.readUtf(64), buf.readUtf(16), buf.readUtf(32),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }
    }

    /** 整局结算快照；客户端保留到下一局开始或断开连接。 */
    public record MatchResultsPayload(String winner, int durationSeconds, java.util.List<MatchResultEntry> entries)
            implements CustomPacketPayload {
        public static final Type<MatchResultsPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "match_results"));
        public static final StreamCodec<FriendlyByteBuf, MatchResultsPayload> STREAM_CODEC =
                StreamCodec.ofMember(MatchResultsPayload::write, MatchResultsPayload::decode);

        private static void write(MatchResultsPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.winner(), 16);
            buf.writeVarInt(payload.durationSeconds());
            buf.writeCollection(payload.entries(), MatchResultEntry::write);
        }

        private static MatchResultsPayload decode(FriendlyByteBuf buf) {
            return new MatchResultsPayload(buf.readUtf(16), buf.readVarInt(),
                    buf.readList(MatchResultEntry::read));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 合成装备（客户端 -> 服务端）：拥有全部组件时组件原价抵消 */
    public record CraftEquipPayload(String equipId) implements CustomPacketPayload {
        public static final Type<CraftEquipPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "craft_equip"));

        public static final StreamCodec<FriendlyByteBuf, CraftEquipPayload> STREAM_CODEC =
                StreamCodec.ofMember(CraftEquipPayload::write, CraftEquipPayload::decode);

        private static void write(CraftEquipPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.equipId());
        }

        private static CraftEquipPayload decode(FriendlyByteBuf buf) {
            return new CraftEquipPayload(buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 冲刺同步（服务端 -> 客户端）：影袭方向（含垂直）与时长，客户端本地推进（平滑镜头） */
    public record DashPayload(float dirX, float dirY, float dirZ, int ticks) implements CustomPacketPayload {
        public static final Type<DashPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "dash"));

        public static final StreamCodec<FriendlyByteBuf, DashPayload> STREAM_CODEC =
                StreamCodec.ofMember(DashPayload::write, DashPayload::decode);

        private static void write(DashPayload payload, FriendlyByteBuf buf) {
            buf.writeFloat(payload.dirX());
            buf.writeFloat(payload.dirY());
            buf.writeFloat(payload.dirZ());
            buf.writeInt(payload.ticks());
        }

        private static DashPayload decode(FriendlyByteBuf buf) {
            return new DashPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 抛物线 motion 同步（服务端 -> 客户端）：每 tick 写入同一段曲线的瞬时速度。 */
    public record ParabolicMotionPayload(float deltaX, float deltaY, float deltaZ,
            float arcHeight, int ticks) implements CustomPacketPayload {
        public static final Type<ParabolicMotionPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "parabolic_motion"));
        public static final StreamCodec<FriendlyByteBuf, ParabolicMotionPayload> STREAM_CODEC =
                StreamCodec.ofMember(ParabolicMotionPayload::write, ParabolicMotionPayload::decode);

        private static void write(ParabolicMotionPayload payload, FriendlyByteBuf buf) {
            buf.writeFloat(payload.deltaX()); buf.writeFloat(payload.deltaY()); buf.writeFloat(payload.deltaZ());
            buf.writeFloat(payload.arcHeight()); buf.writeInt(payload.ticks());
        }

        private static ParabolicMotionPayload decode(FriendlyByteBuf buf) {
            return new ParabolicMotionPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 逆转星盘持续牵引（服务端 -> 受影响客户端）：同步中心和逐 tick motion 参数。 */
    public record StarPullMotionPayload(float centerX, float centerZ, float strength,
            float velocityBlend, int ticks) implements CustomPacketPayload {
        public static final Type<StarPullMotionPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "star_pull_motion"));
        public static final StreamCodec<FriendlyByteBuf, StarPullMotionPayload> STREAM_CODEC =
                StreamCodec.ofMember(StarPullMotionPayload::write, StarPullMotionPayload::decode);

        private static void write(StarPullMotionPayload payload, FriendlyByteBuf buf) {
            buf.writeFloat(payload.centerX());
            buf.writeFloat(payload.centerZ());
            buf.writeFloat(payload.strength());
            buf.writeFloat(payload.velocityBlend());
            buf.writeInt(payload.ticks());
        }

        private static StarPullMotionPayload decode(FriendlyByteBuf buf) {
            return new StarPullMotionPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 法术弹发射冷却（服务端 -> 客户端），HUD 收包后逐 tick 本地递减。 */
    public record MagicBulletCooldownPayload(int cooldownTicks, int totalTicks) implements CustomPacketPayload {
        public static final Type<MagicBulletCooldownPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "magic_bullet_cooldown"));
        public static final StreamCodec<FriendlyByteBuf, MagicBulletCooldownPayload> STREAM_CODEC =
                StreamCodec.ofMember(MagicBulletCooldownPayload::write, MagicBulletCooldownPayload::decode);

        private static void write(MagicBulletCooldownPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.cooldownTicks()); buf.writeInt(payload.totalTicks());
        }

        private static MagicBulletCooldownPayload decode(FriendlyByteBuf buf) {
            return new MagicBulletCooldownPayload(buf.readInt(), buf.readInt());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 周天覆阵引导视角（服务端 -> 客户端）：灵魂出窍式侧视自己，durationTicks 为引导时长（0 = 立即结束） */
    public record ZhouTianChannelPayload(int durationTicks) implements CustomPacketPayload {
        public static final Type<ZhouTianChannelPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "zhoutian_channel"));

        public static final StreamCodec<FriendlyByteBuf, ZhouTianChannelPayload> STREAM_CODEC =
                StreamCodec.ofMember(ZhouTianChannelPayload::write, ZhouTianChannelPayload::decode);

        private static void write(ZhouTianChannelPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.durationTicks());
        }

        private static ZhouTianChannelPayload decode(FriendlyByteBuf buf) {
            return new ZhouTianChannelPayload(buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 寒泽 R 千里冰封俯视镜头（服务端 -> 客户端；0 = 立即还原）。 */
    public record FrostViewPayload(int durationTicks) implements CustomPacketPayload {
        public static final Type<FrostViewPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "frost_view"));

        public static final StreamCodec<FriendlyByteBuf, FrostViewPayload> STREAM_CODEC =
                StreamCodec.ofMember(FrostViewPayload::write, FrostViewPayload::decode);

        private static void write(FrostViewPayload payload, FriendlyByteBuf buf) {
            buf.writeInt(payload.durationTicks());
        }

        private static FrostViewPayload decode(FriendlyByteBuf buf) {
            return new FrostViewPayload(buf.readInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 训练模式操作（客户端 -> 服务端）：升级、金币、宗门选择及训练开关。 */
    public record TrainActionPayload(String action, boolean value) implements CustomPacketPayload {
        public static final Type<TrainActionPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "train_action"));

        public static final StreamCodec<FriendlyByteBuf, TrainActionPayload> STREAM_CODEC =
                StreamCodec.ofMember(TrainActionPayload::write, TrainActionPayload::decode);

        private static void write(TrainActionPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.action());
            buf.writeBoolean(payload.value());
        }

        private static TrainActionPayload decode(FriendlyByteBuf buf) {
            return new TrainActionPayload(buf.readUtf(), buf.readBoolean());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端技能升级请求 */
    public record UpgradeSkillPayload(String skillId) implements CustomPacketPayload {
        public static final Type<UpgradeSkillPayload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Myriad.MODID, "upgrade_skill"));

        public static final StreamCodec<FriendlyByteBuf, UpgradeSkillPayload> STREAM_CODEC =
                StreamCodec.ofMember(UpgradeSkillPayload::write, UpgradeSkillPayload::decode);

        private static void write(UpgradeSkillPayload payload, FriendlyByteBuf buf) {
            buf.writeUtf(payload.skillId());
        }

        private static UpgradeSkillPayload decode(FriendlyByteBuf buf) {
            return new UpgradeSkillPayload(buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("16")
                .playToClient(ShuoViewPayload.TYPE,ShuoViewPayload.CODEC,
                        (payload,context)->com.myriad.client.firstperson.ShuoView.receive(payload))
                .playToClient(FlameSkillViewPayload.TYPE,FlameSkillViewPayload.CODEC,
                        (payload,context)->com.myriad.client.firstperson.FlameDaoView.receive(payload))
                .playToClient(ThunderSkillViewPayload.TYPE,ThunderSkillViewPayload.CODEC,
                        (payload,context)->com.myriad.client.firstperson.ThunderSwordView.receive(payload))
                .playToClient(NightSkillViewPayload.TYPE,NightSkillViewPayload.CODEC,
                        (payload,context)->com.myriad.client.firstperson.NightDaggerView.receive(payload))
                .playToClient(SkillChargePayload.TYPE, SkillChargePayload.CODEC,
                        (payload, context) -> com.myriad.client.SkillChargeClient.receive(payload))
                .playToServer(RecallPayload.TYPE, RecallPayload.CODEC,
                        (payload, context) -> { if(context.player() instanceof ServerPlayer p) com.myriad.game.RecallSystem.request(p); })
                .playToServer(CycleSpectatorPayload.TYPE, CycleSpectatorPayload.CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.myriad.game.HeroRespawnSystem.cycleTarget(player);
                            }
                        })
                .playToClient(SpectatorTargetPayload.TYPE, SpectatorTargetPayload.CODEC,
                        (payload, context) -> com.myriad.client.RespawnSpectatorView.update(
                                payload.active(), payload.targetName()))
                .playToClient(KillAnnouncementPayload.TYPE, KillAnnouncementPayload.CODEC,
                        (payload, context) -> com.myriad.client.KillAnnouncementOverlay.show(
                                payload.killerId(), payload.killerName(), payload.victimId(),
                                payload.victimName(), payload.multiKill()))
                .playToClient(MatchTabPayload.TYPE, MatchTabPayload.CODEC,
                        (payload, context) -> com.myriad.client.MatchTabOverlay.update(payload))
                .playToClient(EnemyHealthPayload.TYPE, EnemyHealthPayload.CODEC,
                        (payload, context) -> com.myriad.client.EnemyHealthBars.update(payload))
                .playToClient(RewardFloatPayload.TYPE, RewardFloatPayload.CODEC,
                        (payload, context) -> com.myriad.client.RewardFloater.add(payload))
                .playToClient(TowerAlertPayload.TYPE, TowerAlertPayload.CODEC,
                        (payload, context) -> com.myriad.client.TowerAlertOverlay.show(
                                payload.laneId(), payload.tier()))
                .playToClient(PlayerDataPayload.TYPE, PlayerDataPayload.STREAM_CODEC,
                        (payload, context) -> {
                            ClientDataHolder.update(payload);
                            Myriad.LOGGER.debug("[sync] client received: lv={} char={} faction={} phase={}",
                                    payload.level(), payload.characterId(), payload.factionId(), payload.phaseId());
                        })
                .playToClient(VeinStatusPayload.TYPE, VeinStatusPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.HudOverlay.setVeinStatus(payload))
                .playToClient(FogVisibilityPayload.TYPE, FogVisibilityPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.FogOfWarClient.update(
                                payload.enabled(), payload.visiblePlayerIds()))
                .playToServer(RequestSyncPayload.TYPE, RequestSyncPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.syncToClient(player);
                            }
                        })
                .playToServer(CastSkillPayload.TYPE, CastSkillPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.myriad.character.skills.lige.LiGeSkills.setRequestedDashDirection(
                                        player, payload.moveX(), payload.moveZ());
                                com.myriad.character.skills.mingzhu.MingZhuSkills.setRequestedDirection(
                                        player, payload.moveX(), payload.moveZ());
                                com.myriad.character.skills.wuxiang.WuxiangSkills.setRequestedDirection(
                                        player, payload.moveX(), payload.moveZ());
                                com.myriad.character.skills.feiyu.FeiYuSkills.setRequestedDirection(
                                        player, payload.moveX(), payload.moveZ());
                                com.myriad.character.skills.wanyue.WanyueSkills.setRequestedDirection(
                                        player, payload.moveX(), payload.moveZ());
                                try {
                                    SkillSystem.cast(player, payload.slot());
                                } finally {
                                    com.myriad.character.skills.mingzhu.MingZhuSkills.clearRequestedDirection(player);
                                    com.myriad.character.skills.lige.LiGeSkills.clearRequestedDashDirection(player);
                                    com.myriad.character.skills.wuxiang.WuxiangSkills.clearRequestedDirection(player);
                                    com.myriad.character.skills.feiyu.FeiYuSkills.clearRequestedDirection(player);
                                    com.myriad.character.skills.wanyue.WanyueSkills.clearRequestedDirection(player);
                                }
                            }
                        })
                .playToServer(ChooseCharacterPayload.TYPE, ChooseCharacterPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.chooseCharacter(player, CharacterType.byId(payload.characterId()));
                            }
                        })
                .playToClient(DamageFloatPayload.TYPE, DamageFloatPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.DamageFloater.add(
                                payload.dmgType(), payload.amount(), payload.crit(),
                                payload.x(), payload.y(), payload.z()))
                .playToClient(HurtFeedbackPayload.TYPE, HurtFeedbackPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.HudOverlay.showHurtFeedback(
                                payload.healthDamage(), payload.maxHealth()))
                .playToClient(TargetHealthPayload.TYPE, TargetHealthPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.HudOverlay.setTarget(
                                payload.entityId(), payload.physicalDefense(), payload.magicDefense()))
                .playToClient(JiarenHpPayload.TYPE, JiarenHpPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.HudOverlay.setJiarenHp(
                                payload.entityId(), payload.hp(), payload.maxHp()))
                .playToClient(MatchStartedPayload.TYPE, MatchStartedPayload.STREAM_CODEC,
                        (payload, context) -> {
                            com.myriad.client.CombatStatsPanel.reset();
                            com.myriad.client.MatchResultsStore.clear();
                            com.myriad.client.TowerAlertOverlay.clear();
                            com.myriad.client.KillAnnouncementOverlay.clear();
                            com.myriad.client.RespawnSpectatorView.reset();
                        })
                .playToClient(CombatTelemetryPayload.TYPE, CombatTelemetryPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.CombatStatsPanel.record(
                                payload.kind(), payload.amount(), payload.source()))
                .playToClient(MatchResultsPayload.TYPE, MatchResultsPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.MatchResultsStore.receive(payload))
                .playToClient(OpenPickScreenPayload.TYPE, OpenPickScreenPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                                net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                                        new com.myriad.client.gui.PickScreen());
                            }
                        })
                .playToClient(OpenJiarenSettingsPayload.TYPE, OpenJiarenSettingsPayload.STREAM_CODEC,
                        (payload, context) -> net.minecraft.client.Minecraft.getInstance().setScreenAndShow(
                                new com.myriad.client.gui.JiarenSettingsScreen(payload.entityId(),
                                        payload.physicalDefense(), payload.magicDefense())))
                .playToServer(SetJiarenDefensesPayload.TYPE, SetJiarenDefensesPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (!(context.player() instanceof ServerPlayer player)
                                    || !Float.isFinite(payload.physicalDefense())
                                    || !Float.isFinite(payload.magicDefense())) return;
                            net.minecraft.world.entity.Entity entity = player.level().getEntity(payload.entityId());
                            if (entity instanceof net.minecraft.world.entity.LivingEntity living
                                    && com.myriad.game.JiarenManager.isJiaren(living.getUUID())
                                    && player.distanceToSqr(living) <= 36.0) {
                                com.myriad.game.JiarenManager.setDefenses(living,
                                        payload.physicalDefense(), payload.magicDefense());
                                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                        "message.myriad.jiaren_defenses_set",
                                        Math.round(com.myriad.game.JiarenManager.physicalDefense(living)),
                                        Math.round(com.myriad.game.JiarenManager.magicDefense(living))));
                            }
                        })
                .playToClient(PickStatePayload.TYPE, PickStatePayload.STREAM_CODEC,
                        (payload, context) -> {
                            ClientDataHolder.updatePickState(payload);
                            Myriad.LOGGER.info("[pick-state] recv st={} myF={} cur={} curF={} dl={} cd={} taken={}",
                                    payload.state(), payload.myFaction(), payload.currentName(),
                                    payload.currentFaction(), payload.deadlineTick(),
                                    payload.countdownTick(), payload.taken().keySet());
                            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                            if (payload.state() == 0 && mc.player != null && mc.gui.screen() == null) {
                                mc.setScreenAndShow(new com.myriad.client.gui.PickScreen());
                            }
                        })
                .playToClient(HealFloatPayload.TYPE, HealFloatPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.HealFloater.add(payload.amount()))
                
                .playToClient(DashPayload.TYPE, DashPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.DashController.start(
                                payload.dirX(), payload.dirY(), payload.dirZ(), payload.ticks()))
                .playToClient(AbilityVfxPayload.TYPE, AbilityVfxPayload.CODEC,
                        (payload, context) -> {
                            com.myriad.client.firstperson.FlameDaoView.receive(payload);
                            com.myriad.client.particle.AbilityMeshParticle.receive(payload);
                        })
                .playToClient(AbilityMotionPayload.TYPE, AbilityMotionPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.DashController.startBounded(payload))
                .playToClient(ParabolicMotionPayload.TYPE, ParabolicMotionPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.DashController.startParabola(
                                payload.deltaX(), payload.deltaY(), payload.deltaZ(), payload.arcHeight(), payload.ticks()))
                .playToClient(StarPullMotionPayload.TYPE, StarPullMotionPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.StarPullController.start(
                                payload.centerX(), payload.centerZ(), payload.strength(),
                                payload.velocityBlend(), payload.ticks()))
                .playToClient(MagicBulletCooldownPayload.TYPE, MagicBulletCooldownPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.ClientDataHolder.startMagicBulletCooldown(
                                payload.cooldownTicks(), payload.totalTicks()))
                .playToClient(ZhouTianChannelPayload.TYPE, ZhouTianChannelPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.ZhoutianChannelView.start(
                                payload.durationTicks()))
                .playToClient(FrostViewPayload.TYPE, FrostViewPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.HanzeFrostView.start(
                                payload.durationTicks()))
                
                .playToClient(UiFeedbackPayload.TYPE, UiFeedbackPayload.CODEC,
                        (payload, context) -> com.myriad.client.UiFeedback.receive(payload.kind()))

                .playToServer(ConfirmPlacementPayload.TYPE, ConfirmPlacementPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.confirmPlacement(player);
                            }
                        })
                .playToServer(CancelPlacementPayload.TYPE, CancelPlacementPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.cancelPlacement(player);
                            }
                        })
                .playToServer(MagicBulletFirePayload.TYPE, MagicBulletFirePayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.myriad.combat.MagicBullet.tryFire(player, player.getMainHandItem());
                            }
                        })
                .playToServer(BasicAttackPayload.TYPE, BasicAttackPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.myriad.combat.BasicAttackSystem.tryAttack(player, payload.targetId());
                            }
                        })
                .playToServer(ToggleGunAimPayload.TYPE, ToggleGunAimPayload.CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.myriad.character.skills.shenli.ShenLiSkills.toggleAim(player);
                            }
                        })
                
                
                .playToServer(TrainActionPayload.TYPE, TrainActionPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.trainAction(player, payload.action(), payload.value());
                            }
                        })
                .playToServer(UpgradeSkillPayload.TYPE, UpgradeSkillPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.upgradeSkill(player, payload.skillId());
                            }
                        })
                .playToClient(TeamListPayload.TYPE, TeamListPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.gui.TeamManageScreen.receiveTeamList(payload.teams(), payload.arenaId(), payload.duelAvailable()))
                .playToServer(SelectArenaPayload.TYPE, SelectArenaPayload.CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player)
                                PlayerDataProvider.selectArenaByAdmin(player, payload.arenaId());
                        })
                .playToServer(RequestTeamListPayload.TYPE, RequestTeamListPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.sendTeamList(player);
                            }
                        })
                .playToServer(SetTeamPayload.TYPE, SetTeamPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.setTeamByAdmin(player, payload.playerName(), payload.factionId());
                            }
                        })
                .playToServer(StartMatchPayload.TYPE, StartMatchPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.startMatchFromUi(player, payload.mode(), payload.arenaId());
                            }
                        })
                .playToClient(GameSettingsPayload.TYPE, GameSettingsPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.gui.SettingsScreen.receiveSettings(payload.settings()))
                .playToServer(RequestGameSettingsPayload.TYPE, RequestGameSettingsPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.sendGameSettings(player);
                            }
                        })
                .playToServer(UpdateSettingPayload.TYPE, UpdateSettingPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.updateGameSetting(player, payload.key(), payload.value());
                            }
                        })
                .playToClient(MapFacilitySnapshotPayload.TYPE, MapFacilitySnapshotPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.gui.MapFacilityEditorScreen.receive(
                                payload.entries()))
                .playToServer(RequestMapFacilityEditorPayload.TYPE, RequestMapFacilityEditorPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.myriad.game.MapFacilityEditor.open(player);
                            }
                        })
                .playToServer(MapFacilityActionPayload.TYPE, MapFacilityActionPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                com.myriad.game.MapFacilityEditor.handle(player, payload);
                            }
                        })
                .playToClient(MinimapSnapshotPayload.TYPE, MinimapSnapshotPayload.STREAM_CODEC,
                        (payload, context) -> com.myriad.client.MinimapClientState.update(payload))
                .playToServer(ShopBuyPayload.TYPE, ShopBuyPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                if (com.myriad.game.TacticalSupplySystem.isSupply(payload.equipId())) {
                                    PlayerDataProvider.buySupply(player, payload.equipId());
                                } else {
                                    PlayerDataProvider.buyEquipment(player, payload.equipId());
                                }
                            }
                        })
                .playToServer(SellEquipPayload.TYPE, SellEquipPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.sellEquipment(player, payload.slot());
                            }
                        })
                .playToServer(CraftEquipPayload.TYPE, CraftEquipPayload.STREAM_CODEC,
                        (payload, context) -> {
                            if (context.player() instanceof ServerPlayer player) {
                                PlayerDataProvider.craftEquipment(player, payload.equipId());
                            }
                        });
    }

    public static void sendToPlayer(ServerPlayer player, PlayerDataPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendFrostView(ServerPlayer player, int durationTicks) {
        PacketDistributor.sendToPlayer(player, new FrostViewPayload(durationTicks));
    }

    /** 技能槽位剩余冷却（供同步包读取） */
    public static int cooldownOf(PlayerData data, int slot) {
        if (data == null || data.getCharacterId() == null) {
            return 0;
        }
        CharacterType type = CharacterType.byId(data.getCharacterId());
        if (type == null) {
            return 0;
        }
        Skill skill = com.myriad.character.rework.FirstGroupSkills.resolve(type, slot, data.getSkillLevels());
        return skill == null ? 0 : data.getCooldown(skill.getId());
    }

    public static int factionId(Faction faction) {
        return faction == null ? 0 : faction == Faction.XUANXIAO ? 1 : 2;
    }

    public static int phaseId(GameManager.Phase phase) {
        return phase == null ? 0 : phase == GameManager.Phase.PLAYING ? 1 : phase == GameManager.Phase.ENDED ? 2 : 0;
    }
}
