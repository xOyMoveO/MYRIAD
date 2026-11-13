package com.myriad.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.GameType;

import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 单局对战管理器：宗门分配、角色选择、地图目标与胜负判定。
 * 随服务器生命周期创建/销毁（ServerStartingEvent / ServerStoppedEvent）。
 */
public class GameManager {
    private static GameManager instance;

    public static GameManager get() {
        return instance;
    }

    public static void create() {
        instance = new GameManager();
    }

    public static void destroy() {
        instance = null;
    }

    public enum Phase {
        /** 等待开局 */
        WAITING,
        /** 角色选择阶段（幽冥宗先选，轮流 30 秒，角色唯一） */
        PICKING,
        /** 对局进行中 */
        PLAYING,
        /** 对局已结束 */
        ENDED
    }

    private final Map<UUID, Faction> factions = new HashMap<>();
    private final List<UUID> joinOrder = new ArrayList<>();
    private final java.util.Set<UUID> pendingRoundReset = new java.util.HashSet<>();
    private Phase phase = Phase.WAITING;
    private Faction winner = null;
    private int xuanxiaoScore = 0;
    private int youmingScore = 0;
    private long matchStartTime = -1;
    public static final int ATTRIBUTE_VEIN_COUNT = 3;
    public static final int LARGE_VEIN_CANDIDATE_COUNT = 2;
    /** 地图固定刷新点：月、火、木三座属性灵脉，以及 2 个大型候选点。 */
    private final Map<Integer, BlockPos> attributeVeinSpawns = new HashMap<>();
    private boolean spiritVeinsEnabled = true;
    private final Map<Integer, BlockPos> largeVeinSpawns = new HashMap<>();
    /** 本局实际启用的月、火、木三座属性灵脉与随机 1 座大型灵脉。 */
    private final Map<Integer, SpiritVein> spiritVeins = new HashMap<>();
    private long spiritVeinAvailableAt = -1;
    /** 英雄连杀仅在本局有效，用于击杀赏金。 */
    private final Map<UUID, Integer> killStreaks = new HashMap<>();
    /** 十秒连续击杀窗口，用于一杀至五杀的顶栏播报与语音。 */
    private record MultiKill(int count, long lastKillTick) { }
    private final Map<UUID, MultiKill> multiKills = new HashMap<>();
    /** 当局 K/D，仅用于本局 Tab 展示。 */
    private final Map<UUID, Integer> matchKills = new HashMap<>();
    private final Map<UUID, Integer> matchDeaths = new HashMap<>();
    private static final int SMALL_VEIN_CAPTURE_TICKS = 120;
    private static final int LARGE_VEIN_CAPTURE_TICKS = 200;
    private static final int SMALL_VEIN_RADIUS = 8;
    private static final int LARGE_VEIN_RADIUS = 12;
    private static final int SMALL_VEIN_EFFECT_RADIUS = 5;
    private static final int LARGE_VEIN_EFFECT_RADIUS = 10;

    private static final net.minecraft.resources.Identifier MOON_VEIN_BONUS =
            net.minecraft.resources.Identifier.parse("myriad:moon_vein_bonus");
    private static final net.minecraft.resources.Identifier FIRE_VEIN_BONUS =
            net.minecraft.resources.Identifier.parse("myriad:fire_vein_bonus");
    private static final net.minecraft.resources.Identifier WOOD_VEIN_PHYSICAL_BONUS =
            net.minecraft.resources.Identifier.parse("myriad:wood_vein_physical_bonus");
    private static final net.minecraft.resources.Identifier WOOD_VEIN_MAGIC_BONUS =
            net.minecraft.resources.Identifier.parse("myriad:wood_vein_magic_bonus");

    public enum SpiritAffinity {
        MOON(1, "moon"), FIRE(2, "fire"), WOOD(3, "wood");
        private final int slot;
        private final String id;
        SpiritAffinity(int slot, String id) { this.slot = slot; this.id = id; }
        public int slot() { return slot; }
        public String id() { return id; }
        public static SpiritAffinity bySlot(int slot) {
            for (SpiritAffinity affinity : values()) if (affinity.slot == slot) return affinity;
            return null;
        }
        public static SpiritAffinity byId(String id) {
            if (id == null) return null;
            for (SpiritAffinity affinity : values()) if (affinity.id.equalsIgnoreCase(id)) return affinity;
            return null;
        }
    }
    /** 阵营伤害增益（天道威压）：阵营 -> 到期游戏刻（可叠加层数记录） */
    /** 角色选择：轮次顺序（幽冥宗先选，再玄霄宗，各宗门按加入顺序） */
    private final List<UUID> pickOrder = new ArrayList<>();
    private int pickIndex = 0;
    /** 当前选择者截止游戏刻（30 秒后自动选） */
    private long pickDeadlineTick = -1;
    /** 全部选完后的开局倒计时截止游戏刻（10 秒；-1 = 未开始倒计时） */
    private long pickCountdownEndTick = -1;
    /** 已被选择的角色（角色 id -> 玩家 UUID） */
    private final Map<String, UUID> charPicks = new HashMap<>();
    private static final int PICK_TICKS = 30 * 20;
    private static final int PICK_COUNTDOWN_TICKS = 10 * 20;
    private transient MinecraftServer server;
    private long matchEndTime = -1;
    private final Map<UUID, Double> heroDamage = new HashMap<>();
    /** 整局最终承伤（减伤后，包含模组护盾实际吸收）与实际受到治疗。 */
    private final Map<UUID, Double> damageTaken = new HashMap<>();
    private final Map<UUID, Double> healingReceived = new HashMap<>();
    private transient boolean returnToLobbyPending;
    public void recordDamage(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target, float amount) {
        if (phase == Phase.PLAYING && target instanceof ServerPlayer && amount > 0)
            heroDamage.merge(attacker.getUUID(), (double) amount, Double::sum);
    }

    public void recordDamageTaken(ServerPlayer target, float amount) {
        if (phase == Phase.PLAYING && amount > 0)
            damageTaken.merge(target.getUUID(), (double) amount, Double::sum);
    }

    public void recordHealingReceived(ServerPlayer target, float amount) {
        if (phase == Phase.PLAYING && amount > 0)
            healingReceived.merge(target.getUUID(), (double) amount, Double::sum);
    }

    public static com.google.gson.JsonElement snapshot() { return MatchProgress.JSON.toJsonTree(instance); }
    public static void restore(com.google.gson.JsonElement value, MinecraftServer server) {
        instance = MatchProgress.JSON.fromJson(value, GameManager.class);
        instance.setServer(server);
    }

