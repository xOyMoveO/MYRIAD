package com.myriad.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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

import com.myriad.character.CharacterType;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 单局对战管理器：阵营分配、角色选择、地图目标与胜负判定。
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
        /** 含轮流选角与全员锁定后的十秒泉水准备。 */
        PICKING,
        /** 对局进行中 */
        PLAYING,
        /** 对局已结束 */
        ENDED
    }

    private final Map<UUID, Faction> factions = new HashMap<>();
    private final java.util.Set<UUID> pendingRoundReset = new java.util.HashSet<>();
    /** Participants who were offline when preparation moved the match to its bases. */
    private final java.util.Set<UUID> pendingPreparationBase = new java.util.HashSet<>();
    private Phase phase = Phase.WAITING;
    private Faction winner = null;
    @com.google.gson.annotations.SerializedName(value="guardianScore",alternate={"xuanxiaoScore"})
    private int guardianScore = 0;
    @com.google.gson.annotations.SerializedName(value="destructionScore",alternate={"youmingScore"})
    private int destructionScore = 0;
    private long matchStartTime = -1;
    private MatchTempo tempo = MatchTempo.STANDARD;

    static MatchTempo combatTempo() {
        return instance != null && instance.phase == Phase.PLAYING && instance.tempo != null
                ? instance.tempo : MatchTempo.STANDARD;
    }
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
    /** 当局 K/D/A，按锁定席位保存。 */
    private final Map<UUID, Integer> matchKills = new HashMap<>();
    private final Map<UUID, Integer> matchDeaths = new HashMap<>();
    private MatchAssists assists = new MatchAssists();
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
    /** 已锁定的蛇形选角顺序，掉线时保留席位。 */
    private final List<UUID> pickOrder = new ArrayList<>();
    /** Admission order is retained even when draft order is shuffled or players disconnect. */
    private final List<UUID> queueRoster = new ArrayList<>();
    private final Map<UUID, String> queueNames = new HashMap<>();
    private final Map<UUID, String> preselected = new HashMap<>();
    private final Set<UUID> invalidatedPreselection = new HashSet<>();
    private UUID queueRound;
    private EarlyRemake earlyRemake = new EarlyRemake();
    private record OfflineStats(int level, int gold, long purchases) {}
    private record EconomySample(int minute, int players, double meanLevel, double meanEarnedGold, double meanPurchases) {}
    private final List<EconomySample> economySamples = new ArrayList<>();
    private final List<Long> crabKillTicks = new ArrayList<>();
    private long firstTowerTick = -1;
    private long crystalTick = -1;

    private void sampleEconomy(long now) {
        if (tempo != MatchTempo.CHERRY_V1) return;
        long elapsed = now - matchStartTime;
        if (elapsed != 6000 && elapsed != 12000 && elapsed != 18000) return;
        int minute = (int) (elapsed / 1200);
        if (economySamples.stream().anyMatch(sample -> sample.minute() == minute)) return;
        for (ServerPlayer player : matchPlayers()) rememberPlayerStats(player);
        var roster = isQueuedMatch() ? queueRoster : factions.keySet();
        if (roster.isEmpty()) return;
        double levels = 0, gold = 0, purchases = 0;
        for (var id : roster) {
            OfflineStats stats = offlineStats.getOrDefault(id, new OfflineStats(1, 500, 0));
            levels += stats.level();
            gold += Math.max(500, stats.gold());
            purchases += stats.purchases();
        }
        int count = roster.size();
        var sample = new EconomySample(minute, count, levels/count, gold/count, purchases/count);
        economySamples.add(sample);
        com.myriad.Myriad.LOGGER.info("[cherry-tempo] round={} {}", queueRound, sample);
    }

    void recordTowerFall(long time) {
        if (combatTempo() == MatchTempo.CHERRY_V1 && firstTowerTick < 0) firstTowerTick = time - matchStartTime;
    }

    void recordCrabKill(long time) {
        if (combatTempo() == MatchTempo.CHERRY_V1) crabKillTicks.add(time - matchStartTime);
    }

    void crystalDestroyed(Faction winningSide, long time) {
        if (combatTempo() == MatchTempo.CHERRY_V1) crystalTick = time - matchStartTime;
        endMatch(winningSide);
    }

    private void logTempoResult(String outcome, long time) {
        if (tempo == MatchTempo.CHERRY_V1) com.myriad.Myriad.LOGGER.info(
                "[cherry-tempo] round={} players={} outcome={} ticks={} firstTower={} crabKills={} crystal={} samples={}",
                queueRound, factions.size(), outcome, time - matchStartTime, firstTowerTick, crabKillTicks, crystalTick, economySamples);
    }
    private final Map<UUID, OfflineStats> offlineStats = new HashMap<>();
    private long pickToken;
    private transient boolean checkDraftPool = true;
    private int pickIndex = 0;
    /** 当前选角名额的服务端绝对截止刻。 */
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
    private com.myriad.network.NetworkSetup.MatchResultsPayload pendingResults;
    public void recordDamage(ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target, float amount) {
        if (phase == Phase.PLAYING && target instanceof ServerPlayer victim && amount > 0
                && countsForMatch(attacker) && countsForMatch(victim))
            heroDamage.merge(attacker.getUUID(), (double) amount, Double::sum);
    }

    public void recordDamageTaken(ServerPlayer target, float amount) {
        if (phase == Phase.PLAYING && amount > 0 && countsForMatch(target))
            damageTaken.merge(target.getUUID(), (double) amount, Double::sum);
    }

    public void recordHealingReceived(ServerPlayer target, float amount) {
        if (phase == Phase.PLAYING && amount > 0 && countsForMatch(target))
            healingReceived.merge(target.getUUID(), (double) amount, Double::sum);
    }

    public static com.google.gson.JsonElement snapshot() {
        if (instance != null && instance.server != null && instance.isQueuedMatch()) {
            for (ServerPlayer player : instance.matchPlayers()) instance.rememberPlayerStats(player);
        }
        return MatchProgress.JSON.toJsonTree(instance);
    }
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

    public int getGuardianScore() {
        return guardianScore;
    }

    public int getDestructionScore() {
        return destructionScore;
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
        if (TrainingMode.isEnabled(player)) {
            PlayerData data = PlayerDataProvider.get(player);
            if (data != null) return data.getFaction();
        }
        return factions.get(player.getUUID());
    }

    public boolean isParticipant(UUID id) {
        return phase != Phase.WAITING && factions.containsKey(id);
    }

    public boolean isQueuedMatch() { return !queueRoster.isEmpty(); }
    public long queuePickToken() { return pickToken; }
    public String queuePreselected(UUID id) { return preselected.getOrDefault(id, ""); }
    public UUID queueCurrentPicker() { return currentPicker(); }
    public List<UUID> queuePickOrder() { return List.copyOf(pickOrder); }
    public String queuePickedRole(UUID id) {
        for (var pick : charPicks.entrySet()) if (pick.getValue().equals(id)) return pick.getKey();
        return "";
    }
    public String queueName(UUID id) { return queueNames.getOrDefault(id, ""); }
    public Faction queueFaction(UUID id) { return factions.get(id); }
    public long queueDeadline() { return currentPicker() == null ? -1 : pickDeadlineTick; }
    public long queuePreparationDeadline() { return pickCountdownEndTick; }

    EarlyRemake.View remakeView() {
        return phase == Phase.PLAYING && isQueuedMatch() ? earlyRemake.view() : EarlyRemake.View.NONE;
    }

    /** Rebuild the online snapshot on the server thread, including immediately before accepting a request. */
    private boolean refreshRemake(long now) {
        if (!isQueuedMatch() || phase != Phase.PLAYING) return false;
        var previous = earlyRemake.view();
        var current = earlyRemake.evaluate(queueRound, now, matchStartTime, factions,
                id -> server.getPlayerList().getPlayer(id) != null,
                !matchKills.isEmpty() || LaneSystem.hasDestroyedTower(matchStartTime));
        if (current.automatic()) {
            finishRemake();
            return true;
        }
        if (!previous.equals(current)) {
            QueueLobby.remakeChanged();
            if (previous.active() && (!current.active() || !previous.offerId().isEmpty()
                    && !previous.offerId().equals(current.offerId())))
                broadcast(Component.translatable("lobby.myriad.remake.cancelled"));
        }
        return false;
    }

    void requestRemake(ServerPlayer player, String offerId, long queueToken) {
        if (phase != Phase.PLAYING || !isQueuedMatch() || !isParticipant(player.getUUID())
                || server.getPlayerList().getPlayer(player.getUUID()) != player) return;
        if (refreshRemake(server.overworld().getGameTime())) return;
        var view = earlyRemake.view();
        if (!offerId.isEmpty() && offerId.equals(view.offerId()) && queueFaction(player.getUUID()) == view.side()
                && QueueLobby.snapshot(player).queueToken() == queueToken) finishRemake();
    }

    private void finishRemake() {
        logTempoResult("remake", server.overworld().getGameTime());
        List<ServerPlayer> players = matchPlayers();
        broadcast(Component.translatable("lobby.myriad.remake.passed"));
        resetToLobby(server, true);
        for (var player : players) {
            QueueLobby.sync(player);
            com.myriad.network.Packets.sendToPlayer(player, new com.myriad.network.RemadePayload());
        }
    }

    private boolean countsForMatch(ServerPlayer player) {
        return isQueuedMatch() ? factions.containsKey(player.getUUID()) : !TrainingGround.isTraining(player);
    }

    private List<ServerPlayer> matchPlayers() {
        return server.getPlayerList().getPlayers().stream().filter(this::countsForMatch).toList();
    }

    public MatchAssists assists() { return assists; }
    public int killsOf(UUID id) { return matchKills.getOrDefault(id, 0); }
    public int killsOf(ServerPlayer player) { return killsOf(player.getUUID()); }
    public int deathsOf(UUID id) { return matchDeaths.getOrDefault(id, 0); }
    public int deathsOf(ServerPlayer player) { return deathsOf(player.getUUID()); }

    /** 记录服务器引用，供广播使用 */
    public void setServer(MinecraftServer server) {
        this.server = server;
        loadSpiritVeinSpawns();
    }

    public void reloadArenaObjectives() {
        resetMatchObjectives();
        loadSpiritVeinSpawns();
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (phase == Phase.PLAYING && isQueuedMatch() && isParticipant(player.getUUID()))
            earlyRemake.reconnect(player.getUUID());
        if (pendingRoundReset.remove(player.getUUID())) {
            if (ModState.isEnabled()) {
                PlayerDataProvider.resetForHostSession(player);
                player.setGameMode(GameType.ADVENTURE);
                LobbySystem.teleportToLobby(player);
            } else {
                // VanillaSession has already restored the vanilla inventory and game mode.
                PlayerDataProvider.set(player, new PlayerData());
            }
        }
        if (ModState.isEnabled() && phase == Phase.WAITING) GuideBook.give(player);
        Faction faction = factions.get(player.getUUID());
        if (TrainingGround.isTraining(player) || isQueuedMatch() && faction == null) return;
        if (faction == null && phase == Phase.PLAYING) {
            // 对局中途加入：补人数较少的阵营。
            long guardianCount = factions.values().stream().filter(f -> f == Faction.GUARDIAN).count();
            long destructionCount = factions.values().stream().filter(f -> f == Faction.DESTRUCTION).count();
            if (Math.min(guardianCount, destructionCount) >= MatchArena.active().teamSize) {
                PlayerDataProvider.get(player).setFaction(null);
                player.setGameMode(GameType.SPECTATOR);
                LobbySystem.teleportToLobby(player);
                player.sendSystemMessage(Component.translatable("message.myriad.arena_full"));
                return;
            }
            faction = guardianCount <= destructionCount ? Faction.GUARDIAN : Faction.DESTRUCTION;
            assign(player, faction);
            PlayerDataProvider.get(player).grantOpeningPurse();
            player.setGameMode(GameType.ADVENTURE);
            // 中途加入：清空背包其余物品，仅保留初始武器
            PlayerDataProvider.resetInitialInventory(player);
        }
        PlayerData data = PlayerDataProvider.get(player);
        if (data != null && faction != null) {
            if (isQueuedMatch()) {
                // A world checkpoint and the separate player save can come from adjacent autosaves.
                // Reconcile the authoritative reservation and keep the purse receipt beside the wallet.
                String locked = queuePickedRole(player.getUUID());
                String savedRole = data.getCharacterId();
                boolean wrongRole = savedRole != null && !savedRole.equals(locked);
                if (!data.belongsToQueueRound(queueRound) || wrongRole
                        || phase == Phase.PICKING && data.openingPurseClaimed()) {
                    data = resetQueuePlayer(player, faction);
                    pendingPreparationBase.add(player.getUUID());
                }
                data.setFaction(faction);
                if (!locked.isEmpty() && !locked.equals(data.getCharacterId())) applyPick(player, CharacterType.byId(locked));
                if (phase == Phase.PLAYING) data.grantOpeningPurse();
            }
            data.setFaction(faction);
            com.myriad.player.PlayerNames.refresh(player);
            if (invalidatedPreselection.remove(player.getUUID())) notifyPreselectionTaken(player);
            if ((phase == Phase.PLAYING || phase == Phase.PICKING && pickCountdownEndTick >= 0)
                    && pendingPreparationBase.remove(player.getUUID())) {
                LaneSystem.teleportPlayersToBases(server.overworld(), List.of(player));
                player.setGameMode(GameType.ADVENTURE);
                // An offline participant also missed finishPicking's starting purse.
                if (phase == Phase.PLAYING && !isQueuedMatch()) data.grantOpeningPurse();
            }
        }
        // Connection-local pick state is empty after reconnect; resend without restarting the countdown.
        if (phase == Phase.PICKING) broadcastPickState();
    }

    private void rememberPlayerStats(ServerPlayer player) {
        PlayerData data = PlayerDataProvider.get(player);
        offlineStats.put(player.getUUID(), new OfflineStats(data.getLevel(), data.getMatchGoldEarned(),
                com.myriad.character.rework.FirstGroupGrowth.ATTRIBUTES.stream().mapToLong(data::firstGroupAttribute).sum()));
    }

    public void onPlayerQuit(ServerPlayer player) {
        if (isQueuedMatch() && isParticipant(player.getUUID())) {
            rememberPlayerStats(player);
            if (phase == Phase.PLAYING)
                earlyRemake.disconnect(player.getUUID(), server.overworld().getGameTime(), matchStartTime);
        }
        // Keep the player's faction, character reservation and K/D for reconnecting.
        if (phase == Phase.WAITING) factions.remove(player.getUUID());
    }

    /** Lock exactly the admitted players. All validation precedes leaving training or resetting a player. */
    public boolean startQueuedMatch(MinecraftServer server, List<ServerPlayer> players) {
        MatchArena.Mode mode = MatchArena.active();
        int count = players.size();
        if (phase != Phase.WAITING || !ModState.isEnabled()
                || count < 2 || count % 2 != 0 || count > mode.teamSize * 2
                || mode == MatchArena.Mode.RIFT && count != mode.teamSize * 2) return false;
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayer player : players) {
            if (player == null || server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !ids.add(player.getUUID())) return false;
        }
        if (CharacterType.playableValues().length < count) {
            for (ServerPlayer player : players) player.sendSystemMessage(Component.translatable("message.myriad.queue_role_pool"));
            return false;
        }

        if (!MatchArena.refreshReadiness(server)) return false;
        this.server = server;
        queueRound = UUID.randomUUID();
        if (mode == MatchArena.Mode.DUEL) MonsterManager.resetArenaRuntime(server);
        else MonsterManager.reloadArena(server);
        JiarenManager.clearGenerated(server.overworld());
        for (ServerPlayer player : players) {
            TrainingGround.leaveForMatch(player);
            queueRoster.add(player.getUUID());
            queueNames.put(player.getUUID(), player.getName().getString());
        }
        factions.clear();
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        for (int i = 0; i < count; i++) assign(shuffled.get(i), i < count / 2 ? Faction.GUARDIAN : Faction.DESTRUCTION);
        guardianScore = 0;
        destructionScore = 0;
        winner = null;
        resetMatchObjectives();
        beginPicking(server);
        return true;
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
        com.myriad.player.PlayerNames.refresh(player);
    }

    private void beginPicking(MinecraftServer server) {
        this.server = server;
        TrainingMode.setEnabled(false);
        for (ServerPlayer player : matchPlayers()) resetQueuePlayer(player, factions.get(player.getUUID()));
        pickOrder.clear();
        pendingPreparationBase.clear();
        boolean guardianFirst = MatchArena.active() == MatchArena.Mode.DUEL;
        List<UUID> first = new ArrayList<>();
        List<UUID> second = new ArrayList<>();
        Faction openingSide = guardianFirst ? Faction.GUARDIAN : Faction.DESTRUCTION;
        // All seats are online at admission. For 5v5, retain their connection order within
        // each faction. The resulting pickOrder then owns the seats across disconnects and saves.
        List<UUID> teamOrder = guardianFirst ? queueRoster : matchPlayers().stream().map(ServerPlayer::getUUID).toList();
        for (UUID id : teamOrder) {
            Faction faction = factions.get(id);
            if (faction == openingSide) first.add(id);
            else if (faction != null) second.add(id);
        }
        if (guardianFirst) {
            Collections.shuffle(first);
            Collections.shuffle(second);
        }
        pickToken = queueRound.getMostSignificantBits() & Long.MAX_VALUE;
        checkDraftPool = true;
        if (!first.isEmpty()) pickOrder.add(first.removeFirst());
        while (!second.isEmpty() || !first.isEmpty()) {
            for (int i = 0; i < 2 && !second.isEmpty(); i++) pickOrder.add(second.removeFirst());
            for (int i = 0; i < 2 && !first.isEmpty(); i++) pickOrder.add(first.removeFirst());
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
        pickDeadlineTick = server.overworld().getGameTime() + pickTicks();
        broadcast(Component.translatable("message.myriad.pick_start"));
        broadcastPickState();
    }

    private PlayerData resetQueuePlayer(ServerPlayer player, Faction faction) {
        PlayerDataProvider.clearMatchPlayerState(player);
        PlayerDataProvider.resetForHostSession(player);
        PlayerData data = PlayerDataProvider.get(player);
        if (isQueuedMatch()) data.beginQueueRound(queueRound);
        data.setFaction(faction);
        return data;
    }

    private int pickTicks() {
        return isQueuedMatch() && MatchArena.active() == MatchArena.Mode.DUEL ? 20 * 20 : PICK_TICKS;
    }

    /** 当前轮到谁选择（null = 无） */
    private UUID currentPicker() {
        return pickIndex >= 0 && pickIndex < pickOrder.size() ? pickOrder.get(pickIndex) : null;
    }

    /** 对称阵营选人：双方共享全部角色池，仍保持角色唯一。 */
    public boolean onPick(ServerPlayer player, com.myriad.character.CharacterType type) {
        if (phase != Phase.PICKING || type == null || !player.getUUID().equals(currentPicker())) {
            return false;
        }
        if (factions.get(player.getUUID()) == null) {
            return false;
        }
        if (charPicks.containsKey(type.getId())) {
            return false;
        }
        completePick(player.getUUID(), player, type, false);
        return true;
    }

    public boolean queuePreselect(ServerPlayer player, String role) {
        UUID id = player.getUUID();
        CharacterType type = CharacterType.byId(role);
        if (!isQueuedMatch() || phase != Phase.PICKING || currentPicker() == null || !isParticipant(id)
                || id.equals(currentPicker()) && server.overworld().getGameTime() >= pickDeadlineTick
                || charPicks.containsValue(id) || type == null || charPicks.containsKey(type.getId())) return false;
        preselected.put(id, type.getId());
        invalidatedPreselection.remove(id);
        return true;
    }

    public boolean queueLock(ServerPlayer player, String role) {
        return isQueuedMatch() && server.overworld().getGameTime() < pickDeadlineTick
                && onPick(player, CharacterType.byId(role));
    }

    private static void applyPick(ServerPlayer player, CharacterType type) {
        PlayerData data = PlayerDataProvider.get(player);
        data.setCharacterId(type.getId());
        data.setSkillPoints(Math.max(1, data.getSkillPoints()));
        PlayerDataProvider.applyChosenCharacter(player, data);
        PlayerDataProvider.syncToClient(player);
    }

    private static void notifyPreselectionTaken(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.myriad.queue_preselection_taken"));
    }

    private void completePick(UUID id, ServerPlayer player, CharacterType type, boolean automatic) {
        String role = type.getId();
        charPicks.put(role, id);
        preselected.remove(id);
        invalidatedPreselection.remove(id);
        preselected.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(role)) return false;
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null) invalidatedPreselection.add(entry.getKey());
            else notifyPreselectionTaken(owner);
            return true;
        });
        if (player != null) applyPick(player, type);
        broadcast(Component.translatable(automatic ? "message.myriad.pick_auto" : "message.myriad.pick_chosen",
                player == null ? queueName(id) : player.getName().getString(), Component.translatable(type.nameKey())));
        advancePick(server);
        broadcastPickState();
    }

    private void advancePick(MinecraftServer server) {
        pickIndex++;
        if (isQueuedMatch()) {
            pickToken++;
            checkDraftPool = true;
        }
        if (pickIndex >= pickOrder.size()) {
            // 全部选择完成即在泉水准备；复用原有十秒，不再次延长或开战重传送。
            preparePlayers(server);
            pickCountdownEndTick = server.overworld().getGameTime() + PICK_COUNTDOWN_TICKS;
            broadcast(Component.translatable("message.myriad.pick_all_done", PICK_COUNTDOWN_TICKS / 20));
        } else {
            pickDeadlineTick = server.overworld().getGameTime() + pickTicks();
        }
    }

    /** Countdown is still PICKING so ordinary combat and economy remain disabled. */
    public boolean preparationFor(ServerPlayer player) {
        return preservesPreparedState(player) && server != null
                && server.overworld().getGameTime() < pickCountdownEndTick;
    }
    /** Player ticks may precede the match tick at the deadline; retain accepted state until PLAYING is set. */
    public boolean preservesPreparedState(ServerPlayer player) {
        return phase == Phase.PICKING && pickCountdownEndTick >= 0
                && "rivet".equals(PlayerDataProvider.get(player).getCharacterId())
                && factions.containsKey(player.getUUID());
    }
    private void preparePlayers(MinecraftServer server) {
        List<ServerPlayer> players = matchPlayers();
        pendingPreparationBase.addAll(factions.keySet());
        LaneSystem.teleportPlayersToBases(server.overworld(),players);
        for(ServerPlayer player:players){
            pendingPreparationBase.remove(player.getUUID());
            if(player.gameMode.getGameModeForPlayer()!=GameType.ADVENTURE)player.setGameMode(GameType.ADVENTURE);
        }
    }

    /** 选人阶段每 tick：全部选完后的倒计时结束即开局；否则处理超时自动选 / 掉线跳过 / 无可用角色跳过 */
    public void tick(MinecraftServer server, long gameTime) {
        if (phase == Phase.ENDED) {
            resetToLobby(server, true);
            return;
        }
        if (phase == Phase.PLAYING) {
            if (refreshRemake(gameTime)) return;
            enforcePlayingModes(server);
            tickSpiritVein(server, gameTime);
            tickPassiveGold(server, gameTime);
            sampleEconomy(gameTime);
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
        if (isQueuedMatch()) {
            if (!checkDraftPool && gameTime < pickDeadlineTick) return;
            List<CharacterType> available = availableForFaction(factions.get(cur));
            checkDraftPool = false;
            if (available.isEmpty()) {
                List<UUID> roster = List.copyOf(queueRoster);
                broadcast(Component.translatable("message.myriad.queue_role_pool"));
                resetToLobby(server, true);
                QueueLobby.restoreRoster(server, roster);
            } else if (gameTime >= pickDeadlineTick) {
                CharacterType selected = CharacterType.byId(preselected.get(cur));
                if (!available.contains(selected)) selected = available.get(server.overworld().getRandom().nextInt(available.size()));
                completePick(cur, cp, selected, true);
            }
            return;
        }
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
        for (ServerPlayer player : matchPlayers()) {
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
        var awards = new java.util.LinkedHashMap<ServerPlayer, Integer>();
        for (ServerPlayer player : matchPlayers()) {
            if (factionOf(player) == null) continue;
            if (PlayerDataProvider.get(player) == null) continue;
            awards.put(player, GlobalBalance.goldIncome(MatchBalance.farmReward(player, 10, false)));
        }
        for (var award : awards.entrySet()) {
            ServerPlayer player = award.getKey();
            PlayerDataProvider.get(player).addGold(award.getValue());
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
        completePick(player.getUUID(), player, avail.getFirst(), true);
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
        tempo = MatchArena.selectedTempo();
        economySamples.clear();
        crabKillTicks.clear();
        firstTowerTick = -1;
        crystalTick = -1;
        matchStartTime = server.overworld().getGameTime();
        matchEndTime = -1;
        if (!rollSpiritVeins()) {
            broadcast(Component.translatable("message.myriad.vein_config_incomplete",
                    attributeVeinSpawns.size(), largeVeinSpawns.size()));
        } else if (!spiritVeins.isEmpty()) {
            spiritVeinAvailableAt = matchStartTime + 60L * 20L;
            broadcast(Component.translatable("message.myriad.vein_will_open", 60));
        }
        List<ServerPlayer> players = matchPlayers();
        // 正常选角已在准备期开始时传送，开战保留这十秒内的站位和锚点。
        // 无选角顺序的极端开局仍沿用直接就位。
        if (pickOrder.isEmpty()) LaneSystem.teleportPlayersToBases(server.overworld(), players);
        for (ServerPlayer player : players) PlayerDataProvider.get(player).grantOpeningPurse();
        // 地图编辑和训练时玩家可能处于创造/旁观模式；正式对局统一使用冒险模式，
        // 既允许战斗与物品交互，又避免玩家破坏战场方块。
        for (ServerPlayer player : players) {
            if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                player.setGameMode(GameType.ADVENTURE);
            }
        }
        broadcastLineups(players);
        broadcastMatchStarted();
        refreshRemake(matchStartTime);
        // 通知客户端关闭选人界面
        for (ServerPlayer p : players) {
            com.myriad.network.Packets.sendToPlayer(p,
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
        if (isQueuedMatch()) return;
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
        for (ServerPlayer p : matchPlayers()) {
            Faction myF = factions.get(p.getUUID());
            com.myriad.network.Packets.sendToPlayer(p,
                    new com.myriad.network.NetworkSetup.PickStatePayload(
                            pickCountdownEndTick >= 0 ? 1 : 0,
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
        if (server == null) return false;
        var next = ArenaConfigFile.readForEdit(spiritVeinConfigPath(), GameManager::decodeVeins,
                () -> new VeinLayout(spiritVeinsEnabled, new HashMap<>(attributeVeinSpawns), new HashMap<>(largeVeinSpawns))).orElse(null);
        if (next == null) return false;
        Map<Integer, BlockPos> targets = large ? next.large : next.attributes;
        if (pos == null) targets.remove(slot);
        else targets.put(slot, pos.immutable());
        if (!ArenaConfigFile.save(spiritVeinConfigPath(), next.json())) return false;
        activateVeins(next);
        return true;
    }

    /** 地图编辑器只读快照；返回副本，避免客户端编辑链路绕过统一保存入口。 */
    public Map<Integer, BlockPos> spiritVeinSpawnSnapshot(boolean large) {
        return Map.copyOf(large ? largeVeinSpawns : attributeVeinSpawns);
    }

    public boolean spiritVeinsEnabled() { return spiritVeinsEnabled; }

    /** 固定刷新点属于地图配置，写入世界存档并跨服务器重启保留。 */
    record VeinLayout(boolean enabled, Map<Integer, BlockPos> attributes, Map<Integer, BlockPos> large) {
        com.google.gson.JsonObject json() {
            var root = new com.google.gson.JsonObject();
            root.addProperty("enabled", enabled);
            root.add("attribute", veinPositionsToJson(attributes));
            root.add("large", veinPositionsToJson(large));
            return root;
        }
    }

    static VeinLayout decodeVeins(com.google.gson.JsonObject root) {
        var layout = new VeinLayout(!root.has("enabled") || root.get("enabled").getAsBoolean(),
                new HashMap<>(), new HashMap<>());
        var attributes = ArenaConfigFile.object(root, root.has("attribute") ? "attribute" : "small");
        veinPositionsFromJson(attributes, layout.attributes, ATTRIBUTE_VEIN_COUNT);
        veinPositionsFromJson(ArenaConfigFile.object(root, "large"), layout.large, LARGE_VEIN_CANDIDATE_COUNT);
        return layout;
    }

    void activateVeins(VeinLayout next) {
        spiritVeinsEnabled = next.enabled;
        attributeVeinSpawns.clear();
        attributeVeinSpawns.putAll(next.attributes);
        largeVeinSpawns.clear();
        largeVeinSpawns.putAll(next.large);
        if (!spiritVeinsEnabled) {
            spiritVeins.clear();
            spiritVeinAvailableAt = -1;
            clearSpiritVeinBonuses();
        }
    }

    private void loadSpiritVeinSpawns() {
        ArenaConfigFile.readOptional(spiritVeinConfigPath(), GameManager::decodeVeins).ifPresent(this::activateVeins);
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
        if (phase != Phase.PLAYING || killer == victim || !countsForMatch(killer) || !countsForMatch(victim)
                || factionOf(killer) == null || factionOf(killer) == factionOf(victim)) {
            return;
        }
        int victimStreak = killStreaks.getOrDefault(victim.getUUID(), 0);
        int priorDeaths=com.myriad.player.EntityState.data(victim).getIntOr("myriad_deaths_without_kill",0);
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
        com.myriad.player.EntityState.data(killer).putInt("myriad_deaths_without_kill",0);
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
            for (ServerPlayer viewer : matchPlayers()) {
                com.myriad.network.Packets.sendToPlayer(viewer, announcement);
            }
        }
    }

    /** 记录一次实际发生的英雄死亡；击杀奖励与“不扣命”效果不影响 K/D 统计。 */
    public void recordHeroDeath(ServerPlayer victim, net.minecraft.world.entity.Entity killerEntity) {
        if (phase != Phase.PLAYING || !countsForMatch(victim) || factionOf(victim) == null) return;
        if (isQueuedMatch()) {
            UUID killerId = killerEntity instanceof ServerPlayer hero && countsForMatch(hero)
                    && factionOf(hero) != null && factionOf(hero) != factionOf(victim) ? hero.getUUID() : null;
            assists.death(victim.getUUID(), killerId, victim.getMaxHealth(), victim.level().getGameTime(),
                    id -> factions.get(id) != null && factions.get(id) == factions.get(killerId));
        }
        matchDeaths.merge(victim.getUUID(), 1, Integer::sum);
        multiKills.remove(victim.getUUID());
        com.myriad.player.PlayerNames.refresh(victim);
        if (killerEntity instanceof ServerPlayer killer && killer != victim && countsForMatch(killer)
                && factionOf(killer) != null && factionOf(killer) != factionOf(victim)) {
            matchKills.merge(killer.getUUID(), 1, Integer::sum);
            com.myriad.player.PlayerNames.refresh(killer);
        }
    }

    void resetMatchObjectives() {
        if (server != null) {
            HeroRespawnSystem.reset(server, factions.keySet());
        }
        spiritVeins.clear();
        clearSpiritVeinBonuses();
        spiritVeinAvailableAt = -1;
        killStreaks.clear();
        multiKills.clear();
        matchKills.clear();
        matchDeaths.clear();
        assists = new MatchAssists();
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
            int guardian = 0, destruction = 0;
            for (ServerPlayer player : matchPlayers()) {
                if (!player.isAlive() || player.isSpectator() || player.level() != server.overworld()
                        || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(vein.pos))
                                > vein.radius() * vein.radius()) continue;
                Faction faction = factionOf(player);
                if (faction == Faction.GUARDIAN) guardian++;
                if (faction == Faction.DESTRUCTION) destruction++;
            }
            vein.guardianPlayers = guardian;
            vein.destructionPlayers = destruction;
            if (vein.owner == null) {
                if (guardian > destruction) {
                    int step = destruction > 0 ? (gameTime % 2 == 0 ? 1 : 0) : 1;
                    vein.progress = Math.min(vein.captureTicks(), vein.progress + step);
                } else if (destruction > guardian) {
                    int step = guardian > 0 ? (gameTime % 2 == 0 ? 1 : 0) : 1;
                    vein.progress = Math.max(-vein.captureTicks(), vein.progress - step);
                }
            } else {
                int ownerPlayers = vein.owner == Faction.GUARDIAN ? guardian : destruction;
                int enemyPlayers = vein.owner == Faction.GUARDIAN ? destruction : guardian;
                if (enemyPlayers > 0 && ownerPlayers == 0) {
                    vein.contestedTicks++;
                    if (vein.contestedTicks <= GameRules.VEIN_CONTEST_GRACE_TICKS) {
                        vein.progress = vein.owner == Faction.GUARDIAN ? vein.captureTicks() : -vein.captureTicks();
                    } else if (vein.contestedTicks == GameRules.VEIN_CONTEST_GRACE_TICKS + 1) {
                        vein.progress = 0;
                    } else {
                        vein.progress = vein.owner == Faction.GUARDIAN
                                ? Math.max(-vein.captureTicks(), vein.progress - 1)
                                : Math.min(vein.captureTicks(), vein.progress + 1);
                    }
                } else if (enemyPlayers > 0) {
                    vein.contestedTicks = Math.max(0, vein.contestedTicks - 1);
                } else {
                    vein.contestedTicks = 0;
                    vein.progress = vein.owner == Faction.GUARDIAN ? vein.captureTicks() : -vein.captureTicks();
                }
            }
            if (gameTime % 10 == 0) spawnSpiritVeinCircle(server.overworld(), vein, gameTime);
            Faction newOwner = vein.progress >= vein.captureTicks() ? Faction.GUARDIAN
                    : vein.progress <= -vein.captureTicks() ? Faction.DESTRUCTION : null;
            if (newOwner != null && newOwner != vein.owner) {
                vein.owner = newOwner;
                refreshSpiritVeinBonuses();
                Component veinName = vein.displayName();
                broadcast(MatchMessages.Tone.OBJECTIVE,
                        Component.translatable("message.myriad.vein_captured", veinName,
                        newOwner.displayName(), vein.large ? 30 : 10, vein.large ? 20 : 5));
            }
            int enemyPlayers = vein.owner == Faction.GUARDIAN ? destruction : guardian;
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
            int enemies = faction == Faction.GUARDIAN ? vein.destructionPlayers : vein.guardianPlayers;
            return enemies > 0 ? 2 : 1;
        }
        return 0;
    }

    /** 小型半径 5、大型半径 10 的双层地面圆环；颜色随占领方变化。 */
    private static void spawnSpiritVeinCircle(net.minecraft.server.level.ServerLevel level,
            SpiritVein vein, long gameTime) {
        double radius = vein.large ? LARGE_VEIN_EFFECT_RADIUS : SMALL_VEIN_EFFECT_RADIUS;
        int points = vein.large ? 64 : 40;
        int color = vein.owner == null ? 0xFFD85A : vein.owner.rgb();
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
        for (ServerPlayer player : matchPlayers()) {
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
                com.myriad.network.Packets.sendToPlayer(player,
                        new com.myriad.network.NetworkSetup.VeinStatusPayload(false, 0, false, 0, 0, 0, 1));
            } else {
                SpiritVein vein = current.getValue();
                com.myriad.network.Packets.sendToPlayer(player,
                        new com.myriad.network.NetworkSetup.VeinStatusPayload(true, current.getKey(), vein.large,
                                vein.guardianPlayers, vein.destructionPlayers, vein.progress, vein.captureTicks()));
            }
        }
    }

    /** 持续控制收益：每 5 秒发给全队，死亡成员同样获得团队目标收益。 */
    private void rewardSpiritVeinPulse(Faction faction, SpiritVein vein) {
        for (ServerPlayer player : matchPlayers()) {
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

    /** 月=法强、火=物攻、木=物防与法防；控制权转移时移除旧阵营并赋予新阵营。 */
    private void refreshSpiritVeinBonuses() {
        if (server == null) return;
        for (ServerPlayer player : matchPlayers()) {
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
        for (ServerPlayer player : matchPlayers()) clearSpiritVeinBonuses(player);
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
        if (faction == Faction.GUARDIAN) guardianScore += amount;
        else destructionScore += amount;
        syncAll(); // The crystal, not legacy objective score, decides victory.
    }

    /** 到时按双方灵脉积分结算；同分则平局。 */
    public void checkTimeout(long gameTime) {
        if (getRemainingTicks(gameTime) == 0) {
            endMatch(guardianScore == destructionScore ? null
                    : guardianScore > destructionScore ? Faction.GUARDIAN : Faction.DESTRUCTION);
        }
    }

    /** 无水晶地图的对局倒计时；没有运行中的倒计时返回 -1。 */
    public long getRemainingTicks(long gameTime) {
        if (phase != Phase.PLAYING || matchStartTime < 0 || LaneSystem.hasCrystals()) {
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
        if (winner == null) broadcast(MatchMessages.Tone.NEUTRAL,
                Component.translatable("message.myriad.match_draw", guardianScore));
        else broadcast(MatchMessages.Tone.SUCCESS,
                Component.translatable("message.myriad.match_end", winner.displayName()));
        logTempoResult(crystalTick >= 0 ? "crystal" : "other_end", matchEndTime);
        long seconds = Math.max(0, matchEndTime - matchStartTime) / 20;
        broadcast(MatchMessages.Tone.OBJECTIVE,
                Component.translatable("message.myriad.results_header", seconds / 60, seconds % 60));
        List<com.myriad.network.NetworkSetup.MatchResultEntry> resultEntries = new ArrayList<>();
        List<UUID> roster = isQueuedMatch() ? queueRoster : matchPlayers().stream().map(ServerPlayer::getUUID).toList();
        for (UUID id : roster) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            PlayerData data = player == null ? null : PlayerDataProvider.get(player);
            OfflineStats last = offlineStats.getOrDefault(id, new OfflineStats(1, 0, 0));
            Faction faction = player == null ? queueFaction(id) : factionOf(player);
            String character = data == null ? queuePickedRole(id) : data.getCharacterId();
            resultEntries.add(new com.myriad.network.NetworkSetup.MatchResultEntry(
                    player == null ? queueName(id) : player.getName().getString(),
                    faction == null ? "" : faction.getName(), character == null ? "" : character,
                    data == null ? last.level() : data.getLevel(), matchKills.getOrDefault(id, 0),
                    matchDeaths.getOrDefault(id, 0), assists.total(id), data == null ? Math.max(500, last.gold()) : data.getMatchGoldEarned(),
                    (int)Math.round(heroDamage.getOrDefault(id, 0.0)), (int)Math.round(damageTaken.getOrDefault(id, 0.0)),
                    (int)Math.round(healingReceived.getOrDefault(id, 0.0))));
        }
        pendingResults = new com.myriad.network.NetworkSetup.MatchResultsPayload(
                winner == null ? "" : winner.getName(), (int)seconds, List.copyOf(resultEntries));
        try {
            java.nio.file.Files.writeString(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("myriad_last_match.json"), MatchProgress.JSON.toJson(snapshot()));
        } catch (Exception ex) { com.myriad.Myriad.LOGGER.warn("Could not save last match results", ex); }
        // Persist ENDED before returning from the death event. The next tick handles lobby cleanup.
        MatchProgress.save(server);
        syncAll();
    }

    private static final class SpiritVein {
        private final BlockPos pos;
        private final boolean large;
        private final SpiritAffinity affinity;
        private Faction owner;
        private int progress;
        private int contestedTicks;
        @com.google.gson.annotations.SerializedName(value="guardianPlayers",alternate={"xuanxiaoPlayers"})
        private int guardianPlayers;
        @com.google.gson.annotations.SerializedName(value="destructionPlayers",alternate={"youmingPlayers"})
        private int destructionPlayers;

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
            guardianPlayers = 0;
            destructionPlayers = 0;
        }
    }

    public void resetToLobby(MinecraftServer server, boolean teleport) {
        this.server = server;
        resetToLobby(server, teleport, matchPlayers());
    }

    /** Administrative reset includes online visitors and waiting players, even during a locked round. */
    void resetAllToLobby(MinecraftServer server) {
        pendingResults = null;
        resetToLobby(server, true, List.copyOf(server.getPlayerList().getPlayers()));
    }

    private void resetToLobby(MinecraftServer server, boolean teleport, List<ServerPlayer> participants) {
        this.server = server;
        var results = pendingResults;
        Set<UUID> finishedRoster = results == null ? Set.of() : Set.copyOf(factions.keySet());
        pendingResults = null;
        resetMatchObjectives();
        phase = Phase.WAITING;
        winner = null;
        for (ServerPlayer player : participants) {
            PlayerDataProvider.clearMatchPlayerState(player);
            BaseRules.logout(player);
        }
        TacticalSupplySystem.clearFor(isQueuedMatch() ? queueRoster : factions.keySet());
        MonsterManager.reloadArena(server);
        JiarenManager.clearGenerated(server.overworld());
        ModEntityTracker.clearGenerated(server.overworld());
        LaneSystem.tick(server.overworld());
        pickOrder.clear(); pendingPreparationBase.clear(); charPicks.clear(); heroDamage.clear(); damageTaken.clear(); healingReceived.clear();
        pickIndex = 0; pickDeadlineTick = -1; pickCountdownEndTick = -1;
        matchStartTime = -1; matchEndTime = -1; guardianScore = 0; destructionScore = 0;
        pendingRoundReset.addAll(isQueuedMatch() ? queueRoster : factions.keySet());
        factions.clear();
        queueRoster.clear();
        queueNames.clear();
        preselected.clear();
        invalidatedPreselection.clear();
        queueRound = null;
        earlyRemake = new EarlyRemake();
        offlineStats.clear();
        pickToken = 0;
        for (ServerPlayer player : participants) {
            pendingRoundReset.remove(player.getUUID());
            player.setCamera(player);
            PlayerDataProvider.resetForHostSession(player);
            player.closeContainer();
            player.setGameMode(GameType.ADVENTURE);
            com.myriad.network.Packets.sendToPlayer(player,
                    new com.myriad.network.NetworkSetup.DashPayload(0, 0, 0, 0));
            com.myriad.network.Packets.sendToPlayer(player,
                    new com.myriad.network.NetworkSetup.PickStatePayload(
                            1, "", "", "", -1, -1, java.util.Map.of(), java.util.Map.of()));
            if (teleport) LobbySystem.teleportToLobby(player);
        }
        VisionSystem.tick(server.overworld());
        syncAll();
        com.myriad.network.MatchTabPayload.sync(server);
        MatchProgress.save(server);
        if (results != null && teleport && ModState.isEnabled()) {
            for (UUID id : finishedRoster) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) continue;
                // Give the buttons the post-cleanup queue token even before the next lobby tick.
                QueueLobby.sync(player);
                com.myriad.network.Packets.sendToPlayer(player, results);
                player.sendSystemMessage(Component.translatable("message.myriad.results_click")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY
                                .withColor(net.minecraft.ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/myriad results"))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                        Component.translatable("message.myriad.results_click_hover")))));
            }
        }
    }

    private void broadcast(Component message) {
        broadcast(MatchMessages.Tone.INFO, message);
    }

    private void broadcast(MatchMessages.Tone tone, Component message) {
        if (server != null) {
            for (ServerPlayer player : matchPlayers()) MatchMessages.send(player, tone, message);
        }
    }

    private void syncAll() {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : matchPlayers()) {
            PlayerDataProvider.syncToClient(player);
        }
    }

    /** 开局广播：阵容信息（换行多行） */
    private void broadcastLineups(List<ServerPlayer> players) {
        StringBuilder guardian = new StringBuilder();
        StringBuilder destruction = new StringBuilder();
        for (ServerPlayer p : players) {
            Faction f = factions.get(p.getUUID());
            if (f == Faction.GUARDIAN) {
                guardian.append("\n- ").append(p.getName().getString());
            } else {
                destruction.append("\n- ").append(p.getName().getString());
            }
        }
        Component body = Component.translatable("message.myriad.match_start")
                .copy()
                .append(Component.literal("\n\n")).append(Faction.GUARDIAN.displayName()).append(Component.literal("："))
                .append(Component.literal(guardian.toString()))
                .append(Component.literal("\n\n")).append(Faction.DESTRUCTION.displayName()).append(Component.literal("："))
                .append(Component.literal(destruction.toString()));
        broadcast(body);
    }

    /** 开局后提示客户端显示「请选择角色」 */
    private void broadcastMatchStarted() {
        if (server == null) {
            return;
        }
        for (ServerPlayer p : matchPlayers()) {
            com.myriad.network.Packets.sendToPlayer(p,
                    new com.myriad.network.NetworkSetup.MatchStartedPayload());
        }
    }
}