    public Phase getPhase() {
        return phase;
    }

    public Faction getWinner() {
        return winner;
    }

    public int getXuanxiaoScore() {
        return xuanxiaoScore;
    }

    public int getYoumingScore() {
        return youmingScore;
    }

    /** 当前对局已经进行的 tick；不在对局中返回 -1。 */
    public long getMatchElapsedTicks(long gameTime) {
        return matchStartTime >= 0 && (phase == Phase.PLAYING || phase == Phase.ENDED)
                ? Math.max(0, (phase == Phase.ENDED && matchEndTime >= 0 ? matchEndTime : gameTime) - matchStartTime) : -1;
    }

    public long getMatchStartTime() {
        return phase == Phase.PLAYING ? matchStartTime : -1;
    }

    public Faction factionOf(ServerPlayer player) {
        if (TrainingMode.isEnabled()) {
            PlayerData data = PlayerDataProvider.get(player);
            if (data != null) return data.getFaction();
        }
        return factions.get(player.getUUID());
    }

    public int killsOf(ServerPlayer player) { return matchKills.getOrDefault(player.getUUID(), 0); }
    public int deathsOf(ServerPlayer player) { return matchDeaths.getOrDefault(player.getUUID(), 0); }

    /** 记录服务器引用，供广播使用 */
    public void setServer(MinecraftServer server) {
        this.server = server;
        loadSpiritVeinSpawns();
    }

    public void reloadArenaObjectives() {
        resetMatchObjectives();
        loadSpiritVeinSpawns();
    }

    private boolean arenaCapacityAllows(List<ServerPlayer> players) {
        if (players.size() <= MatchArena.active().teamSize * 2) return true;
        broadcast(Component.translatable("message.myriad.arena_capacity", MatchArena.active().displayName(),
                MatchArena.active().teamSize * 2));
        return false;
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (ModState.isEnabled() && pendingRoundReset.remove(player.getUUID())) {
            PlayerDataProvider.resetForHostSession(player);
            player.setGameMode(GameType.ADVENTURE);
        }
        if (ModState.isEnabled() && phase == Phase.WAITING) GuideBook.give(player);
        if (!joinOrder.contains(player.getUUID())) {
            joinOrder.add(player.getUUID());
        }
        Faction faction = factions.get(player.getUUID());
        if (faction == null && phase == Phase.PLAYING) {
            // 对局中途加入：补人数较少的宗门。
            long xuanxiaoCount = factions.values().stream().filter(f -> f == Faction.XUANXIAO).count();
            long youmingCount = factions.values().stream().filter(f -> f == Faction.YOUMING).count();
            if (Math.min(xuanxiaoCount, youmingCount) >= MatchArena.active().teamSize) {
                PlayerDataProvider.get(player).setFaction(null);
                player.setGameMode(GameType.SPECTATOR);
                LobbySystem.teleportToLobby(player);
                player.sendSystemMessage(Component.translatable("message.myriad.arena_full"));
                return;
            }
            faction = xuanxiaoCount <= youmingCount ? Faction.XUANXIAO : Faction.YOUMING;
            assign(player, faction);
            PlayerData lateData = PlayerDataProvider.get(player);
            lateData.setGold(500);
            lateData.resetMatchGoldEarned(500);
            player.setGameMode(GameType.ADVENTURE);
            // 中途加入：清空背包其余物品，仅保留初始武器
            PlayerDataProvider.resetInitialInventory(player);
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data != null && faction != null) {
            data.setFaction(faction);
            player.refreshTabListName();
        }
    }

    public void onPlayerQuit(ServerPlayer player) {
        // Keep the player's faction, character reservation and K/D for reconnecting.
        if (phase == Phase.WAITING) factions.remove(player.getUUID());
    }

    /** 开局：按加入顺序将玩家均衡分配到玄霄宗与幽冥宗。 */
    public void startMatch(MinecraftServer server) {
        this.server = server;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.isEmpty()) {
            return;
        }
        if (phase == Phase.PLAYING || phase == Phase.PICKING) {
            broadcast(Component.translatable("message.myriad.match_already_running"));
            return;
        }
        if (!arenaCapacityAllows(players)) return;
        com.myriad.Myriad.LOGGER.info("[match] start: {} player(s): {}",
                players.size(), players.stream().map(p -> p.getName().getString() + "@" + System.identityHashCode(p))
                        .toList());
        factions.clear();
        java.util.Collections.shuffle(players);
        // 对称 PvP：自动均分两宗门；奇数人数时玄霄宗多一人。
        int xuanxiaoCount = Math.max(1, (players.size() + 1) / 2);
        xuanxiaoCount = Math.min(xuanxiaoCount, players.size() - 1);
        boolean swap = server.overworld().getRandom().nextBoolean();
        for (int i = 0; i < players.size(); i++) {
            assign(players.get(i), (i < xuanxiaoCount) != swap ? Faction.XUANXIAO : Faction.YOUMING);
        }
        // 开局重置：清空背包其余物品，仅保留初始武器
        for (ServerPlayer p : players) {
            PlayerDataProvider.resetInitialInventory(p);
        }
        xuanxiaoScore = 0;
        youmingScore = 0;
        winner = null;
        resetMatchObjectives();
        beginPicking(server);
    }

    private void assign(ServerPlayer player, Faction faction) {
        factions.put(player.getUUID(), faction);
        PlayerData data = PlayerDataProvider.get(player);
        com.myriad.Myriad.LOGGER.info("[match] assign {}@{} -> {} (cap={})",
                player.getName().getString(), System.identityHashCode(player), faction, data != null);
        if (data != null) {
            data.setFaction(faction);
            com.myriad.Myriad.LOGGER.info("[match] assign done, readback={}", data.getFaction());
        }
        player.sendSystemMessage(Component.translatable("message.myriad.team_assigned", faction.displayName()));
        player.refreshTabListName();
    }

    /** 手动分配开局：使用当前 PlayerData 宗门直接开局，未分配玩家自动补入幽冥宗。 */
    public void startMatchWithCurrent(MinecraftServer server) {
        this.server = server;
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        if (players.isEmpty() || phase == Phase.PLAYING || phase == Phase.PICKING) {
            if (phase == Phase.PLAYING || phase == Phase.PICKING) {
                broadcast(Component.translatable("message.myriad.match_already_running"));
            }
            return;
        }
        int xuanxiaoCount = 0;
        int youmingCount = 0;
        if (!arenaCapacityAllows(players)) return;
        for (ServerPlayer p : players) {
            PlayerData data = PlayerDataProvider.get(p);
            if (data != null && data.getFaction() == Faction.XUANXIAO) {
                xuanxiaoCount++;
            } else if (data != null && data.getFaction() == Faction.YOUMING) {
                youmingCount++;
            }
        }
        if (xuanxiaoCount == 0 || youmingCount == 0 || Math.abs(xuanxiaoCount - youmingCount) > 1) {
            broadcast(Component.translatable("message.myriad.team_need_both"));
            return;
        }
        if (Math.max(xuanxiaoCount, youmingCount) > MatchArena.active().teamSize) {
            broadcast(Component.translatable("message.myriad.arena_team_capacity", MatchArena.active().teamSize));
            return;
        }
        factions.clear();
        for (ServerPlayer p : players) {
            PlayerData data = PlayerDataProvider.get(p);
            Faction f = data == null ? null : data.getFaction();
            if (f == null) {
                f = xuanxiaoCount <= youmingCount ? Faction.XUANXIAO : Faction.YOUMING;
                data.setFaction(f);
                p.refreshTabListName();
                if (f == Faction.XUANXIAO) xuanxiaoCount++; else youmingCount++;
            }
            assign(p, f);
        }
        // 开局重置：清空背包其余物品，仅保留初始武器
        for (ServerPlayer p : players) {
            PlayerDataProvider.resetInitialInventory(p);
        }
        xuanxiaoScore = 0;
        youmingScore = 0;
        winner = null;
        resetMatchObjectives();
        beginPicking(server);
    }

    // ---- 角色选择阶段：幽冥宗先选，轮流 30 秒，每个角色仅一人可选 ----

    /** 进入角色选择阶段（训练模式跳过，直接开局） */
    private void beginPicking(MinecraftServer server) {
        this.server = server;
        TrainingMode.setEnabled(false);
        TrainingMode.setNoCooldown(false);
        TrainingMode.setNoGoldCost(false);
        TrainingMode.setNoMana(false);
        // Manual character previews must not reserve heroes or carry progression into draft.
        PlayerDataProvider.clearModGeneratedState(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Faction team = factions.get(player.getUUID());
            PlayerDataProvider.resetForHostSession(player);
            PlayerDataProvider.get(player).setFaction(team);
        }
        pickOrder.clear();
        List<UUID> youming = new ArrayList<>();
        List<UUID> xuanxiao = new ArrayList<>();
        for (UUID uid : joinOrder) {
            Faction f = factions.get(uid);
            if (f == Faction.YOUMING) {
                youming.add(uid);
            } else if (f == Faction.XUANXIAO) {
                xuanxiao.add(uid);
            }
        }
        // Opening side picks one, then alternate two consecutive picks per side.
        int y = 0, x = 0;
        if (!youming.isEmpty()) pickOrder.add(youming.get(y++));
        while (y < youming.size() || x < xuanxiao.size()) {
            for (int n = 0; n < 2 && x < xuanxiao.size(); n++) pickOrder.add(xuanxiao.get(x++));
            for (int n = 0; n < 2 && y < youming.size(); n++) pickOrder.add(youming.get(y++));
        }
        pickIndex = 0;
        charPicks.clear();
        pickCountdownEndTick = -1;
        if (pickOrder.isEmpty()) {
            // 无有效玩家（极端情况）：直接开局
            finishPicking(server);
            return;
        }
        phase = Phase.PICKING;
        pickDeadlineTick = server.overworld().getGameTime() + PICK_TICKS;
        broadcast(Component.translatable("message.myriad.pick_start"));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new com.myriad.network.NetworkSetup.OpenPickScreenPayload());
        }
        broadcastPickState();
    }

    /** 当前轮到谁选择（null = 无） */
    private UUID currentPicker() {
        return pickIndex >= 0 && pickIndex < pickOrder.size() ? pickOrder.get(pickIndex) : null;
    }

    /** 对称宗门选人：双方共享全部角色池，仍保持角色唯一。 */
    public boolean onPick(ServerPlayer player, com.myriad.character.CharacterType type) {
        if(type==null||type.developmentOnly())return false;
        if (phase != Phase.PICKING || type == null || !player.getUUID().equals(currentPicker())) {
            return false;
        }
        if (factions.get(player.getUUID()) == null) {
            return false;
        }
        if (charPicks.containsKey(type.getId())) {
            return false;
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) {
            return false;
        }
        data.setCharacterId(type.getId());
        // 开局选角即获得 1 个技能点（至少 1 点，不叠加）
        data.setSkillPoints(Math.max(1, data.getSkillPoints()));
        charPicks.put(type.getId(), player.getUUID());
        PlayerDataProvider.applyChosenCharacter(player, data);
        PlayerDataProvider.syncToClient(player);
        broadcast(Component.translatable("message.myriad.pick_chosen",
                player.getName().getString(), Component.translatable(type.nameKey())));
        advancePick(server);
        broadcastPickState();
        return true;
    }

    private void advancePick(MinecraftServer server) {
        pickIndex++;
        if (pickIndex >= pickOrder.size()) {
            // 全部选择完成：10 秒倒计时后正式开局（选人界面同步显示倒计时）
            pickCountdownEndTick = server.overworld().getGameTime() + PICK_COUNTDOWN_TICKS;
            broadcast(Component.translatable("message.myriad.pick_all_done", PICK_COUNTDOWN_TICKS / 20));
        } else {
            pickDeadlineTick = server.overworld().getGameTime() + PICK_TICKS;
        }
    }

    /** 选人阶段每 tick：全部选完后的倒计时结束即开局；否则处理超时自动选 / 掉线跳过 / 无可用角色跳过 */
    public void tick(MinecraftServer server, long gameTime) {
        if (returnToLobbyPending) {
            resetToLobby(server, true);
            return;
        }
        if (phase == Phase.PLAYING) {
            enforcePlayingModes(server);
            tickSpiritVein(server, gameTime);
            tickPassiveGold(server, gameTime);
            return;
        }
        if (phase != Phase.PICKING) {
            return;
        }
        if (gameTime % 20 == 0) {
            com.myriad.Myriad.LOGGER.info("[gm-tick] phase=PICKING gt={} dl={} cd={} cur={}",
                    gameTime, pickDeadlineTick, pickCountdownEndTick, currentPicker());
        }
        if (pickCountdownEndTick >= 0) {
            if (gameTime >= pickCountdownEndTick) {
                finishPicking(server);
            }
            return;
        }
        UUID cur = currentPicker();
        if (cur == null) {
            finishPicking(server);
            return;
        }
        ServerPlayer cp = server.getPlayerList().getPlayer(cur);
        if (cp == null || !cp.isAlive()) {
            advancePick(server);
            broadcastPickState();
            return;
        }
        if (availableForFaction(factions.get(cur)).isEmpty()) {
            broadcast(Component.translatable("message.myriad.pick_skip_no_option",
                    cp.getName().getString()));
            advancePick(server);
            broadcastPickState();
            return;
        }
        if (gameTime >= pickDeadlineTick) {
            autoPick(server, cp);
        }
    }

    private void enforcePlayingModes(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!factions.containsKey(player.getUUID())) continue;
            if (HeroRespawnSystem.isWaiting(player)) continue;
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) player.setCamera(player);
            if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                player.setGameMode(GameType.ADVENTURE);
            }
        }
    }

    /** 对局中所有参赛玩家每 5 秒获得经全局收益倍率调整后的金币，死亡等待期间也不会中断。 */
    private void tickPassiveGold(MinecraftServer server, long gameTime) {
        if (matchStartTime < 0) return;
        long elapsed = gameTime - matchStartTime;
        if (elapsed <= 0 || elapsed % 100 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (factionOf(player) == null) continue;
            PlayerData data = PlayerDataProvider.get(player);
            if (data == null) continue;
            data.addGold(GlobalBalance.goldIncome(MatchBalance.farmReward(player, 10, false)));
            PlayerDataProvider.syncToClient(player);
        }
        com.myriad.network.MatchTabPayload.sync(server);
    }

    /** 超时自动选择：取本阵营第一个未选角色；无则跳过 */
    private void autoPick(MinecraftServer server, ServerPlayer player) {
        com.myriad.Myriad.LOGGER.info("[pick] autoPick fired for {} (gt={} dl={})",
                player.getName().getString(), server.overworld().getGameTime(), pickDeadlineTick);
        List<com.myriad.character.CharacterType> avail = availableForFaction(factions.get(player.getUUID()));
        if (avail.isEmpty()) {
            broadcast(Component.translatable("message.myriad.pick_skip_no_option",
                    player.getName().getString()));
            advancePick(server);
            broadcastPickState();
            return;
        }
        com.myriad.character.CharacterType type = avail.get(0);
        PlayerData data = PlayerDataProvider.get(player);
        if (data == null) {
            advancePick(server);
            broadcastPickState();
            return;
        }
        data.setCharacterId(type.getId());
        // 开局选角即获得 1 个技能点（至少 1 点，不叠加）
        data.setSkillPoints(Math.max(1, data.getSkillPoints()));
        charPicks.put(type.getId(), player.getUUID());
        PlayerDataProvider.applyChosenCharacter(player, data);
        PlayerDataProvider.syncToClient(player);
        broadcast(Component.translatable("message.myriad.pick_auto",
                player.getName().getString(), Component.translatable(type.nameKey())));
        advancePick(server);
        broadcastPickState();
    }

    /** 对称模式下双方共享的未选择角色池。 */
    public List<com.myriad.character.CharacterType> availableForFaction(Faction faction) {
        List<com.myriad.character.CharacterType> result = new ArrayList<>();
        if (faction == null) {
            return result;
        }
        for (com.myriad.character.CharacterType type : com.myriad.character.CharacterType.playableValues()) {
            if (!charPicks.containsKey(type.getId())) {
                result.add(type);
            }
        }
        return result;
    }

    private void finishPicking(MinecraftServer server) {
        phase = Phase.PLAYING;
        pickCountdownEndTick = -1;
        matchStartTime = server.overworld().getGameTime();
        matchEndTime = -1;
        if (!rollSpiritVeins()) {
            broadcast(Component.translatable("message.myriad.vein_config_incomplete",
                    attributeVeinSpawns.size(), largeVeinSpawns.size()));
        } else if (!spiritVeins.isEmpty()) {
            spiritVeinAvailableAt = matchStartTime + 60L * 20L;
            broadcast(Component.translatable("message.myriad.vein_will_open", 60));
        }
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        // 阵营和角色已经确定，正式进入对局时把所有玩家统一送回本方泉水。
        // 这样不会继承选人阶段或上一局结束时的站位，也能让首波兵线从
        // 明确的双方基地出发。
        LaneSystem.teleportPlayersToBases(server.overworld(), players);
        for (ServerPlayer player : players) {
            PlayerData data = PlayerDataProvider.get(player);
            data.setGold(500);
            data.resetMatchGoldEarned(500);
        }
        // 地图编辑和训练时玩家可能处于创造/旁观模式；正式对局统一使用冒险模式，
        // 既允许战斗与物品交互，又避免玩家破坏战场方块。
        for (ServerPlayer player : players) {
            if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                player.setGameMode(GameType.ADVENTURE);
            }
        }
        broadcastLineups(players);
        broadcastMatchStarted();
        // 通知客户端关闭选人界面
        for (ServerPlayer p : players) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new com.myriad.network.NetworkSetup.PickStatePayload(
                            1, "", "", "", -1, -1, java.util.Map.of(), java.util.Map.of()));
        }
        syncAll();
    }

    /** 广播当前选人状态给所有玩家 */
    private void broadcastPickState() {
        if (server == null) {
            return;
        }
        com.myriad.network.MatchTabPayload.sync(server);
        UUID cur = currentPicker();
        String curName = "";
        String curFaction = "";
        long deadline = -1;
        if (cur != null) {
            ServerPlayer cp = server.getPlayerList().getPlayer(cur);
            if (cp != null) {
                curName = cp.getName().getString();
                Faction cf = factions.get(cur);
                curFaction = cf == null ? "" : cf.getName();
                deadline = pickDeadlineTick;
            }
        }
        java.util.Map<String, String> taken = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> takenUuids = new java.util.LinkedHashMap<>();
        for (com.myriad.character.CharacterType type :
                com.myriad.character.CharacterType.playableValues()) {
            UUID uid = charPicks.get(type.getId());
            if (uid != null) {
                ServerPlayer sp = server.getPlayerList().getPlayer(uid);
                taken.put(type.getId(), sp == null ? "?" : sp.getName().getString());
                takenUuids.put(type.getId(), uid.toString());
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Faction myF = factions.get(p.getUUID());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new com.myriad.network.NetworkSetup.PickStatePayload(
                            0,
                            myF == null ? "" : myF.getName(),
                            curName,
                            curFaction,
                            deadline,
                            pickCountdownEndTick,
                            taken,
                            takenUuids));
        }
    }

    /** 本局第 index 次三选一的品质（全员一致） */

    /** 天道威压：记录一次阵营伤害增益（30 秒，可叠加） */

    /** 该阵营当前伤害增益层数（% = 层数 × 5） */

    /** 赛前配置固定刷新点；属性灵脉 1=月、2=火、3=木，大型候选编号 1~2。 */
    public boolean setSpiritVeinSpawn(boolean large, int slot, BlockPos pos) {
        int maxSlot = large ? LARGE_VEIN_CANDIDATE_COUNT : ATTRIBUTE_VEIN_COUNT;
        if (phase != Phase.WAITING || slot < 1 || slot > maxSlot) return false;
        Map<Integer, BlockPos> targets = large ? largeVeinSpawns : attributeVeinSpawns;
        if (pos == null) targets.remove(slot);
        else targets.put(slot, pos.immutable());
        saveSpiritVeinSpawns();
        return true;
    }

    /** 地图编辑器只读快照；返回副本，避免客户端编辑链路绕过统一保存入口。 */
    public Map<Integer, BlockPos> spiritVeinSpawnSnapshot(boolean large) {
        return Map.copyOf(large ? largeVeinSpawns : attributeVeinSpawns);
    }

    public boolean spiritVeinsEnabled() { return spiritVeinsEnabled; }

    /** 固定刷新点属于地图配置，写入世界存档并跨服务器重启保留。 */
    private void saveSpiritVeinSpawns() {
        if (server == null) return;
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("enabled", spiritVeinsEnabled);
        root.add("attribute", veinPositionsToJson(attributeVeinSpawns));
        root.add("large", veinPositionsToJson(largeVeinSpawns));
        try {
            java.nio.file.Files.writeString(spiritVeinConfigPath(), root.toString());
        } catch (java.io.IOException e) {
            com.myriad.Myriad.LOGGER.error("[vein] failed to save fixed spawn points", e);
        }
    }

    private void loadSpiritVeinSpawns() {
        spiritVeinsEnabled = true;
        attributeVeinSpawns.clear();
        largeVeinSpawns.clear();
        java.nio.file.Path path = spiritVeinConfigPath();
        if (!java.nio.file.Files.isRegularFile(path)) return;
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(path)).getAsJsonObject();
            spiritVeinsEnabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            com.google.gson.JsonObject attributes = root.getAsJsonObject("attribute");
            if (attributes == null) attributes = root.getAsJsonObject("small");
            veinPositionsFromJson(attributes, attributeVeinSpawns, ATTRIBUTE_VEIN_COUNT);
            veinPositionsFromJson(root.getAsJsonObject("large"), largeVeinSpawns, LARGE_VEIN_CANDIDATE_COUNT);
            if (!spiritVeinsEnabled) {
                // Map configuration overrides old dedicated-server checkpoints too.
                spiritVeins.clear();
                spiritVeinAvailableAt = -1;
                clearSpiritVeinBonuses();
            }
        } catch (Exception e) {
            com.myriad.Myriad.LOGGER.error("[vein] failed to load fixed spawn points", e);
        }
    }

    private java.nio.file.Path spiritVeinConfigPath() {
        return MatchArena.configPath(server, "myriad_vein_spawns.json");
    }

    private static com.google.gson.JsonObject veinPositionsToJson(Map<Integer, BlockPos> positions) {
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        for (Map.Entry<Integer, BlockPos> entry : positions.entrySet()) {
            BlockPos pos = entry.getValue();
            com.google.gson.JsonObject value = new com.google.gson.JsonObject();
            value.addProperty("x", pos.getX());
            value.addProperty("y", pos.getY());
            value.addProperty("z", pos.getZ());
            result.add(String.valueOf(entry.getKey()), value);
        }
        return result;
    }

    private static void veinPositionsFromJson(com.google.gson.JsonObject json,
            Map<Integer, BlockPos> output, int maxSlot) {
        if (json == null) return;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
            int slot = Integer.parseInt(entry.getKey());
            if (slot < 1 || slot > maxSlot) continue;
            com.google.gson.JsonObject value = entry.getValue().getAsJsonObject();
            output.put(slot, new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(),
                    value.get("z").getAsInt()));
        }
    }

    /** 三属性灵脉固定开放；大型灵脉暂停，保留候选点存档。 */
    private boolean rollSpiritVeins() {
        spiritVeins.clear();
        if (!spiritVeinsEnabled) return true;
        if (attributeVeinSpawns.size() != ATTRIBUTE_VEIN_COUNT) return false;
        for (SpiritAffinity affinity : SpiritAffinity.values()) {
            spiritVeins.put(affinity.slot(), new SpiritVein(
                    attributeVeinSpawns.get(affinity.slot()), false, affinity));
        }
        return true;
    }

    /** 英雄击杀：金币、经验与连杀赏金，保证双方发生正面冲突时有明确收益。 */
    public void onHeroKill(ServerPlayer killer, ServerPlayer victim) {
        if (phase != Phase.PLAYING || killer == victim
                || factionOf(killer) == null || factionOf(killer) == factionOf(victim)) {
            return;
        }
        int victimStreak = killStreaks.getOrDefault(victim.getUUID(), 0);
        int priorDeaths=victim.getPersistentData().getIntOr("myriad_deaths_without_kill",0);
        int gap=PlayerDataProvider.get(victim).getLevel()-PlayerDataProvider.get(killer).getLevel();
        int reward = GlobalBalance.goldIncome(MatchBalance.killReward(false,MatchBalance.elapsed(killer),victimStreak,priorDeaths,gap));
        com.myriad.player.PlayerData killerData = PlayerDataProvider.get(killer);
        if (killerData != null) {
            killerData.addGold(reward);
            com.myriad.player.LevelSystem.addXp(killer, killerData,
                    MatchBalance.killReward(true,MatchBalance.elapsed(killer),victimStreak,priorDeaths,gap));
            PlayerDataProvider.syncToClient(killer);
        }
        killStreaks.put(killer.getUUID(), killStreaks.getOrDefault(killer.getUUID(), 0) + 1);
        killStreaks.remove(victim.getUUID());
        killer.getPersistentData().putInt("myriad_deaths_without_kill",0);
        long now = killer.level().getGameTime();
        MultiKill prior = multiKills.get(killer.getUUID());
        int multiKill = prior != null && now - prior.lastKillTick <= 200
                ? Math.min(5, prior.count + 1) : 1;
        multiKills.put(killer.getUUID(), new MultiKill(multiKill, now));
        multiKills.remove(victim.getUUID());
        if (server != null) {
            var announcement = new com.myriad.network.KillAnnouncementPayload(
                    killer.getUUID(), killer.getName().getString(), victim.getUUID(),
                    victim.getName().getString(), multiKill);
            for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, announcement);
            }
        }
    }

    /** 记录一次实际发生的英雄死亡；击杀奖励与“不扣命”效果不影响 K/D 统计。 */
    public void recordHeroDeath(ServerPlayer victim, net.minecraft.world.entity.Entity killerEntity) {
        if (phase != Phase.PLAYING || factionOf(victim) == null) return;
        matchDeaths.merge(victim.getUUID(), 1, Integer::sum);
        multiKills.remove(victim.getUUID());
        victim.refreshTabListName();
        if (killerEntity instanceof ServerPlayer killer && killer != victim
                && factionOf(killer) != null && factionOf(killer) != factionOf(victim)) {
            matchKills.merge(killer.getUUID(), 1, Integer::sum);
            killer.refreshTabListName();
        }
    }

    private void resetMatchObjectives() {
        if (server != null) HeroRespawnSystem.reset(server);
        spiritVeins.clear();
        clearSpiritVeinBonuses();
        spiritVeinAvailableAt = -1;
        killStreaks.clear();
        multiKills.clear();
        matchKills.clear();
        matchDeaths.clear();
        heroDamage.clear();
        damageTaken.clear();
        healingReceived.clear();
    }

    /** 三座属性灵脉与一座大型灵脉：占领后允许轮换，敌方需连续施压才会夺取。 */
    private void tickSpiritVein(MinecraftServer server, long gameTime) {
        if (spiritVeins.isEmpty() || spiritVeinAvailableAt < 0 || gameTime < spiritVeinAvailableAt) {
            syncSpiritVeinHud(server, false);
            return;
        }
        for (Map.Entry<Integer, SpiritVein> entry : spiritVeins.entrySet()) {
            SpiritVein vein = entry.getValue();
            int xuanxiao = 0, youming = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isAlive() || player.isSpectator() || player.level() != server.overworld()
                        || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(vein.pos))
                                > vein.radius() * vein.radius()) continue;
                Faction faction = factionOf(player);
                if (faction == Faction.XUANXIAO) xuanxiao++;
                if (faction == Faction.YOUMING) youming++;
            }
            vein.xuanxiaoPlayers = xuanxiao;
            vein.youmingPlayers = youming;
            if (vein.owner == null) {
                if (xuanxiao > youming) {
                    int step = youming > 0 ? (gameTime % 2 == 0 ? 1 : 0) : 1;
                    vein.progress = Math.min(vein.captureTicks(), vein.progress + step);
                } else if (youming > xuanxiao) {
                    int step = xuanxiao > 0 ? (gameTime % 2 == 0 ? 1 : 0) : 1;
                    vein.progress = Math.max(-vein.captureTicks(), vein.progress - step);
                }
            } else {
                int ownerPlayers = vein.owner == Faction.XUANXIAO ? xuanxiao : youming;
                int enemyPlayers = vein.owner == Faction.XUANXIAO ? youming : xuanxiao;
                if (enemyPlayers > 0 && ownerPlayers == 0) {
                    vein.contestedTicks++;
                    if (vein.contestedTicks <= GameRules.VEIN_CONTEST_GRACE_TICKS) {
                        vein.progress = vein.owner == Faction.XUANXIAO ? vein.captureTicks() : -vein.captureTicks();
                    } else if (vein.contestedTicks == GameRules.VEIN_CONTEST_GRACE_TICKS + 1) {
                        vein.progress = 0;
                    } else {
                        vein.progress = vein.owner == Faction.XUANXIAO
                                ? Math.max(-vein.captureTicks(), vein.progress - 1)
                                : Math.min(vein.captureTicks(), vein.progress + 1);
                    }
                } else if (enemyPlayers > 0) {
                    vein.contestedTicks = Math.max(0, vein.contestedTicks - 1);
                } else {
                    vein.contestedTicks = 0;
                    vein.progress = vein.owner == Faction.XUANXIAO ? vein.captureTicks() : -vein.captureTicks();
                }
            }
            if (gameTime % 10 == 0) spawnSpiritVeinCircle(server.overworld(), vein, gameTime);
            Faction newOwner = vein.progress >= vein.captureTicks() ? Faction.XUANXIAO
                    : vein.progress <= -vein.captureTicks() ? Faction.YOUMING : null;
            if (newOwner != null && newOwner != vein.owner) {
                vein.owner = newOwner;
                refreshSpiritVeinBonuses();
                Component veinName = vein.displayName();
                broadcast(MatchMessages.Tone.OBJECTIVE,
                        Component.translatable("message.myriad.vein_captured", veinName,
                        newOwner.displayName(), vein.large ? 30 : 10, vein.large ? 20 : 5));
            }
            int enemyPlayers = vein.owner == Faction.XUANXIAO ? youming : xuanxiao;
            if (vein.owner != null && Math.abs(vein.progress) == vein.captureTicks()
                    && enemyPlayers == 0 && gameTime % GameRules.VEIN_INCOME_PULSE_TICKS == 0) {
                rewardSpiritVeinPulse(vein.owner, vein);
            }
        }
        if (gameTime % 20 == 0) refreshSpiritVeinBonuses();
        syncSpiritVeinHud(server, true);
    }

    /** 0=附近没有己方灵脉，1=己方灵脉，2=己方灵脉且有敌方玩家争夺。 */
    public int ownedVeinStatus(ServerPlayer player, double range) {
        Faction faction = factionOf(player);
        if (faction == null) return 0;
        double rangeSqr = range * range;
        for (SpiritVein vein : spiritVeins.values()) {
            if (vein.owner != faction || player.distanceToSqr(
                    net.minecraft.world.phys.Vec3.atCenterOf(vein.pos)) > rangeSqr) continue;
            int enemies = faction == Faction.XUANXIAO ? vein.youmingPlayers : vein.xuanxiaoPlayers;
            return enemies > 0 ? 2 : 1;
        }
        return 0;
    }

    /** 小型半径 5、大型半径 10 的双层地面圆环；颜色随占领方变化。 */
    private static void spawnSpiritVeinCircle(net.minecraft.server.level.ServerLevel level,
            SpiritVein vein, long gameTime) {
        double radius = vein.large ? LARGE_VEIN_EFFECT_RADIUS : SMALL_VEIN_EFFECT_RADIUS;
        int points = vein.large ? 64 : 40;
        int color = vein.owner == Faction.XUANXIAO ? 0xFF594F
                : vein.owner == Faction.YOUMING ? 0x55BFFF : 0xFFD85A;
        DustParticleOptions primary = new DustParticleOptions(color, vein.large ? 1.5F : 1.2F);
        DustParticleOptions accent = new DustParticleOptions(0xF4FFFF, vein.large ? .9F : .7F);
        double phase = (gameTime % 80) * Math.PI / 40.0;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            double x = vein.pos.getX() + 0.5 + Math.cos(angle) * radius;
            double z = vein.pos.getZ() + 0.5 + Math.sin(angle) * radius;
            level.sendParticles(primary, x, vein.pos.getY() + .18, z, 1, 0, 0, 0, 0);
            if (i % 4 == 0) {
                double inner = radius - .35;
                level.sendParticles(accent,
                        vein.pos.getX() + 0.5 + Math.cos(angle + phase) * inner,
                        vein.pos.getY() + .28,
                        vein.pos.getZ() + 0.5 + Math.sin(angle + phase) * inner,
                        1, 0, 0, 0, 0);
            }
        }
    }

    /** 向每名玩家同步其当前所在灵脉；范围外发送隐藏状态，避免 HUD 残留。 */
    private void syncSpiritVeinHud(MinecraftServer server, boolean available) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map.Entry<Integer, SpiritVein> current = null;
            double nearest = Double.MAX_VALUE;
            if (available && player.isAlive() && player.level() == server.overworld()) {
                for (Map.Entry<Integer, SpiritVein> entry : spiritVeins.entrySet()) {
                    SpiritVein vein = entry.getValue();
                    double distance = player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(vein.pos));
                    if (distance <= vein.radius() * vein.radius() && distance < nearest) {
                        current = entry;
                        nearest = distance;
                    }
                }
            }
            if (current == null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.myriad.network.NetworkSetup.VeinStatusPayload(false, 0, false, 0, 0, 0, 1));
            } else {
                SpiritVein vein = current.getValue();
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                        new com.myriad.network.NetworkSetup.VeinStatusPayload(true, current.getKey(), vein.large,
                                vein.xuanxiaoPlayers, vein.youmingPlayers, vein.progress, vein.captureTicks()));
            }
        }
    }

    /** 持续控制收益：每 5 秒发给全队，死亡成员同样获得团队目标收益。 */
    private void rewardSpiritVeinPulse(Faction faction, SpiritVein vein) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (factionOf(player) == faction) {
                com.myriad.player.PlayerData data = PlayerDataProvider.get(player);
                if (data != null) {
                    data.addGold(GlobalBalance.goldIncome(vein.large ? 30 : 10));
                    com.myriad.player.LevelSystem.addXp(player, data, vein.large ? 20 : 5);
                    PlayerDataProvider.syncToClient(player);
                }
            }
        }
    }

    /** 月=法强、火=物攻、木=物防与法防；控制权转移时移除旧宗门并赋予新宗门。 */
    private void refreshSpiritVeinBonuses() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearSpiritVeinBonuses(player);
            Faction faction = factionOf(player);
            if (faction == null || phase != Phase.PLAYING) continue;
            for (SpiritVein vein : spiritVeins.values()) {
                if (vein.owner != faction || vein.affinity == null) continue;
                switch (vein.affinity) {
                    case MOON -> addVeinBonus(player, com.myriad.registry.ModAttributes.SPELL_POWER,
                            MOON_VEIN_BONUS);
                    case FIRE -> addVeinBonus(player, com.myriad.registry.ModAttributes.PHYSICAL_ATTACK,
                            FIRE_VEIN_BONUS);
                    case WOOD -> {
                        addVeinBonus(player, com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE,
                                WOOD_VEIN_PHYSICAL_BONUS);
                        addVeinBonus(player, com.myriad.registry.ModAttributes.MAGIC_RESISTANCE,
                                WOOD_VEIN_MAGIC_BONUS);
                    }
                }
            }
        }
    }

    private void clearSpiritVeinBonuses() {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) clearSpiritVeinBonuses(player);
    }

    private static void clearSpiritVeinBonuses(ServerPlayer player) {
        removeVeinBonus(player, com.myriad.registry.ModAttributes.SPELL_POWER, MOON_VEIN_BONUS);
        removeVeinBonus(player, com.myriad.registry.ModAttributes.PHYSICAL_ATTACK, FIRE_VEIN_BONUS);
        removeVeinBonus(player, com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE, WOOD_VEIN_PHYSICAL_BONUS);
        removeVeinBonus(player, com.myriad.registry.ModAttributes.MAGIC_RESISTANCE, WOOD_VEIN_MAGIC_BONUS);
    }

    private static void addVeinBonus(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            net.minecraft.resources.Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.addTransientModifier(new AttributeModifier(
                id, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeVeinBonus(ServerPlayer player,
            net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            net.minecraft.resources.Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.removeModifier(id);
    }

    private void addTeamScore(Faction faction, int amount) {
        if (phase != Phase.PLAYING) return;
        if (faction == Faction.XUANXIAO) xuanxiaoScore += amount;
        else youmingScore += amount;
        syncAll(); // The crystal, not legacy objective score, decides victory.
    }

    /** 到时按双方灵脉积分结算；同分则平局。 */
    public void checkTimeout(long gameTime) {
        if (LaneSystem.hasCrystals()) return;
        if (phase != Phase.PLAYING || matchStartTime < 0) {
            return;
        }
        if (gameTime - matchStartTime >= GameRules.matchDurationTicks()) {
            if (xuanxiaoScore == youmingScore) {
                phase = Phase.ENDED;
                matchEndTime = gameTime;
                broadcast(MatchMessages.Tone.NEUTRAL,
                        Component.translatable("message.myriad.match_draw", xuanxiaoScore));
                syncAll();
            } else {
                endMatch(xuanxiaoScore > youmingScore ? Faction.XUANXIAO : Faction.YOUMING);
            }
        }
    }

    /** 对局剩余刻数（非对局期返回 -1） */
    public long getRemainingTicks(long gameTime) {
        if (phase != Phase.PLAYING || matchStartTime < 0) {
            return -1;
        }
        return Math.max(0, GameRules.matchDurationTicks() - (gameTime - matchStartTime));
    }

    public void endMatch(Faction winner) {
        if (phase != Phase.PLAYING) return;
        this.winner = winner;
        this.phase = Phase.ENDED;
        this.matchEndTime = server == null ? matchStartTime : server.overworld().getGameTime();
        clearSpiritVeinBonuses();
        broadcast(MatchMessages.Tone.SUCCESS,
                Component.translatable("message.myriad.match_end", winner.displayName()));
        long seconds = Math.max(0, matchEndTime - matchStartTime) / 20;
        broadcast(MatchMessages.Tone.OBJECTIVE,
                Component.translatable("message.myriad.results_header", seconds / 60, seconds % 60));
        java.util.List<com.myriad.network.NetworkSetup.MatchResultEntry> resultEntries =
                new java.util.ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = PlayerDataProvider.get(player);
            Faction faction = factionOf(player);
            resultEntries.add(new com.myriad.network.NetworkSetup.MatchResultEntry(
                    player.getName().getString(), faction == null ? "" : faction.getName(),
                    data.getCharacterId() == null ? "" : data.getCharacterId(), data.getLevel(),
                    killsOf(player), deathsOf(player), data.getMatchGoldEarned(),
                    (int)Math.round(heroDamage.getOrDefault(player.getUUID(), 0.0)),
                    (int)Math.round(damageTaken.getOrDefault(player.getUUID(), 0.0)),
                    (int)Math.round(healingReceived.getOrDefault(player.getUUID(), 0.0))));
        }
        var resultsPayload = new com.myriad.network.NetworkSetup.MatchResultsPayload(
                winner.getName(), (int)seconds, resultEntries);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setCamera(player);
            player.setGameMode(GameType.ADVENTURE);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, resultsPayload);
            player.sendSystemMessage(Component.translatable("message.myriad.results_click")
                    .withStyle(net.minecraft.network.chat.Style.EMPTY
                            .withColor(net.minecraft.ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/myriad results"))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                    Component.translatable("message.myriad.results_click_hover")))));
        }
        try {
            java.nio.file.Files.writeString(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("myriad_last_match.json"), MatchProgress.JSON.toJson(snapshot()));
        } catch (Exception ex) { com.myriad.Myriad.LOGGER.warn("Could not save last match results", ex); }
        returnToLobbyPending = true; // defer entity cleanup until the death event finishes
        syncAll();
    }

    private static final class SpiritVein {
        private final BlockPos pos;
        private final boolean large;
        private final SpiritAffinity affinity;
        private Faction owner;
        private int progress;
        private int contestedTicks;
        private int xuanxiaoPlayers;
        private int youmingPlayers;

        private SpiritVein(BlockPos pos, boolean large, SpiritAffinity affinity) {
            this.pos = pos;
            this.large = large;
            this.affinity = affinity;
        }

        private int captureTicks() { return large ? LARGE_VEIN_CAPTURE_TICKS : SMALL_VEIN_CAPTURE_TICKS; }
        private int radius() { return large ? LARGE_VEIN_RADIUS : SMALL_VEIN_RADIUS; }
        private Component displayName() {
            return large ? Component.translatable("message.myriad.vein_large")
                    : Component.translatable("message.myriad.vein_" + affinity.id());
        }

        private void reset() {
            owner = null;
            progress = 0;
            contestedTicks = 0;
            xuanxiaoPlayers = 0;
            youmingPlayers = 0;
        }
    }

    /** 重置并重新开局 */
    public void restart(MinecraftServer server) {
        resetToLobby(server, true);
        ModState.setEnabled(true);
        TrainingMode.setEnabled(false);
        startMatch(server);
    }

    public void resetToLobby(MinecraftServer server, boolean teleport) {
        this.server = server;
        phase = Phase.WAITING;
        winner = null;
        returnToLobbyPending = false;
        resetMatchObjectives();
        PlayerDataProvider.clearModGeneratedState(server);
        LaneSystem.tick(server.overworld());
        ModEntityTracker.clearGenerated(server);
        BaseRules.clear();
        pickOrder.clear(); charPicks.clear(); heroDamage.clear(); damageTaken.clear(); healingReceived.clear();
        pickIndex = 0; pickDeadlineTick = -1; pickCountdownEndTick = -1;
        matchStartTime = -1; matchEndTime = -1; xuanxiaoScore = 0; youmingScore = 0;
        pendingRoundReset.addAll(joinOrder);
        factions.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            pendingRoundReset.remove(player.getUUID());
            player.setCamera(player);
            PlayerDataProvider.resetForHostSession(player);
            player.closeContainer();
            player.setGameMode(GameType.ADVENTURE);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.myriad.network.NetworkSetup.DashPayload(0, 0, 0, 0));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.myriad.network.NetworkSetup.PickStatePayload(
                            1, "", "", "", -1, -1, java.util.Map.of(), java.util.Map.of()));
            if (teleport) LobbySystem.teleportToLobby(player);
        }
        VisionSystem.tick(server.overworld());
        syncAll();
        com.myriad.network.MatchTabPayload.sync(server);
        MatchProgress.save(server);
    }

    private void broadcast(Component message) {
        broadcast(MatchMessages.Tone.INFO, message);
    }

    private void broadcast(MatchMessages.Tone tone, Component message) {
        MatchMessages.broadcast(server, tone, message);
    }

    private void syncAll() {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerDataProvider.syncToClient(player);
        }
    }

    /** 开局广播：阵容信息（换行多行） */
    private void broadcastLineups(List<ServerPlayer> players) {
        StringBuilder xuanxiao = new StringBuilder();
        StringBuilder youming = new StringBuilder();
        for (ServerPlayer p : players) {
            Faction f = factions.get(p.getUUID());
            if (f == Faction.XUANXIAO) {
                xuanxiao.append("\n- ").append(p.getName().getString());
            } else {
                youming.append("\n- ").append(p.getName().getString());
            }
        }
        Component body = Component.translatable("message.myriad.match_start")
                .copy()
                .append(Component.literal("\n\n")).append(Faction.XUANXIAO.displayName()).append(Component.literal("："))
                .append(Component.literal(xuanxiao.toString()))
                .append(Component.literal("\n\n")).append(Faction.YOUMING.displayName()).append(Component.literal("："))
                .append(Component.literal(youming.toString()));
        broadcast(body);
    }

    /** 开局后提示客户端显示「请选择角色」 */
    private void broadcastMatchStarted() {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new com.myriad.network.NetworkSetup.MatchStartedPayload());
        }
    }
}
