package com.myriad.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.myriad.network.NetworkSetup;
import com.myriad.player.LevelSystem;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 三路兵线与防御塔（服务端权威）。路线和塔位均由管理员在地图内放点，存入世界存档。
 * 路径点始终按守护基地 -> 毁灭基地排序，毁灭兵线自动反向读取。
 */
public final class LaneSystem {
    private LaneSystem() {}

    public static final int MAX_PATH_POINTS = 12;
    public static final int TOWERS_PER_LANE = 3;
    public static final double FOUNTAIN_RADIUS = 6.0;
    public static final double FOUNTAIN_RESTORE_RATIO = 0.10;
    private static final String LANE_ENTITY_TAG = "myriad_lane_entity";
    /** 对局开始后首波兵线的延迟：1 分 10 秒。 */
    private static final long WAVE_INTERVAL = 40L * 20L;
    private static final int MELEE_PER_WAVE = 2;
    /** Each faction's wave on a lane may contain at most one siege minion. */
    private static final int MAX_SIEGE_PER_LANE_WAVE = 1;
    private static final int RANGED_PER_WAVE = 2;
    /** 防御塔从锁定到结算一次攻击的间隔：1.4 秒。 */
    private static final long TOWER_LOCK_TICKS = 28L;
    private static final double TOWER_ATTACK_RANGE = DefenseTowerEntity.ATTACK_RANGE;

    public enum Lane {
        TOP("top"), MID("mid"), BOTTOM("bottom");

        private final String id;
        Lane(String id) { this.id = id; }
        public String id() { return id; }
        public static Lane byId(String id) {
            if (id == null) return null;
            return switch (id.toLowerCase(java.util.Locale.ROOT)) {
                case "top", "上", "上路" -> TOP;
                case "mid", "middle", "中", "中路" -> MID;
                case "bottom", "bot", "下", "下路" -> BOTTOM;
                default -> null;
            };
        }
    }

    private enum Kind { TOWER, MELEE, RANGED, MAGIC, SIEGE, CRYSTAL }

    private record TowerKey(Faction faction, Lane lane, int tier) {}

    public record LayoutLine(String text) {}

    /** 供小地图读取的兵线单位只读快照；kind 与网络小地图图标编号一致。 */
    public record MinimapUnit(double x, double z, Faction faction, int kind) {}

    /** 实际兵线路线点；客户端按 lane、order 顺序连接，不再绘制固定示意线。 */
    public record MinimapPathPoint(int lane, int order, double x, double z) {}

    private static final class UnitState {
        final Kind kind;
        final Faction faction;
        final Lane lane;
        final int towerTier;
        List<BlockPos> path;
        int waypointIndex;
        float health;
        float maxHealth;
        float attackDamage;
        long lastAttackTick;
        double attackRecovery = 36;
        transient long lastRecoveryTick = Long.MIN_VALUE;
        transient UUID towerLockTarget;
        transient ServerPlayer warnedHero;
        /** 首名在塔下伤害友方英雄的敌方英雄；有效期间覆盖普通小兵仇恨。 */
        transient UUID towerHeroPriorityTarget;
        transient long towerLockStarted;
        transient long lastTowerHitFeedback = Long.MIN_VALUE;
        transient boolean towerFlashing;
        transient net.minecraft.util.Brightness towerLightBeforeHit;
        transient UUID visual;
        transient Mob entity;
        transient net.minecraft.world.entity.Display.ItemDisplay visualEntity;
        final transient NpcNavigation.Route navigation = new NpcNavigation.Route();
        long nextTargetScan;
        UUID chaseTarget;
        transient UUID defendedAgainst;
        Vec3 chaseOrigin;
        boolean returning;
        transient UUID ignoredTarget;
        transient long ignoreUntil;

        UnitState(Kind kind, Faction faction, Lane lane, int towerTier, List<BlockPos> path,
                float maxHealth) {
            this.kind = kind;
            this.faction = faction;
            this.lane = lane;
            this.towerTier = towerTier;
            this.path = path;
            this.waypointIndex = path == null || path.size() < 2 ? 0 : 1;
            this.health = maxHealth;
            this.maxHealth = maxHealth;
        }

        boolean tower() { return kind == Kind.TOWER || kind == Kind.CRYSTAL; }
        boolean ranged() { return kind == Kind.RANGED || kind == Kind.MAGIC; }
    }


    public static BlockPos laneCorner(Lane lane) { return layout.corners.get(lane); }

    public static boolean setLaneCorner(Lane lane, BlockPos pos) {
        if (!canEdit() || lane == null || lane == Lane.MID) return false;
        return editLayout(next -> {
            if (pos == null) next.corners.remove(lane);
            else next.corners.put(lane, pos.immutable());
        });
    }
    public static List<Lane> enabledLanes() { return List.copyOf(layout.enabled); }
    public static List<Integer> enabledTowerTiers() { return List.copyOf(layout.tiers); }
    private static final java.util.Set<String> DESTROYED_TOWERS = new java.util.HashSet<>();

    static boolean hasDestroyedTower(long matchStart) {
        return activeMatchStart == matchStart && !DESTROYED_TOWERS.isEmpty();
    }

    private static String towerId(Faction faction,Lane lane,int tier){return faction.getName()+"/"+lane.id+"/"+tier;}
    private static boolean siegeUnlocked(Faction faction,Lane lane){
        if (!layout.enabled.contains(lane)) return false;
        Faction enemy=faction==Faction.GUARDIAN?Faction.DESTRUCTION:Faction.GUARDIAN;
        for(int tier:layout.tiers)if(!layout.towers.containsKey(new TowerKey(enemy,lane,tier))
                || !DESTROYED_TOWERS.contains(towerId(enemy,lane,tier)))return false;
        return true;
    }
    private static float baseHealth(Kind kind){return switch(kind){case SIEGE->2240;case MELEE->665;case MAGIC->595;default->455;};}
    /** 兵线基础伤害整体下调约 20%，降低前期小兵对英雄和兵线的压制。 */
    private static float baseAttack(Kind kind){return switch(kind){case SIEGE->63;case MELEE->24;case MAGIC->39;default->18;};}
    private static void scaleMinion(Mob mob,UnitState state,long now){
        int stages=Math.max(0,(waveNumber-1)/4);
        boolean late=activeMatchStart>=0 && now-activeMatchStart>=20L*60*20;
        float maximum=scaledMinionHealth(baseHealth(state.kind),stages,late);
        if(Math.abs(state.maxHealth-maximum)>.01){
            state.health=maximum*Math.max(0,Math.min(1,state.health/state.maxHealth));state.maxHealth=maximum;
            configureProxyHealth(mob,maximum);
        }
        state.attackDamage=baseAttack(state.kind)*(1+stages*.10F)*(late?1.6F:1);
    }

    static float scaledMinionHealth(float base, int stages, boolean late) {
        float oldMaximum = base * (1 + stages * .15F) * (late ? 2 : 1);
        return base + (oldMaximum - base) * .40F;
    }
    public static boolean hasCrystals(){return !layout.crystals.isEmpty();}
    private static boolean crystalVulnerable(Faction defender){
        Faction enemy=defender==Faction.GUARDIAN?Faction.DESTRUCTION:Faction.GUARDIAN;
        for(Lane lane:Lane.values())if(siegeUnlocked(enemy,lane))return true;
        return false;
    }
    private static final Map<UUID, UnitState> UNITS = new HashMap<>();
    private static MinecraftServer server;
    private static long activeMatchStart = Long.MIN_VALUE;
    private static long nextWaveAt = Long.MAX_VALUE;
    private static int waveNumber;
    private static boolean layoutActive;
    private static boolean runtimeNeedsCleanup;

    static final class Layout {
        final Map<Lane, Map<Integer, BlockPos>> paths = new EnumMap<>(Lane.class);
        final Map<Lane, BlockPos> corners = new EnumMap<>(Lane.class);
        final java.util.EnumSet<Lane> manual = java.util.EnumSet.noneOf(Lane.class);
        final Map<BlockPos, Double> radii = new LinkedHashMap<>();
        final Map<TowerKey, BlockPos> towers = new LinkedHashMap<>();
        final java.util.EnumSet<Lane> enabled = java.util.EnumSet.allOf(Lane.class);
        final java.util.SortedSet<Integer> tiers = new java.util.TreeSet<>(List.of(1, 2, 3));
        final Map<Faction, BlockPos> fountains = new EnumMap<>(Faction.class);
        final Map<Faction, BlockPos> crystals = new EnumMap<>(Faction.class);

        Layout() {
            for (Lane lane : Lane.values()) paths.put(lane, new LinkedHashMap<>());
        }

        Layout copy() {
            Layout next = new Layout();
            next.enabled.clear();
            next.enabled.addAll(enabled);
            next.tiers.clear();
            next.tiers.addAll(tiers);
            for (Lane lane : Lane.values()) next.paths.get(lane).putAll(paths.get(lane));
            next.corners.putAll(corners);
            next.manual.addAll(manual);
            next.radii.putAll(radii);
            next.towers.putAll(towers);
            next.fountains.putAll(fountains);
            next.crystals.putAll(crystals);
            return next;
        }
    }

    private static Layout layout = new Layout();

    static void activateLayout(Layout candidate) {
        layout = candidate;
    }

    public static void initialize(MinecraftServer minecraftServer) {
        server = minecraftServer;
        ArenaConfigFile.readOptional(layoutPath(), root -> decodeLayout(root, false)).ifPresent(LaneSystem::activateLayout);
        activeMatchStart = Long.MIN_VALUE;
        runtimeNeedsCleanup = true;
    }

    static void resetArenaRuntime(MinecraftServer minecraftServer) {
        clearRuntime(minecraftServer.overworld());
        activeMatchStart = Long.MIN_VALUE;
        runtimeNeedsCleanup = true;
    }

    public static void shutdown() {
        server = null;
        layout = new Layout();
        UNITS.clear();
        activeMatchStart = Long.MIN_VALUE;
        nextWaveAt = Long.MAX_VALUE;
        layoutActive = false;
    }

    public static void tick(ServerLevel level) {
        if (runtimeNeedsCleanup) {
            clearRuntime(level);
            runtimeNeedsCleanup = false;
        }
        GameManager manager = GameManager.get();
        if (level.getGameTime() % 20 == 0) syncBuildingLayout(level);
        long start = manager == null ? -1 : manager.getMatchStartTime();
        if (manager == null || manager.getPhase() != GameManager.Phase.PLAYING || start < 0) {
            if (activeMatchStart != Long.MIN_VALUE) clearRuntime(level);
            activeMatchStart = Long.MIN_VALUE;
            return;
        }
        if (activeMatchStart != start) resetForMatch(level, start);
        long now = level.getGameTime();
        if (now % 20 == 0) tickFountains(level, manager);
        if (layoutActive && now % 20 == 0) {
            // 玩家离开兵线后也保持三路区块持续模拟，避免小兵走进卸载区块后冻结。
            for (Lane lane : Lane.values()) {
                CombatChunks.holdLaneRoute(level, orderedPath(lane, Faction.GUARDIAN));
            }
        }
        if (layoutActive && now >= nextWaveAt) {
            waveNumber++;
            spawnWave(level);
            nextWaveAt += WAVE_INTERVAL;
        }
        for (Map.Entry<UUID, UnitState> entry : new HashMap<>(UNITS).entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null) entity = entry.getValue().entity;
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                removeVisual(level, entry.getValue());
                UNITS.remove(entry.getKey());
                continue;
            }
            UnitState state = entry.getValue();
            if (now % 20 == 0) CombatChunks.hold(level, mob.blockPosition());
            if (state.tower()) tickTower(level, mob, state, now);
            else tickMinion(level, mob, state, now);
            if (now % 20 == 0) updateName(mob, state);
        }
    }

    private static void resetForMatch(ServerLevel level, long matchStart) {
        clearRuntime(level);
        syncBuildingLayout(level);
        activeMatchStart = matchStart;
        waveNumber = 0;
        // 兵线只依赖可行走路线（或由双方基地自动推导的兜底路线），不应被
        // 尚未摆完的防御塔点位阻塞。这样地图可以先测试兵线，再逐步完善设施。
        layoutActive = pathLayoutReady();
        if (!layoutActive) {
            broadcast(MatchMessages.Tone.WARNING,
                    Component.translatable("message.myriad.lane_config_incomplete"));
        }
        for (Map.Entry<TowerKey, BlockPos> entry : layout.towers.entrySet()) {
            spawnTower(level, entry.getKey(), entry.getValue());
        }
        layout.crystals.forEach((faction,pos)->spawnCrystal(level,faction,pos));
        if (!towerLayoutReady()) {
            broadcast(MatchMessages.Tone.WARNING,
                    Component.translatable("message.myriad.tower_config_incomplete"));
        }
        // 首波从正式开战时刻计时，后续使用共同的波次间隔。
        nextWaveAt = matchStart + GameManager.combatTempo().firstWaveTicks();
    }

    /** 阵营泉水：己方玩家处于中心半径 6 格内时，每秒恢复 10% 最大生命与最大法力。 */
    private static void tickFountains(ServerLevel level, GameManager manager) {
        for (ServerPlayer player : level.players()) {
            if (!com.myriad.combat.NpcCombat.canTarget(player)) continue;
            Faction faction = manager.factionOf(player);
            if (faction != null) for (var enemy : layout.fountains.entrySet()) {
                if(enemy.getKey()==faction || player.distanceToSqr(Vec3.atCenterOf(enemy.getValue()))>FOUNTAIN_RADIUS*FOUNTAIN_RADIUS)continue;
                float hit=Math.max(40,PlayerDataProvider.get(player).getModMaxHealth()*.25F);
                com.myriad.combat.NpcCombat.environmentHit(player,hit,com.myriad.combat.DamageCalc.Kind.TRUE);
                spawnAttackBeam(level,Vec3.atCenterOf(enemy.getValue()).add(0,3,0),player.getBoundingBox().getCenter(),enemy.getKey());
            }
            BlockPos fountain = faction == null ? null : layout.fountains.get(faction);
            if (fountain == null) continue;
            Vec3 center = Vec3.atCenterOf(fountain);
            if (player.distanceToSqr(center) > FOUNTAIN_RADIUS * FOUNTAIN_RADIUS) continue;

            PlayerData data = PlayerDataProvider.get(player);
            if (data == null) continue;
            float healthBefore = player.getHealth();
            float healthRestore = data.getModMaxHealth() * (float) FOUNTAIN_RESTORE_RATIO;
            if (healthBefore < data.getModMaxHealth())
                com.myriad.combat.CombatTelemetry.heal(player, healthRestore, "fountain");

            double maxMana = player.getAttributeValue(com.myriad.registry.ModAttributes.MAX_MANA);
            float manaBefore = data.getMana();
            data.setMana((float)Math.min(maxMana,
                    manaBefore + maxMana * FOUNTAIN_RESTORE_RATIO));
            if (player.getHealth() > healthBefore || data.getMana() > manaBefore) {
                PlayerDataProvider.syncToClient(player);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + .8, player.getZ(), 8, .35, .55, .35, .02);
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + .5, player.getZ(), 4, .25, .4, .25, .015);
            }
        }
        for (Map.Entry<Faction, BlockPos> entry : layout.fountains.entrySet()) {
            BlockPos pos = entry.getValue();
            DustParticleOptions dust = new DustParticleOptions(
                    entry.getKey().rgb(), 1.0F);
            for (int i = 0; i < 32; i++) {
                double angle = Math.PI * 2.0 * i / 32.0;
                level.sendParticles(dust,
                        pos.getX() + .5 + Math.cos(angle) * FOUNTAIN_RADIUS,
                        pos.getY() + .12,
                        pos.getZ() + .5 + Math.sin(angle) * FOUNTAIN_RADIUS,
                        1, 0, 0, 0, 0);
            }
        }
    }

    private static boolean pathLayoutReady() {
        for (Lane lane : layout.enabled) {
            if (orderedPath(lane, Faction.GUARDIAN).size() < 2) return false;
        }
        return true;
    }

    private static boolean towerLayoutReady() {
        for (Lane lane : layout.enabled) {
            for (Faction faction : Faction.values()) {
                for (int tier : layout.tiers) {
                    if (!layout.towers.containsKey(new TowerKey(faction, lane, tier))) return false;
                }
            }
        }
        return true;
    }

    private static void spawnTower(ServerLevel level, TowerKey key, BlockPos pos) {
        CombatChunks.prepare(level, pos);
        Entity raw = com.myriad.registry.ModEntities.DEFENSE_TOWER.get()
                .create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(raw instanceof DefenseTowerEntity tower)) return;
        tower.setTowerFaction(key.faction);
        tower.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        tower.setNoAi(true);
        tower.setNoGravity(true);
        tower.setSilent(true);
        tower.setPersistenceRequired();
        float maxHealth = (float)(switch (key.tier) {
            case 1 -> 8400.0F;
            case 2 -> 11200.0F;
            default -> 15400.0F;
        });
        configureProxyHealth(tower, maxHealth);
        UnitState state = new UnitState(Kind.TOWER, key.faction, key.lane, key.tier, null, maxHealth);
        state.entity = tower;
        updateName(tower, state);
        tower.addTag(LANE_ENTITY_TAG);
        if (!level.addFreshEntity(ModEntityTracker.markGenerated(tower))) return;
        UNITS.put(tower.getUUID(), state);
        ensureTowerVisual(level, tower, state);
    }

    private static void spawnWave(ServerLevel level) {
        for (Lane lane : layout.enabled) {
            for (Faction faction : Faction.values()) {
                List<BlockPos> path = orderedPath(lane, faction);
                if (path.size() < 2) continue;
                boolean siegeWave = siegeUnlocked(faction, lane);
                for (int i = 0; i < MELEE_PER_WAVE; i++) {
                    Kind kind = siegeWave && i < MAX_SIEGE_PER_LANE_WAVE ? Kind.SIEGE : Kind.MELEE;
                    spawnMinion(level, faction, lane, path, kind, i);
                }
                for (int i = 0; i < RANGED_PER_WAVE; i++) {
                    spawnMinion(level, faction, lane, path,
                            waveNumber % 3 == 0 && i == 1 ? Kind.MAGIC : Kind.RANGED, 2.0 + i);
                }
            }
        }
    }

    private static void spawnMinion(ServerLevel level, Faction faction, Lane lane, List<BlockPos> path,
            Kind kind, double formationIndex) {
        Entity raw = (kind==Kind.SIEGE?com.myriad.registry.ModEntities.SIEGE_MINION:com.myriad.registry.ModEntities.LANE_MINION).get()
                .create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(raw instanceof Mob minion)) return;
        Vec3 start = Vec3.atBottomCenterOf(path.getFirst());
        Vec3 direction = Vec3.atBottomCenterOf(path.get(1)).subtract(start).multiply(1, 0, 1);
        Vec3 forward = direction.horizontalDistanceSqr() < 1.0E-6
                ? new Vec3(0, 0, 1) : direction.normalize();
        // A hand-authored corridor begins on traversable ground; keep the entire
        // formation in that verified corridor instead of behind it in a pedestal.
        double offset = usesManualPath(lane)
                ? Math.min(start.subtract(Vec3.atBottomCenterOf(path.get(1))).horizontalDistance() * .8,
                        (3.0 - formationIndex) * 1.8)
                : -formationIndex * 1.8;
        Vec3 spawn = start.add(forward.scale(offset));
        CombatChunks.prepare(level, BlockPos.containing(spawn));
        minion.setPos(spawn.x, spawn.y, spawn.z);
        ((LaneMinionEntity)minion).configure(faction, kind == Kind.MAGIC ? 2 : kind == Kind.RANGED ? 1 : 0);
        minion.setNoAi(true);
        minion.setPersistenceRequired();
        minion.setCanPickUpLoot(false);
        float maxHealth = baseHealth(kind);
        configureProxyHealth(minion, maxHealth);
        UnitState state = new UnitState(kind, faction, lane, 0, List.copyOf(path), maxHealth);
        state.entity = minion;
        scaleMinion(minion,state,level.getGameTime());
        updateName(minion, state);
        minion.addTag(LANE_ENTITY_TAG);
        if (!level.addFreshEntity(ModEntityTracker.markGenerated(minion))) {
            com.myriad.Myriad.LOGGER.error("[lane] failed to spawn {} {} at {}", faction, lane, spawn);
            return;
        }
        UNITS.put(minion.getUUID(), state);
    }

    private static void configureProxyHealth(Mob mob, float maxHealth) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(Math.min(1024.0, maxHealth));
        mob.setHealth((float) Math.min(1024.0, maxHealth));
    }

    private static void tickTower(ServerLevel level, Mob tower, UnitState state, long now) {
        tower.setDeltaMovement(Vec3.ZERO);
        if (now % 20 == 0) ensureTowerVisual(level, tower, state);
        if (state.towerFlashing && now >= state.lastTowerHitFeedback + 4) {
            if (state.visualEntity != null && !state.visualEntity.isRemoved())
                state.visualEntity.setBrightnessOverride(state.towerLightBeforeHit);
            state.towerFlashing = false;
        }
        LivingEntity locked = state.towerLockTarget == null ? null
                : level.getEntity(state.towerLockTarget) instanceof LivingEntity living ? living : null;
        LivingEntity priority = state.towerHeroPriorityTarget == null ? null
                : level.getEntity(state.towerHeroPriorityTarget) instanceof LivingEntity living ? living : null;
        TowerAim choice = towerAim(tower, state, priority);
        if (choice == null) {
            state.towerHeroPriorityTarget = null;
            if (locked != priority) choice = towerAim(tower, state, locked);
        }
        if (choice == null) {
            state.towerLockTarget = null;
            choice = acquireTowerTarget(level, tower, state, locked, priority);
        }
        if (choice == null) {
            warnTowerTarget(state, null);
            return;
        }
        LivingEntity target = choice.target();
        Vec3 aim = choice.point();
        if (!target.getUUID().equals(state.towerLockTarget)) {
            state.towerLockTarget = target.getUUID();
            state.towerLockStarted = now;
            if (state.kind == Kind.CRYSTAL) level.playSound(null, tower.getX(), tower.getY(), tower.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, .45F, 1.25F);
        }
        warnTowerTarget(state, target);
        float lockProgress = Math.clamp((now - state.towerLockStarted) / (float) TOWER_LOCK_TICKS, 0.0F, 1.0F);
        if ((now - state.towerLockStarted) % 2 == 0) {
            spawnTowerLockBeam(level, TowerSight.muzzle(tower), aim, state.faction, lockProgress);
        }
        if (now - state.towerLockStarted < TOWER_LOCK_TICKS) return;
        state.towerLockTarget = null;
        state.lastAttackTick = now;
        float damage = switch (state.towerTier) {
            case 1 -> 72.0F;
            case 2 -> 96.0F;
            default -> 126.0F;
        };
        if(state.kind==Kind.CRYSTAL)damage=144;
        if(target instanceof ServerPlayer hero) {
            var tag=com.myriad.player.EntityState.data(tower);
            String id=hero.getUUID().toString();
            int stacks=id.equals(tag.getStringOr("myriad_tower_target",""))
                    && now-tag.getLongOr("myriad_tower_hit",-1000)<=60?tag.getIntOr("myriad_tower_stacks",0)+1:0;
            stacks=Math.min(4,stacks);tag.putString("myriad_tower_target",id);tag.putInt("myriad_tower_stacks",stacks);tag.putLong("myriad_tower_hit",now);
            damage*=MatchBalance.towerScale(MatchBalance.elapsed(hero),stacks);
        } else {
            // 小兵不吃连续锁定递增；提高塔下清兵效率，不改变对英雄的伤害。
            com.myriad.player.EntityState.data(tower).remove("myriad_tower_target");
            damage *= 1.80F;
        }
        com.myriad.combat.NpcCombat.towerHit(target, tower, damage, .04F,
                com.myriad.combat.DamageCalc.Kind.PHYSICAL);
        spawnTowerLockBeam(level, TowerSight.muzzle(tower), aim, state.faction, 1.0F);
        level.playSound(null, tower.getX(), tower.getY(), tower.getZ(), SoundEvents.GUARDIAN_ATTACK,
                SoundSource.HOSTILE, .8F, state.towerTier == 3 ? .75F : 1.0F);
    }

    /** A sight test is valid only for this tower and tick. Keep its point through aim and fire. */
    private record TowerAim(LivingEntity target, Vec3 point) {}

    private static TowerAim towerAim(Mob tower, UnitState state, LivingEntity target) {
        if (target == null || !com.myriad.combat.NpcCombat.canTarget(target)) return null;
        Faction faction = factionOf(target);
        if (faction == null || faction == state.faction
                || tower.distanceToSqr(target) > TOWER_ATTACK_RANGE * TOWER_ATTACK_RANGE) return null;
        if (state.kind == Kind.CRYSTAL) return new TowerAim(target, target.getBoundingBox().getCenter());
        if (target instanceof ServerPlayer hero && !VisionSystem.visibleToTower(tower, state.faction, hero)) return null;
        Vec3 point = TowerSight.visibleBodyPoint(tower, target);
        return point == null ? null : new TowerAim(target, point);
    }

    private static TowerAim acquireTowerTarget(ServerLevel level, Mob tower, UnitState state,
            LivingEntity rejectedLock, LivingEntity rejectedPriority) {
        if (state.kind == Kind.CRYSTAL) {
            LivingEntity target = nearestEnemyMinion(level, tower, state.faction, TOWER_ATTACK_RANGE);
            if (target == null) target = nearestEnemyPlayer(level, tower, state.faction, TOWER_ATTACK_RANGE);
            return target == null ? null : new TowerAim(target, target.getBoundingBox().getCenter());
        }
        // The nearest visible minion outranks every hero. A previously rejected target has
        // already been tested against this same tower and block geometry during this tick.
        TowerAim best = null;
        boolean bestIsMinion = false;
        double rangeSquared = TOWER_ATTACK_RANGE * TOWER_ATTACK_RANGE;
        double bestDistance = rangeSquared;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                tower.getBoundingBox().inflate(TOWER_ATTACK_RANGE))) {
            if (!candidate.isAlive() || candidate == tower || candidate == rejectedLock || candidate == rejectedPriority) continue;
            var unit = UNITS.get(candidate.getUUID());
            boolean minion = unit != null && !unit.tower();
            if (!minion && (bestIsMinion || !(candidate instanceof ServerPlayer))) continue;
            double distance = tower.distanceToSqr(candidate);
            if (distance >= rangeSquared || minion == bestIsMinion && distance >= bestDistance) continue;
            TowerAim visible = towerAim(tower, state, candidate);
            if (visible != null) {
                best = visible;
                bestDistance = distance;
                bestIsMinion = minion;
            }
        }
        return best;
    }

    private static void warnTowerTarget(UnitState state, LivingEntity target) {
        if (state.kind != Kind.TOWER) return;
        var hero = target instanceof ServerPlayer player ? player : null;
        if (state.warnedHero == hero) return;
        int id = state.entity.getId();
        if (state.warnedHero != null && !state.warnedHero.isRemoved()) {
            com.myriad.network.Packets.sendToPlayer(state.warnedHero, new com.myriad.network.TowerLockPayload(id, false));
        }
        state.warnedHero = hero;
        if (hero != null) {
            com.myriad.network.Packets.sendToPlayer(hero, new com.myriad.network.TowerLockPayload(id, true));
        }
    }

    /**
     * 敌方英雄在塔下伤害守方英雄时立刻吸引塔火。已有有效英雄仇恨时保持首名目标，
     * 避免范围伤害或多人同时出手让防御塔在目标之间反复切换。
     */
    public static void onHeroAttacked(ServerPlayer victim, ServerPlayer attacker) {
        GameManager manager = GameManager.get();
        if (manager == null || manager.getPhase() != GameManager.Phase.PLAYING || victim == attacker) return;
        Faction defender = manager.factionOf(victim), aggressor = manager.factionOf(attacker);
        if (defender == null || aggressor == null || defender == aggressor || victim.level() != attacker.level()) return;
        long now = victim.level().getGameTime();
        for (UnitState state : UNITS.values()) {
            Mob tower = state.entity;
            if (state.kind != Kind.TOWER || state.faction != defender || tower == null || !tower.isAlive()
                    || tower.distanceToSqr(victim) > TOWER_ATTACK_RANGE * TOWER_ATTACK_RANGE
                    || tower.distanceToSqr(attacker) > TOWER_ATTACK_RANGE * TOWER_ATTACK_RANGE
                    || towerAim(tower, state, attacker) == null) continue;
            LivingEntity priority = state.towerHeroPriorityTarget == null ? null
                    : tower.level().getEntity(state.towerHeroPriorityTarget) instanceof LivingEntity living ? living : null;
            if (towerAim(tower, state, priority) != null) continue;
            LivingEntity locked = state.towerLockTarget == null ? null
                    : tower.level().getEntity(state.towerLockTarget) instanceof LivingEntity living ? living : null;
            if (locked instanceof ServerPlayer && towerAim(tower, state, locked) != null) {
                state.towerHeroPriorityTarget = locked.getUUID();
                continue;
            }
            state.towerHeroPriorityTarget = attacker.getUUID();
            state.towerLockTarget = attacker.getUUID();
            state.towerLockStarted = now;
            warnTowerTarget(state, attacker);
        }
    }

    private static void tickMinion(ServerLevel level, Mob minion, UnitState state, long now) {
        int cooldown = state.ranged() ? 36 : state.kind==Kind.SIEGE?30:22;
        if(now>state.lastRecoveryTick){
            state.attackRecovery=Math.min(cooldown,state.attackRecovery+
                    com.myriad.character.skills.keheng.KehengTimeFields.managedNpcRate(minion,now));
            state.lastRecoveryTick=now;
        }
        ((LaneMinionEntity)minion).setMoving(false);
        scaleMinion(minion,state,now);
        NpcNavigation.separate(level,(LaneMinionEntity)minion);
        // Old checkpoints may contain a pending retreat. Resume forward instead.
        state.returning = false;
        advanceLaneProgress(minion, state);
        Vec3 laneReturn = laneReturnPoint(minion, state);
        if (laneReturn != null && minion.position().multiply(1, 0, 1).distanceToSqr(laneReturn.multiply(1, 0, 1)) > 100) {
            state.chaseTarget = null;
            state.defendedAgainst = null;
            state.chaseOrigin = null;
            state.navigation.clear();
            moveToward(level, minion, laneReturn, .28);
            return;
        }
        LivingEntity target = state.chaseTarget == null ? null
                : level.getEntity(state.chaseTarget) instanceof LivingEntity living ? living : null;
        UnitState tracked = target == null ? null : UNITS.get(target.getUUID());
        boolean towerTarget = tracked != null && tracked.tower();
        LivingEntity towerInAttackRange = nearestVulnerableTower(level, minion, state,
                minionAttackRange(state));
        if (!towerTarget && towerInAttackRange != null) {
            target = towerInAttackRange;
            tracked = UNITS.get(target.getUUID());
            towerTarget = true;
            state.chaseTarget = target.getUUID();
            state.defendedAgainst = null;
            state.chaseOrigin = minion.position();
            state.navigation.clear();
        }
        if (state.chaseTarget != null && (target == null || !com.myriad.combat.NpcCombat.canTarget(target)
                || factionOf(target) == null || factionOf(target) == state.faction
                || target instanceof ServerPlayer player && (VisionSystem.concealedFromNpc(minion, player)
                        || !NpcNavigation.visible(minion, target)
                        || target.getUUID().equals(state.defendedAgainst) && !inDefenseRange(minion,player,state))
                || towerTarget && (tracked.kind!=Kind.CRYSTAL&&tracked.lane != state.lane || !isTowerVulnerable(tracked))
                || towerTarget && (platformRadius(target)>0&&!inMinionAttackRange(minion,target,state,minionAttackRange(state))
                        || hasPendingCorner(state)
                        && minion.distanceToSqr(target) > minionAttackRange(state) * minionAttackRange(state)
                        || minion.distanceToSqr(target) > 100
                        && !NpcNavigation.visible(minion, target))
                || !towerTarget && (minion.distanceToSqr(target) > 400
                || tracked == null && state.chaseOrigin != null && (target.position().distanceToSqr(state.chaseOrigin) > 400
                || minion.position().distanceToSqr(state.chaseOrigin) > 400)))) {
            state.ignoredTarget = state.chaseTarget;
            state.ignoreUntil = now + 40;
            state.chaseTarget = null;
            state.defendedAgainst = null;
            state.chaseOrigin = null;
            state.nextTargetScan = now;
            state.navigation.clear();
            target = null;
        }
        // A tower/player lock must not let opposing waves walk past one another.
        if (target != null && !target.getUUID().equals(state.defendedAgainst)
                && (tracked == null || tracked.tower()) && now >= state.nextTargetScan) {
            state.nextTargetScan = now + 5;
            LivingEntity blocker = nearestLaneEnemy(level, minion, state);
            if (blocker != null) {
                target = blocker;
                state.chaseTarget = null;
                state.chaseOrigin = null;
                state.navigation.clear();
            }
        }
        if (target == null && now >= state.nextTargetScan) {
            state.nextTargetScan = now + 5;
            double attackRange = minionAttackRange(state);
            target = nearestVulnerableTower(level, minion, state, attackRange);
            if (target == null) target = nearestLaneEnemy(level, minion, state);
            if (target == null) {
                target = nearestEnemyPlayer(level, minion, state.faction, 12.0,
                        now < state.ignoreUntil ? state.ignoredTarget : null);
            }
            if (target == null) {
                Mob objective = laneObjective(state);
                if (objective != null && platformRadius(objective)==0 && !hasPendingCorner(state)
                        && (minion.distanceToSqr(objective) <= 100 || reachedEnemySide(state)
                        && minion.distanceToSqr(objective) <= 1600 && NpcNavigation.clearWalk(minion,
                                objective.position().add(minion.position().subtract(objective.position()).normalize()
                                        .scale(objective.getBbWidth() * .5 + .8))))
                        && NpcNavigation.visible(minion, objective)) target = objective;
            }
        }
        if (target != null && state.chaseTarget == null) {
            state.chaseTarget = target.getUUID();
            state.chaseOrigin = minion.position();
            state.navigation.clear();
            UnitState objective = UNITS.get(target.getUUID());
            if (objective != null && objective.tower() && state.path != null) {
                // Engaging a tower commits lane progress, including for ranged units
                // that stop before reaching the tower's waypoint.
                BlockPos point = objective.kind==Kind.CRYSTAL?layout.crystals.get(objective.faction)
                        :layout.towers.get(new TowerKey(objective.faction, objective.lane, objective.towerTier));
                int index = point==null?-1:state.path.indexOf(point);
                if (index >= 0) state.waypointIndex = Math.max(state.waypointIndex, index);
            }
        }
        double range = minionAttackRange(state);
        if (target != null) {
            if (inMinionAttackRange(minion,target,state,range) && visibleLaneTarget(minion,target)) {
                state.navigation.clear();
                if (state.attackRecovery + 1E-9 >= cooldown) {
                    state.attackRecovery = 0;
                    state.lastAttackTick = now;
                    ((LaneMinionEntity)minion).attackAnimation();
                    minion.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
                    float damage = state.attackDamage;
                    UnitState targetState = UNITS.get(target.getUUID());
                    com.myriad.combat.NpcCombat.hit(target, minion, damage,
                            state.kind == Kind.MAGIC ? com.myriad.combat.DamageCalc.Kind.MAGIC
                                    : com.myriad.combat.DamageCalc.Kind.PHYSICAL);
                    if (state.ranged()) {
                        spawnAttackBeam(level, minion.getEyePosition(), target.getBoundingBox().getCenter(),
                                state.faction);
                        if (state.kind == Kind.MAGIC) level.sendParticles(ParticleTypes.END_ROD,
                                target.getX(), target.getY() + .8, target.getZ(), 8, .3, .3, .3, .04);
                        level.playSound(null, minion.getX(), minion.getY(), minion.getZ(),
                                SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, .45F, 1.25F);
                    }
                }
            } else {
                moveToward(level, minion, target.position(), .28);
            }
            return;
        }
        if (state.path == null || state.waypointIndex >= state.path.size()) return;
        // Tower positions remain geometric waypoints after destruction. Skipping them
        // makes the wave cut across walls toward a distant next tower.
        if (state.waypointIndex >= state.path.size()) return;
        Vec3 waypoint = Vec3.atBottomCenterOf(state.path.get(state.waypointIndex));
        moveToward(level, minion, waypoint, .28);
    }

    private static double minionAttackRange(UnitState state) {
        return state.ranged() ? 8.0 : state.kind == Kind.SIEGE ? 2.4 : 1.8;
    }

    /** 返回小兵当前进度附近最近的兵线路径点，用于被击退或追敌过远后的强制归线。 */
    private static Vec3 laneReturnPoint(Mob minion, UnitState state) {
        if (state.path == null || state.path.isEmpty()) return null;
        int toIndex = Math.min(state.path.size() - 1, Math.max(0, state.waypointIndex));
        int fromIndex = Math.max(0, toIndex - 1);
        Vec3 from = Vec3.atBottomCenterOf(state.path.get(fromIndex));
        Vec3 to = Vec3.atBottomCenterOf(state.path.get(toIndex));
        Vec3 segment = to.subtract(from).multiply(1, 0, 1);
        if (segment.lengthSqr() < 1.0E-6) return to;
        Vec3 position = minion.position().multiply(1, 0, 1);
        double t = Math.clamp(position.subtract(from.multiply(1, 0, 1)).dot(segment) / segment.lengthSqr(), 0.0, 1.0);
        Vec3 horizontal = from.multiply(1, 0, 1).add(segment.scale(t));
        return new Vec3(horizontal.x, from.y + (to.y - from.y) * t, horizontal.z);
    }

    private static void skipDestroyedTowerPoints(UnitState state) {
        while (state.waypointIndex < state.path.size()) {
            BlockPos point = state.path.get(state.waypointIndex);
            TowerKey key = layout.towers.keySet().stream().filter(k -> k.lane == state.lane
                    && k.faction != state.faction && layout.towers.get(k).equals(point)).findFirst().orElse(null);
            if (key == null || UNITS.values().stream().anyMatch(s -> s.tower() && s.faction == key.faction
                    && s.lane == key.lane && s.towerTier == key.tier && s.health > 0
                    && s.entity != null && s.entity.isAlive())) break;
            state.waypointIndex++;
            state.navigation.clear();
        }
    }

    private static Mob laneObjective(UnitState minion) {
        Mob tower=UNITS.values().stream().filter(s -> s.kind==Kind.TOWER && s.lane == minion.lane
                && s.faction != minion.faction && s.health > 0 && s.entity != null && s.entity.isAlive())
                .min(Comparator.comparingInt(s -> s.towerTier)).map(s -> s.entity).orElse(null);
        if(tower!=null)return tower;
        return UNITS.values().stream().filter(s->s.kind==Kind.CRYSTAL&&s.faction!=minion.faction&&s.health>0
                &&s.entity!=null&&s.entity.isAlive()&&crystalVulnerable(s.faction)).map(s->s.entity).findFirst().orElse(null);
    }

    private static LivingEntity nearestLaneEnemy(ServerLevel level, Mob minion, UnitState state) {
        return nearest(level, minion, 12, candidate -> {
            UnitState other = UNITS.get(candidate.getUUID());
            return other != null && !other.tower() && other.health > 0 && other.lane == state.lane
                    && other.faction != state.faction && com.myriad.combat.NpcCombat.canTarget(candidate)
                    && NpcNavigation.visible(minion, candidate);
        });
    }

    private static boolean reachedEnemySide(UnitState state) {
        if (state.path == null) return true;
        for (int i = 1; i < state.path.size(); i++) {
            BlockPos point = state.path.get(i);
            if (layout.towers.entrySet().stream().anyMatch(e -> e.getKey().lane == state.lane
                    && e.getKey().faction != state.faction && e.getValue().equals(point))) return state.waypointIndex >= i;
        }
        return state.waypointIndex >= state.path.size();
    }

    private static boolean hasPendingCorner(UnitState state) {
        BlockPos corner = layout.corners.get(state.lane);
        return corner != null && state.path != null && state.path.indexOf(corner) >= state.waypointIndex;
    }

    /** Advance monotonically even during combat: never revisit an already passed waypoint. */
    private static void advanceLaneProgress(Mob mob, UnitState state) {
        if (state.path == null) return;
        Vec3 position = mob.position().multiply(1,0,1);
        // Combat can carry a minion around a bend. Rejoin the furthest nearby
        // forward segment instead of walking back to the missed corner.
        for (int i = state.path.size() - 1; i > state.waypointIndex; i--) {
            BlockPos corner = layout.corners.get(state.lane);
            int cornerIndex = corner == null ? -1 : state.path.indexOf(corner);
            if (cornerIndex >= state.waypointIndex && i > cornerIndex) continue;
            Vec3 from = Vec3.atBottomCenterOf(state.path.get(i - 1)).multiply(1,0,1);
            Vec3 to = Vec3.atBottomCenterOf(state.path.get(i)).multiply(1,0,1);
            Vec3 segment = to.subtract(from);
            if (segment.lengthSqr() < .01) continue;
            double t = position.subtract(from).dot(segment) / segment.lengthSqr();
            if (t >= 0 && t <= 1 && position.distanceToSqr(from.add(segment.scale(t))) <= 16
                    && NpcNavigation.clearWalk(mob, Vec3.atBottomCenterOf(state.path.get(i)))) {
                state.waypointIndex = i;
                state.navigation.clear();
                break;
            }
        }
        while (state.waypointIndex < state.path.size()) {
            int i = state.waypointIndex;
            Vec3 point = Vec3.atBottomCenterOf(state.path.get(i)).multiply(1,0,1);
            Vec3 previous = Vec3.atBottomCenterOf(state.path.get(Math.max(0,i-1))).multiply(1,0,1);
            Vec3 incoming = point.subtract(previous);
            double projection = incoming.lengthSqr() > .01 ? position.subtract(point).dot(incoming) / incoming.lengthSqr() : 0;
            boolean passed = incoming.lengthSqr() > .01 && projection >= 0
                    && position.distanceToSqr(point.add(incoming.scale(projection))) <= 1.5625;
            double radius = layout.towers.containsValue(state.path.get(i)) ? 3.5 : 1.25;
            if (state.path.get(i).equals(layout.corners.get(state.lane))) {
                passed = false;
                radius = 2.25; // navigation stops short; formations cannot all occupy one block
            }
            if (!passed && position.distanceToSqr(point) > radius*radius) break;
            if (!state.path.get(i).equals(layout.corners.get(state.lane))
                    && !layout.towers.containsValue(state.path.get(i)) && i + 1 < state.path.size()
                    && !NpcNavigation.clearWalk(mob, Vec3.atBottomCenterOf(state.path.get(i + 1)))
                    && position.distanceToSqr(point) > .16) break;
            state.waypointIndex++;
            state.navigation.clear();
        }
    }

    private static void moveToward(ServerLevel level, Mob mob, Vec3 target, double speed) {
        UnitState state = UNITS.get(mob.getUUID());
        if (state != null) NpcNavigation.move(level,mob,state.navigation,target,speed);
    }

    /** 使用模组减伤和护盾链路，不受世界难度缩放影响。 */
    private static void applyNpcDamage(LivingEntity target, Mob attacker, float damage) {
        com.myriad.combat.NpcCombat.hit(target, attacker, damage,
                com.myriad.combat.DamageCalc.Kind.PHYSICAL);
    }

    private static LivingEntity nearestEnemyMinion(ServerLevel level, LivingEntity source, Faction faction,
            double range) {
        return nearest(level, source, range, candidate -> {
            UnitState other = UNITS.get(candidate.getUUID());
            return other != null && !other.tower() && other.faction != faction;
        });
    }

    private static LivingEntity nearestVulnerableTower(ServerLevel level, Mob source, UnitState seeker,
            double range) {
        if (usesManualPath(seeker.lane)) {
            double search=range+12;
            return nearest(level,source,search,candidate->{
                UnitState other=UNITS.get(candidate.getUUID());
                return other!=null&&other.tower()&&other.faction!=seeker.faction
                        &&(other.kind==Kind.CRYSTAL||other.lane==seeker.lane)
                        &&isTowerVulnerable(other)&&inMinionAttackRange(source,candidate,seeker,range)
                        &&visibleLaneTarget(source,candidate);
            });
        }
        return nearest(level, source, range, candidate -> {
            UnitState other = UNITS.get(candidate.getUUID());
            return other != null && other.tower() && other.faction != seeker.faction
                    && (other.kind == Kind.CRYSTAL || other.lane == seeker.lane)
                    && isTowerVulnerable(other) && NpcNavigation.visible(source, candidate);
        });
    }

    private static double platformRadius(LivingEntity target){
        return MatchArena.active()==MatchArena.Mode.DUEL&&!layout.manual.isEmpty()&&target instanceof DefenseTowerEntity
                ? layout.radii.getOrDefault(BlockPos.containing(target.position()),0.0) : 0.0;
    }

    private static boolean visibleLaneTarget(Mob from,LivingEntity target){
        // On a raised pedestal the exposed upper body can be visible even when
        // the low edge of the hitbox is occluded by its own foundation.
        if(NpcNavigation.visible(from,target))return true;
        if(platformRadius(target)<=0)return false;
        Vec3 upper=target.position().add(0,target.getBbHeight()*.78,0);
        return from.level().clip(new net.minecraft.world.level.ClipContext(from.getEyePosition(),upper,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,from))
                .getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static boolean inMinionAttackRange(Mob source,LivingEntity target,UnitState seeker,double range){
        UnitState tracked=UNITS.get(target.getUUID());
        if(tracked==null||!tracked.tower())return source.distanceToSqr(target)<=range*range;
        double platform=usesManualPath(seeker.lane)?platformRadius(target):0;
        if(platform>0)return platformInReach(source.position(),target.position(),platform,range);
        double normal=range+target.getBbWidth()*.5;
        return source.distanceToSqr(target)<=normal*normal;
    }

    static boolean platformInReach(Vec3 source,Vec3 building,double radius,double attackReach){
        double reach=radius+attackReach;
        return Math.abs(source.y-building.y)<=5&&source.subtract(building).horizontalDistanceSqr()<=reach*reach;
    }

    private static LivingEntity nearestEnemyPlayer(ServerLevel level, LivingEntity source, Faction faction,
            double range) {
        return nearestEnemyPlayer(level, source, faction, range, null);
    }

    private static LivingEntity nearestEnemyPlayer(ServerLevel level, LivingEntity source, Faction faction,
            double range, UUID ignoredTarget) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                source.getBoundingBox().inflate(range), p -> com.myriad.combat.NpcCombat.canTarget(p))) {
            if (player.getUUID().equals(ignoredTarget)) continue;
            if (factionOf(player) == null || factionOf(player) == faction) continue;
            if (source instanceof LaneMinionEntity && (VisionSystem.concealedFromNpc(source, player)
                    || !source.hasLineOfSight(player))) continue;
            double distance = player.distanceToSqr(source);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static LivingEntity nearest(ServerLevel level, LivingEntity source, double range,
            java.util.function.Predicate<LivingEntity> predicate) {
        LivingEntity best = null;
        double bestDistance = range * range;
        AABB box = source.getBoundingBox().inflate(range);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != source && e.isAlive() && predicate.test(e))) {
            double distance = candidate.distanceToSqr(source);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isTowerVulnerable(UnitState tower) {
        if(tower.kind==Kind.CRYSTAL)return crystalVulnerable(tower.faction);
        if (!tower.tower()) return true;
        for (UnitState state : UNITS.values()) {
            if (state.tower() && state.faction == tower.faction && state.lane == tower.lane
                    && state.towerTier < tower.towerTier && state.health > 0) return false;
        }
        return true;
    }

    private static void spawnAttackBeam(ServerLevel level, Vec3 from, Vec3 to, Faction faction) {
        DustParticleOptions color = new DustParticleOptions(faction.rgb(), 1.0F);
        int count = Math.max(3, (int) Math.ceil(from.distanceTo(to) * 2.5));
        for (int i = 0; i <= count; i++) {
            Vec3 point = from.lerp(to, i / (double) count);
            level.sendParticles(color, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.END_ROD, point.x, point.y, point.z,
                    1, 0, 0, 0, 0);
        }
    }

    /** 防御塔锁定提示线：两秒内持续追踪目标，并以粒子尺寸和双侧轮廓逐渐加粗。 */
    private static void spawnTowerLockBeam(ServerLevel level, Vec3 from, Vec3 to, Faction faction,
            float progress) {
        int rgb = faction.rgb();
        DustParticleOptions core = new DustParticleOptions(rgb, .35F + progress * 1.45F);
        Vec3 delta = to.subtract(from);
        Vec3 side = new Vec3(-delta.z, 0, delta.x);
        if (side.lengthSqr() < 1.0E-5) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        double radius = .025 + progress * .19;
        int count = Math.max(4, (int) Math.ceil(from.distanceTo(to) * 1.25));
        for (int i = 0; i <= count; i++) {
            Vec3 point = from.lerp(to, i / (double) count);
            level.sendParticles(core, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            if (progress >= .45F && i % 2 == 0) {
                Vec3 edge = side.scale(radius);
                level.sendParticles(core, point.x + edge.x, point.y, point.z + edge.z, 1, 0, 0, 0, 0);
                level.sendParticles(core, point.x - edge.x, point.y, point.z - edge.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 由伤害事件在最终伤害计算后调用；返回 true 表示目标属于兵线系统。 */
    public static boolean absorbIncomingDamage(LivingEntity target,
            net.minecraft.world.damagesource.DamageSource source,
            com.myriad.combat.DamageContext event) {
        UnitState state = UNITS.get(target.getUUID());
        if (state == null) return false;
        Faction attacker = factionOf(source.getEntity());
        if(state.kind==Kind.CRYSTAL&&(GameManager.get()==null||GameManager.get().getPhase()!=GameManager.Phase.PLAYING)){
            event.setAmount(0);return true;
        }
        if (attacker == null || attacker == state.faction || (state.tower() && !isTowerVulnerable(state))) {
            event.setAmount(0.0F);
            return true;
        }
        float amount = Math.max(0.0F, event.getAmount());
        if (!state.tower() && source.getEntity() instanceof ServerPlayer) {
            GameManager manager = GameManager.get();
            long elapsed = manager == null ? 0 : manager.getMatchElapsedTicks(target.level().getGameTime());
            amount *= heroDamageMultiplier(elapsed);
        }
        // 无己方兵线进入塔下时保留 80% 反偷塔减伤，让兵线成为主要推进条件。
        if (state.tower() && source.getEntity() instanceof ServerPlayer
                && !hasMinionNear(target, attacker, 12.0)) {
            amount *= 0.20F;
        }
        // 前期保护只作用于三座防御塔，不作用于水晶；5 分钟后开始衰减，12 分钟完全解除。
        if (state.kind == Kind.TOWER) {
            GameManager manager = GameManager.get();
            long elapsed = manager == null ? 0 : manager.getMatchElapsedTicks(target.level().getGameTime());
            amount *= (float) MatchBalance.towerDamageTakenScale(elapsed);
        }
        float before=state.health;
        state.health = Math.max(0.0F, state.health - amount);
        if (state.kind==Kind.TOWER && state.health<before) towerHitFeedback(target,state);
        updateName(target, state);
        if (source.getEntity() instanceof ServerPlayer player) {
            com.myriad.network.Packets.sendToPlayer(player,
                    new NetworkSetup.JiarenHpPayload(target.getId(), state.health, state.maxHealth));
        }
        event.setAmount(state.health <= 0 ? 2048.0F : 0.0F);
        return true;
    }

    /** Anti-hero protection grows smoothly; no hard damage cap and NPC clashes are unaffected. */
    static float heroDamageMultiplier(long elapsedTicks) {
        double minutes = Math.max(0, elapsedTicks) / 1200.0;
        return (float)(100.0 / (100.0 + Math.min(100.0, 25.0 + minutes * 3.0)));
    }

    private static boolean hasMinionNear(LivingEntity tower, Faction faction, double range) {
        if (!(tower.level() instanceof ServerLevel level)) return false;
        double rangeSqr = range * range;
        for (Map.Entry<UUID, UnitState> entry : UNITS.entrySet()) {
            UnitState state = entry.getValue();
            if (state.tower() || state.faction != faction) continue;
            Entity entity = level.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isAlive()
                    && living.distanceToSqr(tower) <= rangeSqr) return true;
        }
        return false;
    }

    public static boolean isManaged(LivingEntity entity) {
        return entity != null && UNITS.containsKey(entity.getUUID());
    }

    public static boolean isTower(LivingEntity entity) {
        UnitState state = entity == null ? null : UNITS.get(entity.getUUID());
        return state != null && state.tower();
    }

    /** 兵线单位或防御塔的独立生命值；非兵线实体回退到原版生命。 */
    public static float health(LivingEntity entity) {
        UnitState state = entity == null ? null : UNITS.get(entity.getUUID());
        return state == null ? (entity == null ? 0.0F : entity.getHealth()) : state.health;
    }

    public static Faction factionOf(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            GameManager manager = GameManager.get();
            return manager == null ? null : manager.factionOf(player);
        }
        UnitState state = entity == null ? null : UNITS.get(entity.getUUID());
        return state == null ? null : state.faction;
    }

    /** 兵线单位/塔死亡奖励与状态回收。 */
    public static void onDeath(LivingEntity entity, Entity killerEntity) {
        onDeath(entity, killerEntity, false);
    }

    public static void onDeath(LivingEntity entity, Entity killerEntity, boolean administrativeRemoval) {
        UnitState state = UNITS.remove(entity.getUUID());
        if (state == null) return;
        if (entity.level() instanceof ServerLevel level) removeVisual(level, state);
        Faction killerFaction = factionOf(killerEntity);
        if(state.kind==Kind.CRYSTAL){
            if(crystalVulnerable(state.faction)
                    &&GameManager.get()!=null&&GameManager.get().getPhase()==GameManager.Phase.PLAYING)
                GameManager.get().crystalDestroyed(state.faction == Faction.GUARDIAN ? Faction.DESTRUCTION : Faction.GUARDIAN,
                        entity.level().getGameTime());
            return;
        }
        if (state.tower()) {
            if (!administrativeRemoval && GameManager.get() != null) GameManager.get().recordTowerFall(entity.level().getGameTime());
            DESTROYED_TOWERS.add(towerId(state.faction,state.lane,state.towerTier));
            if (server != null) {
                for (ServerPlayer player : server.overworld().players()) {
                    if (GameManager.get().isParticipant(player.getUUID()) && factionOf(player) == state.faction) {
                        com.myriad.network.Packets.sendToPlayer(player,
                                new com.myriad.network.TowerAlertPayload(
                                        state.lane.id, state.towerTier));
                    }
                }
            }
            if (killerFaction != null && killerFaction != state.faction) {
                int reward = GlobalBalance.goldIncome(switch (state.towerTier) { case 1 -> 150; case 2 -> 250; default -> 400; });
                grantTeamGold(killerFaction, reward, entity);
                broadcast(MatchMessages.Tone.OBJECTIVE,
                        Component.translatable("message.myriad.tower_destroyed",
                        state.faction.displayName(), Component.translatable("lane.myriad." + state.lane.id),
                        state.towerTier, killerFaction.displayName(), reward));
            }
        } else if (!administrativeRemoval && entity.level() instanceof ServerLevel level
                && (killerFaction == null || killerFaction != state.faction)) {
            distributeMinionReward(level, entity, state, killerEntity);
        }
    }

    private enum MinionCredit { LAST_HIT, TEAMMATE, NON_PLAYER }

    private static void distributeMinionReward(ServerLevel level, LivingEntity minion, UnitState state, Entity source) {
        var match = GameManager.get();
        if (match == null) return;
        boolean queuedBattle = match.isQueuedMatch() && match.getPhase() == GameManager.Phase.PLAYING
                && level == level.getServer().overworld();
        ServerPlayer finisher = source instanceof ServerPlayer player ? player : null;
        if (finisher != null) {
            if (finisher.level() != level || !level.players().contains(finisher) || factionOf(finisher) == null
                    || factionOf(finisher) == state.faction
                    || queuedBattle && (!match.isParticipant(finisher.getUUID()) || TrainingMode.isEnabled(finisher)
                        || level.getServer().getPlayerList().getPlayer(finisher.getUUID()) != finisher)) return;
            rewardMinion(finisher, minion, state, MinionCredit.LAST_HIT);
            if (!queuedBattle) return;
        }
        for (ServerPlayer player : level.players()) {
            if (player == finisher || !player.isAlive() || player.isSpectator()
                    || player.distanceToSqr(minion) > 144 || factionOf(player) == null || factionOf(player) == state.faction
                    || queuedBattle && (!match.isParticipant(player.getUUID()) || TrainingMode.isEnabled(player)
                        || level.getServer().getPlayerList().getPlayer(player.getUUID()) != player)) continue;
            rewardMinion(player, minion, state, finisher == null ? MinionCredit.NON_PLAYER : MinionCredit.TEAMMATE);
        }
    }

    private static void rewardMinion(ServerPlayer player, LivingEntity minion, UnitState state, MinionCredit credit) {
        PlayerData data = PlayerDataProvider.get(player);
        boolean lastHit = credit == MinionCredit.LAST_HIT;
        int baseGold = switch (state.kind) {
            case MAGIC -> 65;
            case SIEGE -> 40;
            default -> state.ranged() ? 18 : 12;
        };
        int baseXp = state.kind == Kind.SIEGE ? 50 : state.ranged() ? 24 : 18;
        int gold = credit == MinionCredit.TEAMMATE ? 0
                : GlobalBalance.goldIncome(MatchBalance.farmReward(player, minionReward(baseGold, lastHit, false), false));
        int xp = MatchBalance.farmReward(player, minionReward(baseXp, lastHit, true), true);
        data.addGold(gold);
        LevelSystem.addXp(player, data, xp); // Synchronizes both values after the award.
        com.myriad.network.RewardFloatPayload.send(player, minion, gold, xp);
    }

    static int minionReward(int base, boolean playerKill) {
        return minionReward(base, playerKill, false);
    }

    static int minionReward(int base, boolean playerKill, boolean experience) {
        GameManager manager = GameManager.get();
        long elapsed = manager == null || manager.getPhase() != GameManager.Phase.PLAYING || server == null
                ? 0 : Math.max(0, manager.getMatchElapsedTicks(server.overworld().getGameTime()));
        // 每 2 分钟提高 10%，最多翻倍；明确按对局时间而不是波次结算。
        long tenths = 10L + Math.min(10L, elapsed / (2L * 60L * 20L));
        long sharePercent = playerKill ? 100L : experience ? 80L : 25L;
        return (int)Math.min(Integer.MAX_VALUE,base*tenths*sharePercent/1000L);
    }

    private static void towerHitFeedback(LivingEntity tower,UnitState state) {
        if (!(tower.level() instanceof ServerLevel level))return;
        long now=level.getGameTime();
        if(state.lastTowerHitFeedback!=Long.MIN_VALUE && now-state.lastTowerHitFeedback<5)return;
        state.lastTowerHitFeedback=now;
        if(state.visualEntity!=null&&!state.visualEntity.isRemoved()){
            if (!state.towerFlashing) state.towerLightBeforeHit=state.visualEntity.getBrightnessOverride();
            state.visualEntity.setBrightnessOverride(new net.minecraft.util.Brightness(15,15));
            state.towerFlashing = true;
        }
        Vec3 at=tower.getBoundingBox().getCenter().add(0,.25,0);
        var gold=new DustParticleOptions(0xFFC670,1.3F);
        var ice=new DustParticleOptions(0xB8EAFF,1F);
        var sound=new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT),
                net.minecraft.sounds.SoundSource.BLOCKS,at.x,at.y,at.z,.55F,1.30F,level.getRandom().nextLong());
        for(var viewer:level.players()) {
            if(viewer.connection==null||viewer.distanceToSqr(tower)>32*32||!VisionSystem.canSee(viewer,tower)
                    ||!viewer.hasLineOfSight(tower))continue;
            level.sendParticles(viewer,gold,false,false,at.x,at.y,at.z,9,.38,.43,.38,.0);
            level.sendParticles(viewer,ice,false,false,at.x,at.y,at.z,6,.30,.45,.30,.0);
            viewer.connection.send(sound);
        }
    }

    private static boolean inDefenseRange(Mob minion,ServerPlayer attacker,UnitState state) {
        return inMinionAttackRange(minion,attacker,state,minionAttackRange(state)*1.10);
    }

    /** Nearby allied minions defend a hero against a visible player attacker. */
    public static void defendPlayer(ServerPlayer victim, ServerPlayer attacker) {
        GameManager manager = GameManager.get();
        if (manager == null || manager.getPhase() != GameManager.Phase.PLAYING) return;
        Faction ally = factionOf(victim), enemy = factionOf(attacker);
        if (ally == null || enemy == null || ally == enemy || !com.myriad.combat.NpcCombat.canTarget(attacker)) return;
        for (UnitState state : UNITS.values()) {
            Mob minion = state.entity;
            if (state.tower() || state.faction != ally || minion == null || !minion.isAlive()
                    || minion.level() != attacker.level() || !inDefenseRange(minion,attacker,state)
                    || VisionSystem.concealedFromNpc(minion, attacker)
                    || !NpcNavigation.visible(minion, attacker)) continue;
            if(attacker.getUUID().equals(state.defendedAgainst)&&attacker.getUUID().equals(state.chaseTarget))continue;
            state.chaseTarget = attacker.getUUID();
            state.defendedAgainst = attacker.getUUID();
            state.chaseOrigin = minion.position();
            state.ignoredTarget = null;
            state.ignoreUntil = 0;
            state.navigation.clear();
        }
    }

    private static void grantTeamGold(Faction faction, int amount, LivingEntity victim) {
        if (server == null) return;
        GameManager manager = GameManager.get();
        for (ServerPlayer player : server.overworld().players()) {
            if (manager == null || !manager.isParticipant(player.getUUID()) || manager.factionOf(player) != faction) continue;
            PlayerData data = PlayerDataProvider.get(player);
            if (data != null) {
                data.addGold(amount);
                com.myriad.network.RewardFloatPayload.send(player, victim, amount, 0);
                PlayerDataProvider.syncToClient(player);
            }
        }
    }

    private static void updateName(LivingEntity entity, UnitState state) {
        if (entity instanceof LaneMinionEntity minion) {
            minion.setDisplayHealth(state.health, state.maxHealth);
            minion.setCustomName(null);
            minion.setCustomNameVisible(false);
            return;
        }
        if(state.kind==Kind.CRYSTAL){entity.setCustomName(Component.translatable("entity.myriad.base_crystal",state.faction.displayName(),Math.round(state.health),Math.round(state.maxHealth)));entity.setCustomNameVisible(true);return;}
        ChatFormatting color = state.faction.chatColor();
        Component name;
        if (state.tower()) {
            name = Component.translatable("entity.myriad.lane_tower",
                    state.faction.displayName(), Component.translatable("lane.myriad." + state.lane.id),
                    state.towerTier, Math.round(state.health), Math.round(state.maxHealth)).withStyle(color);
        } else {
            name = Component.translatable(state.kind == Kind.SIEGE ? "entity.myriad.siege_minion" : state.kind == Kind.MAGIC ? "entity.myriad.magic_minion" : state.ranged()
                    ? "entity.myriad.ranged_minion" : "entity.myriad.melee_minion",
                    state.faction.displayName(), Math.round(state.health), Math.round(state.maxHealth)).withStyle(color);
        }
        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
    }

    private static boolean usesManualPath(Lane lane){
        return MatchArena.active()==MatchArena.Mode.DUEL&&layout.manual.contains(lane)&&layout.paths.get(lane).size()>=2;
    }
    static List<BlockPos> orderedPath(Lane lane, Faction faction) {
        if (!layout.enabled.contains(lane)) return List.of();
        // Existing arenas keep tower-derived routing unless the 3v3 layout explicitly opts in.
        if (!usesManualPath(lane)&&layout.towers.keySet().stream().anyMatch(k -> k.lane == lane)) {
            List<BlockPos> route = new ArrayList<>();
            Faction enemy = faction == Faction.GUARDIAN ? Faction.DESTRUCTION : Faction.GUARDIAN;
            BlockPos start = respawnPoint(faction);
            if (start != null) route.add(start);
            for (int tier = 3; tier >= 1; tier--) {
                BlockPos p = layout.towers.get(new TowerKey(faction,lane,tier));
                if (p != null && !route.contains(p)) route.add(p);
            }
            BlockPos corner = layout.corners.get(lane);
            if (corner != null && !route.contains(corner)) route.add(corner);
            for (int tier = 1; tier <= 3; tier++) {
                BlockPos p = layout.towers.get(new TowerKey(enemy,lane,tier));
                if (p != null && !route.contains(p)) route.add(p);
            }
            BlockPos end = layout.crystals.getOrDefault(enemy,respawnPoint(enemy));
            if (end != null && !route.contains(end)) route.add(end);
            if (route.size() >= 2) return route;
        }
        List<Map.Entry<Integer, BlockPos>> entries = new ArrayList<>(layout.paths.get(lane).entrySet());
        entries.sort(Map.Entry.comparingByKey());
        List<BlockPos> result = new ArrayList<>(entries.stream().map(Map.Entry::getValue).toList());
        // 若管理员还没有设置三条路线，使用两座基地之间的自动兜底路线，保证
        // 开局仍能看到兵线；设置了任意手工路径后仍完全按手工路径运行。
        if (result.size() < 2) result = new ArrayList<>(fallbackPath(lane));

        // 地图编辑器允许从任意一方开始放点，不能再假定 slot 1 永远是
        // 守护端。根据实际复活点判断哪一端更靠近当前阵营，必要时反转，
        // 确保双方首波都从自己的基地附近出发。
        BlockPos ownBase = respawnPoint(faction);
        BlockPos enemyBase = respawnPoint(faction == Faction.GUARDIAN
                ? Faction.DESTRUCTION : Faction.GUARDIAN);
        if (ownBase != null && enemyBase != null && result.size() >= 2) {
            // A legacy route can accidentally end back at its starting base. Keep the
            // outbound section through the opposite base rather than spawning both teams together.
            boolean firstOwnSide = horizontalDistanceSqr(result.getFirst(), ownBase)
                    < horizontalDistanceSqr(result.getFirst(), enemyBase);
            boolean lastOwnSide = horizontalDistanceSqr(result.getLast(), ownBase)
                    < horizontalDistanceSqr(result.getLast(), enemyBase);
            if (firstOwnSide == lastOwnSide && result.size() > 2) {
                BlockPos farBase = firstOwnSide ? enemyBase : ownBase;
                int end = 0;
                for (int i = 1; i < result.size(); i++) {
                    if (horizontalDistanceSqr(result.get(i), farBase) < horizontalDistanceSqr(result.get(end), farBase)) end = i;
                }
                if (end > 0 && end < result.size() - 1) result = new ArrayList<>(result.subList(0, end + 1));
            }
            double firstOwn = horizontalDistanceSqr(result.getFirst(), ownBase);
            double lastOwn = horizontalDistanceSqr(result.getLast(), ownBase);
            if (firstOwn > lastOwn) Collections.reverse(result);
        } else if (faction == Faction.DESTRUCTION) {
            // 旧存档没有基地点时保留原有约定，避免改变历史地图行为。
            Collections.reverse(result);
        }
        return result;
    }

    private static double horizontalDistanceSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static List<BlockPos> fallbackPath(Lane lane) {
        BlockPos source = respawnPoint(Faction.GUARDIAN);
        BlockPos target = respawnPoint(Faction.DESTRUCTION);
        if (source == null || target == null) return List.of();
        Vec3 start = Vec3.atCenterOf(source);
        Vec3 end = Vec3.atCenterOf(target);
        Vec3 direction = end.subtract(start).multiply(1, 0, 1);
        if (direction.horizontalDistanceSqr() < 1.0E-6) return List.of(source, target);
        direction = direction.normalize();
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        double offset = switch (lane) {
            case TOP -> 10.0;
            case MID -> 0.0;
            case BOTTOM -> -10.0;
        };
        Vec3 bend = side.scale(offset);
        return List.of(source, BlockPos.containing(start.add(direction.scale(0.30 * start.distanceTo(end))).add(bend)),
                BlockPos.containing(start.add(direction.scale(0.70 * start.distanceTo(end))).add(bend)), target);
    }

    public static boolean setPathPoint(Lane lane, int slot, BlockPos pos) {
        if (!canEdit() || lane == null || slot < 1 || slot > MAX_PATH_POINTS || pos == null) return false;
        return editLayout(next -> next.paths.get(lane).put(slot, pos.immutable()));
    }

    public static boolean clearPathPoint(Lane lane, int slot) {
        if (!canEdit() || lane == null || slot < 1 || slot > MAX_PATH_POINTS) return false;
        return editLayout(next -> next.paths.get(lane).remove(slot));
    }

    public static boolean setTowerPoint(Faction faction, Lane lane, int tier, BlockPos pos) {
        if (!canEdit() || faction == null || lane == null || pos == null) return false;
        return editLayout(next -> next.enabled.contains(lane) && next.tiers.contains(tier),
                next -> forgetPlatform(next, next.towers.put(new TowerKey(faction, lane, tier), pos.immutable()), pos));
    }

    public static boolean clearTowerPoint(Faction faction, Lane lane, int tier) {
        if (!canEdit() || faction == null || lane == null || tier < 1 || tier > TOWERS_PER_LANE) return false;
        return editLayout(next -> forgetPlatform(next, next.towers.remove(new TowerKey(faction, lane, tier)), null));
    }

    public static boolean setFountainPoint(Faction faction, BlockPos pos) {
        if (!canEdit() || faction == null || pos == null) return false;
        return editLayout(next -> next.fountains.put(faction, pos.immutable()));
    }

    public static boolean setCrystalPoint(Faction faction, BlockPos pos) {
        if (!canEdit() || faction == null || pos == null) return false;
        if (!editLayout(next -> forgetPlatform(next, next.crystals.put(faction, pos.immutable()),pos))) return false;
        syncBuildingLayout(server.overworld());
        return true;
    }

    public static boolean clearCrystalPoint(Faction faction) {
        if (!canEdit() || faction == null) return false;
        if (!editLayout(next -> forgetPlatform(next, next.crystals.remove(faction),null))) return false;
        syncBuildingLayout(server.overworld());
        return true;
    }

    private static void syncBuildingLayout(ServerLevel level) {
        Map<String, BuildingVisuals.Placement> desired = new LinkedHashMap<>();
        GameManager manager = GameManager.get();
        if (manager == null || manager.getPhase() == GameManager.Phase.WAITING) {
            layout.crystals.forEach((faction, pos) -> desired.put("crystal_" + faction.getName(),
                    new BuildingVisuals.Placement(Vec3.atBottomCenterOf(pos), faction, 0)));
            layout.towers.forEach((key, pos) -> desired.put("tower_" + key.faction.getName() + "_" + key.lane.id + "_" + key.tier,
                    new BuildingVisuals.Placement(Vec3.atBottomCenterOf(pos), key.faction, key.tier)));
        }
        BuildingVisuals.syncLayout(level, desired);
    }


    private static void ensureTowerVisual(ServerLevel level, Mob tower, UnitState state) {
        if (state.visualEntity != null && !state.visualEntity.isRemoved()) return;
        if (state.visual != null && level.getEntity(state.visual) instanceof net.minecraft.world.entity.Display.ItemDisplay display) {
            display.setPos(tower.getX(), tower.getY(), tower.getZ());
            state.visualEntity=display;
            return;
        }
        var display = state.kind==Kind.CRYSTAL?BuildingVisuals.crystal(level,tower.position(),state.faction)
                :BuildingVisuals.tower(level, tower.position(), state.faction, state.towerTier);
        if (display != null) {
            display.addTag(LANE_ENTITY_TAG);
            state.visual = display.getUUID();
            state.visualEntity = display;
        }
    }

    private static void removeVisual(ServerLevel level, UnitState state) {
        warnTowerTarget(state, null);
        if (state.visualEntity != null) state.visualEntity.discard();
        state.visualEntity = null;
        if (state.visual != null && level.getEntity(state.visual) instanceof Entity display) display.discard();
        state.visual = null;
    }

    public static boolean clearFountainPoint(Faction faction) {
        if (!canEdit() || faction == null) return false;
        return editLayout(next -> next.fountains.remove(faction));
    }

    public static BlockPos fountainPoint(Faction faction) {
        return layout.fountains.get(faction);
    }

    public static BlockPos crystalPoint(Faction faction) {
        return layout.crystals.get(faction);
    }

    /** 返回阵营复活点；未设置泉水时使用水晶点作为安全兜底。 */
    public static BlockPos respawnPoint(Faction faction) {
        if (faction == null) return null;
        BlockPos fountain = layout.fountains.get(faction);
        return fountain != null ? fountain : layout.crystals.get(faction);
    }

    /** 开局把已分配阵营的玩家送到各自泉水，并清除上一局残余速度。 */
    public static void teleportPlayersToBases(ServerLevel level,
            java.util.Collection<ServerPlayer> players) {
        if (level == null || players == null) return;
        GameManager manager = GameManager.get();
        for (ServerPlayer player : players) {
            if (player == null || manager == null) continue;
            BlockPos base = respawnPoint(manager.factionOf(player));
            if (base == null) continue;
            Vec3 target = Vec3.atBottomCenterOf(base).add(0, 1.0, 0);
            player.teleportTo(level, target.x, target.y, target.z, java.util.Set.of(),
                    player.getYRot(), player.getXRot(), true);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }
    }

    /** 地图编辑器只读查询。 */
    public static BlockPos pathPoint(Lane lane, int slot) {
        Map<Integer, BlockPos> points = layout.paths.get(lane);
        return points == null ? null : points.get(slot);
    }

    /** 地图编辑器只读查询。 */
    public static BlockPos towerPoint(Faction faction, Lane lane, int tier) {
        return layout.towers.get(new TowerKey(faction, lane, tier));
    }

    public static List<MinimapUnit> minimapUnits(ServerLevel level) {
        List<MinimapUnit> result = new ArrayList<>(minimapFacilities());
        for (Map.Entry<UUID, UnitState> entry : UNITS.entrySet()) {
            UnitState state = entry.getValue();
            // Buildings come from the layout, so unloaded chunks cannot hide or duplicate them.
            if (state.kind == Kind.TOWER || state.kind == Kind.CRYSTAL) continue;
            Entity entity = level.getEntity(entry.getKey());
            if (entity != null && entity.isAlive()) {
                int kind = switch (state.kind) {
                    case MELEE -> 2;
                    case RANGED -> 3;
                    case TOWER -> 4;
                    case CRYSTAL -> 5;
                    case MAGIC -> 6;
                    case SIEGE -> 7;
                };
                result.add(new MinimapUnit(entity.getX(), entity.getZ(), state.faction, kind));
            }
        }
        return List.copyOf(result);
    }

    public static List<MinimapUnit> minimapFacilities() {
        List<MinimapUnit> result = new ArrayList<>();
        layout.towers.forEach((key, pos) -> {
            if (layout.enabled.contains(key.lane) && layout.tiers.contains(key.tier)
                    && !DESTROYED_TOWERS.contains(towerId(key.faction, key.lane, key.tier))) {
                result.add(new MinimapUnit(pos.getX() + .5, pos.getZ() + .5, key.faction, 4));
            }
        });
        layout.crystals.forEach((faction, pos) -> result.add(
                new MinimapUnit(pos.getX() + .5, pos.getZ() + .5, faction, 5)));
        layout.fountains.forEach((faction, pos) -> result.add(new MinimapUnit(
                pos.getX() + .5, pos.getZ() + .5, faction, NetworkSetup.MinimapMarker.FOUNTAIN)));
        return List.copyOf(result);
    }

    /** 返回兵线真正采用的路径（守护端到毁灭端），供战术地图绘制。 */
    public static List<MinimapPathPoint> minimapPaths() {
        List<MinimapPathPoint> result = new ArrayList<>();
        for (Lane lane : Lane.values()) {
            List<BlockPos> path = orderedPath(lane, Faction.GUARDIAN);
            for (int order = 0; order < path.size(); order++) {
                BlockPos point = path.get(order);
                result.add(new MinimapPathPoint(lane.ordinal(), order,
                        point.getX() + 0.5, point.getZ() + 0.5));
            }
        }
        return List.copyOf(result);
    }

    public static List<LayoutLine> layoutLines() {
        List<LayoutLine> lines = new ArrayList<>();
        layout.crystals.forEach((faction, pos) -> lines.add(new LayoutLine("crystal " + faction.getName()
                + " = " + pos.getX() + " " + pos.getY() + " " + pos.getZ())));
        for (Lane lane : Lane.values()) {
            List<Map.Entry<Integer, BlockPos>> points = new ArrayList<>(layout.paths.get(lane).entrySet());
            points.sort(Map.Entry.comparingByKey());
            for (Map.Entry<Integer, BlockPos> point : points) {
                BlockPos p = point.getValue();
                lines.add(new LayoutLine("path " + lane.id + " " + point.getKey() + " = "
                        + p.getX() + " " + p.getY() + " " + p.getZ()));
            }
        }
        layout.towers.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<TowerKey, BlockPos> e) -> e.getKey().lane.id)
                        .thenComparing(e -> e.getKey().faction.getName()).thenComparingInt(e -> e.getKey().tier))
                .forEach(entry -> {
                    TowerKey key = entry.getKey();
                    BlockPos p = entry.getValue();
                    lines.add(new LayoutLine("tower " + key.faction.getName() + " " + key.lane.id + " "
                            + key.tier + " = " + p.getX() + " " + p.getY() + " " + p.getZ()));
                });
        layout.fountains.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    BlockPos p = entry.getValue();
                    lines.add(new LayoutLine("fountain " + entry.getKey().getName() + " = "
                            + p.getX() + " " + p.getY() + " " + p.getZ()));
                });
        return List.copyOf(lines);
    }

    private static boolean canEdit() {
        GameManager manager = GameManager.get();
        return server != null && (manager == null || manager.getPhase() == GameManager.Phase.WAITING);
    }

    private static void clearRuntime(ServerLevel level) {
        DESTROYED_TOWERS.clear();
        for (UnitState state : UNITS.values()) {
            if (state.entity != null) state.entity.discard();
            removeVisual(level, state);
        }
        List<Entity> stale = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null && !entity.isRemoved() && entity.entityTags().contains(LANE_ENTITY_TAG)) stale.add(entity);
        }
        stale.forEach(Entity::discard);
        UNITS.clear();
        nextWaveAt = Long.MAX_VALUE;
        layoutActive = false;
    }

    private static void broadcast(Component message) {
        broadcast(MatchMessages.Tone.INFO, message);
    }

    private static void broadcast(MatchMessages.Tone tone, Component message) {
        if (server == null) return;
        GameManager manager = GameManager.get();
        for (ServerPlayer player : server.overworld().players()) {
            if (manager == null || !manager.isQueuedMatch() || manager.isParticipant(player.getUUID()))
                MatchMessages.send(player, tone, message);
        }
    }

    private static boolean editLayout(java.util.function.Consumer<Layout> edit) {
        return editLayout(next -> true, edit);
    }

    private static boolean editLayout(java.util.function.Predicate<Layout> allowed, java.util.function.Consumer<Layout> edit) {
        Layout next = ArenaConfigFile.readForEdit(layoutPath(), root -> decodeLayout(root, false), layout::copy).orElse(null);
        if (next == null || !allowed.test(next)) return false;
        edit.accept(next);
        if (!ArenaConfigFile.save(layoutPath(), layoutJson(next))) return false;
        activateLayout(next);
        return true;
    }

    private static JsonObject layoutJson(Layout layout) {
        JsonObject root = new JsonObject();
        JsonArray enabled = new JsonArray();
        layout.enabled.forEach(lane -> enabled.add(lane.id));
        root.add("enabledLanes", enabled);
        JsonArray tiers = new JsonArray();
        layout.tiers.forEach(tiers::add);
        root.add("towerTiers", tiers);
        JsonObject paths = new JsonObject();
        for (Lane lane : Lane.values()) {
            JsonArray array = new JsonArray();
            layout.paths.get(lane).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                JsonObject point = positionJson(entry.getValue());
                point.addProperty("slot", entry.getKey());
                array.add(point);
            });
            paths.add(lane.id, array);
        }
        root.add("paths", paths);
        if (!layout.manual.isEmpty()) {
            JsonArray manual = new JsonArray();
            layout.manual.forEach(lane -> manual.add(lane.id));
            root.add("manualPaths", manual);
        }
        JsonObject corners = new JsonObject();
        layout.corners.forEach((lane, pos) -> corners.add(lane.id(), positionJson(pos)));
        root.add("laneCorners", corners);
        JsonArray towers = new JsonArray();
        for (Map.Entry<TowerKey, BlockPos> entry : layout.towers.entrySet()) {
            JsonObject tower = positionJson(entry.getValue());
            tower.addProperty("faction", entry.getKey().faction.getName());
            tower.addProperty("lane", entry.getKey().lane.id);
            tower.addProperty("tier", entry.getKey().tier);
            writePlatform(layout, tower, entry.getValue());
            towers.add(tower);
        }
        root.add("towers", towers);
        JsonArray fountains = new JsonArray();
        for (Map.Entry<Faction, BlockPos> entry : layout.fountains.entrySet()) {
            JsonObject fountain = positionJson(entry.getValue());
            fountain.addProperty("faction", entry.getKey().getName());
            fountains.add(fountain);
        }
        root.add("fountains", fountains);
        JsonArray crystals = new JsonArray();
        layout.crystals.forEach((faction, pos) -> {
            JsonObject crystal = positionJson(pos);
            crystal.addProperty("faction", faction.getName());
            writePlatform(layout, crystal, pos);
            crystals.add(crystal);
        });
        root.add("crystals", crystals);
        return root;
    }

    static Layout decodeLayout(JsonObject root, boolean duel) {
        Layout layout = new Layout();
        readEnabledLanes(layout, root);
        JsonArray manual = ArenaConfigFile.array(root, "manualPaths");
        for (JsonElement value : manual) {
            Lane lane = Lane.byId(value.getAsString());
            if (lane != null && layout.enabled.contains(lane)) layout.manual.add(lane);
        }
        JsonObject corners = ArenaConfigFile.object(root, "laneCorners");
        for (Lane lane : List.of(Lane.TOP, Lane.BOTTOM)) {
            if (corners.has(lane.id())) layout.corners.put(lane, readPosition(corners.getAsJsonObject(lane.id())));
        }
        JsonArray crystals = ArenaConfigFile.array(root, "crystals");
        for (JsonElement element : crystals) {
            JsonObject crystal = element.getAsJsonObject();
            Faction faction = parseFaction(crystal.get("faction").getAsString());
            if (faction != null) {
                BlockPos pos = readPosition(crystal);
                layout.crystals.put(faction, pos);
                if (duel) validPosition("crystals", pos);
                readPlatform(layout, crystal, pos);
            }
        }
        JsonObject paths = ArenaConfigFile.object(root, "paths");
        for (Lane lane : Lane.values()) {
            JsonArray array = paths.getAsJsonArray(lane.id);
            if (array == null) continue;
            for (JsonElement element : array) {
                JsonObject point = element.getAsJsonObject();
                int slot = point.get("slot").getAsInt();
                if (slot >= 1 && slot <= MAX_PATH_POINTS) layout.paths.get(lane).put(slot, readPosition(point));
            }
        }
        JsonArray towers = ArenaConfigFile.array(root, "towers");
        for (JsonElement element : towers) {
            JsonObject tower = element.getAsJsonObject();
            Faction faction = parseFaction(tower.get("faction").getAsString());
            Lane lane = Lane.byId(tower.get("lane").getAsString());
            int tier = tower.get("tier").getAsInt();
            if (faction != null && layout.enabled.contains(lane) && layout.tiers.contains(tier)) {
                BlockPos pos = readPosition(tower);
                layout.towers.put(new TowerKey(faction, lane, tier), pos);
                if (duel) validPosition("towers", pos);
                readPlatform(layout, tower, pos);
            }
        }
        JsonArray fountains = ArenaConfigFile.array(root, "fountains");
        for (JsonElement element : fountains) {
            JsonObject fountain = element.getAsJsonObject();
            Faction faction = parseFaction(fountain.get("faction").getAsString());
            if (faction != null) {
                BlockPos point = readPosition(fountain);
                if (duel) validPosition("fountains", point);
                layout.fountains.put(faction, point);
            }
        }
        if (duel) {
            if (!layout.enabled.equals(java.util.Set.of(Lane.MID)) || ArenaConfigFile.array(root, "enabledLanes").size() != 1)
                throw new IllegalArgumentException("enabledLanes must contain only mid");
            if (layout.tiers.size() < 2 || root.has("towerTiers") && ArenaConfigFile.array(root, "towerTiers").size() != layout.tiers.size())
                throw new IllegalArgumentException("towerTiers requires distinct tiers from 1 to 3");
            int expectedTowers = layout.tiers.size() * 2;
            if (layout.towers.size() != expectedTowers || ArenaConfigFile.array(root, "towers").size() != expectedTowers)
                throw new IllegalArgumentException("towers must cover each side and tier once");
            if (layout.crystals.size() != 2 || layout.fountains.size() != 2 || crystals.size() != 2 || fountains.size() != 2)
                throw new IllegalArgumentException("crystals and fountains must cover both teams");
        }
        return layout;
    }

    private static void validPosition(String field, BlockPos p) {
        if (Math.abs((long)p.getX()) > 29_000_000 || Math.abs((long)p.getZ()) > 29_000_000
                || p.getY() < -63 || p.getY() > 318)
            throw new IllegalArgumentException(field + " position outside world");
    }

    public static Faction parseFaction(String id) {
        return Faction.byId(id);
    }

    private static void readPlatform(Layout layout, JsonObject entry, BlockPos pos){
        if(!entry.has("minionPlatformRadius"))return;
        double radius=entry.get("minionPlatformRadius").getAsDouble();
        if(Double.isFinite(radius)&&radius>0&&radius<=10)layout.radii.put(pos,radius);
    }

    private static void writePlatform(Layout layout, JsonObject entry, BlockPos pos){
        Double radius=layout.radii.get(pos);
        if(radius!=null)entry.addProperty("minionPlatformRadius",radius);
    }

    private static void forgetPlatform(Layout layout, BlockPos old, BlockPos next){
        if(old!=null&&!old.equals(next))layout.radii.remove(old);
    }

    private static void readEnabledLanes(Layout layout, JsonObject root) {
        layout.tiers.clear();
        layout.tiers.addAll(List.of(1,2,3));
        JsonArray tiers = root.getAsJsonArray("towerTiers");
        if (tiers != null) {
            java.util.SortedSet<Integer> requested = new java.util.TreeSet<>();
            for (JsonElement entry : tiers) {
                int tier = entry.getAsInt();
                if (tier >= 1 && tier <= TOWERS_PER_LANE) requested.add(tier);
            }
            if (!requested.isEmpty()) {
                layout.tiers.clear();
                layout.tiers.addAll(requested);
            }
        }
        layout.enabled.clear();
        layout.enabled.addAll(java.util.EnumSet.allOf(Lane.class));
        JsonArray enabled = root.getAsJsonArray("enabledLanes");
        if (enabled == null) return;
        java.util.EnumSet<Lane> requested = java.util.EnumSet.noneOf(Lane.class);
        for (JsonElement entry : enabled) {
            Lane lane = Lane.byId(entry.getAsString());
            if (lane != null) requested.add(lane);
        }
        if (!requested.isEmpty()) {
            layout.enabled.clear();
            layout.enabled.addAll(requested);
        }
    }

    private static JsonObject positionJson(BlockPos pos) {
        JsonObject json = new JsonObject();
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        return json;
    }

    private static BlockPos readPosition(JsonObject json) {
        return new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
    }

    private static Path layoutPath() {
        return MatchArena.configPath(server, "myriad_lane_layout.json");
    }

    public static JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("start",activeMatchStart); root.addProperty("nextWave",nextWaveAt);
        root.addProperty("wave",waveNumber); root.addProperty("active",layoutActive);
        root.add("destroyedTowers",MatchProgress.JSON.toJsonTree(DESTROYED_TOWERS));
        JsonArray units=new JsonArray();
        for(UnitState state:UNITS.values()) {
            if(state.entity==null||!state.entity.isAlive())continue;
            JsonObject row=MatchProgress.JSON.toJsonTree(state).getAsJsonObject();
            row.add("position",MatchProgress.JSON.toJsonTree(state.entity.position()));units.add(row);
        }
        root.add("units",units);return root;
    }
    public static void restore(ServerLevel level,JsonObject root) {
        clearRuntime(level);runtimeNeedsCleanup=false;
        if(root.has("destroyedTowers"))for(var id:root.getAsJsonArray("destroyedTowers")){
            String key=id.getAsString();int slash=key.indexOf('/');
            Faction faction=slash<0?null:Faction.byId(key.substring(0,slash));
            DESTROYED_TOWERS.add(faction==null?key:faction.getName()+key.substring(slash));
        }
        activeMatchStart=root.get("start").getAsLong();nextWaveAt=root.get("nextWave").getAsLong();
        waveNumber=root.get("wave").getAsInt();layoutActive=root.get("active").getAsBoolean();
        for(JsonElement value:root.getAsJsonArray("units")) {
            UnitState saved=MatchProgress.JSON.fromJson(value,UnitState.class);
            Vec3 position=MatchProgress.JSON.fromJson(value.getAsJsonObject().get("position"),Vec3.class);
            java.util.Set<UUID> before=java.util.Set.copyOf(UNITS.keySet());
            if(saved.kind==Kind.CRYSTAL)spawnCrystal(level,saved.faction,BlockPos.containing(position));
            else if(saved.tower())spawnTower(level,new TowerKey(saved.faction,saved.lane,saved.towerTier),BlockPos.containing(position));
            else spawnMinion(level,saved.faction,saved.lane,orderedPath(saved.lane,saved.faction),saved.kind,0);
            for(var entry:UNITS.entrySet())if(!before.contains(entry.getKey())) {
                UnitState live=entry.getValue();live.maxHealth=saved.maxHealth;live.health=saved.health;
                if (java.util.Objects.equals(live.path,saved.path)) live.waypointIndex=saved.waypointIndex;
                live.lastAttackTick=saved.lastAttackTick;live.attackRecovery=saved.attackRecovery;live.entity.setPos(position.x,position.y,position.z);
                CombatChunks.prepare(level,BlockPos.containing(position));updateName(live.entity,live);
            }
        }
        // Upgrade pre-siege checkpoints: configured towers absent from an active snapshot were destroyed.
        if(!root.has("destroyedTowers") && layoutActive && activeMatchStart>=0){
            for(TowerKey key:layout.towers.keySet()){
                boolean alive=UNITS.values().stream().anyMatch(s->s.tower()&&s.faction==key.faction&&s.lane==key.lane&&s.towerTier==key.tier);
                if(!alive)DESTROYED_TOWERS.add(towerId(key.faction,key.lane,key.tier));
            }
        }
        // Old checkpoints contain only the decorative crystal; add its combat body on upgrade.
        if(root.get("active").getAsBoolean())for(var entry:layout.crystals.entrySet())
            if(UNITS.values().stream().noneMatch(s->s.kind==Kind.CRYSTAL&&s.faction==entry.getKey()))spawnCrystal(level,entry.getKey(),entry.getValue());
    }

    private static void spawnCrystal(ServerLevel level,Faction faction,BlockPos pos){
        CombatChunks.prepare(level,pos);
        var entity=com.myriad.registry.ModEntities.BASE_CRYSTAL.get().create(level,EntitySpawnReason.MOB_SUMMONED);
        if(entity==null)return;
        entity.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
        configureProxyHealth(entity,21000.0F);
        UnitState state=new UnitState(Kind.CRYSTAL,faction,Lane.MID,4,null,
                21000.0F);state.entity=entity;
        entity.addTag(LANE_ENTITY_TAG);
        if(!level.addFreshEntity(ModEntityTracker.markGenerated(entity)))return;
        UNITS.put(entity.getUUID(),state);updateName(entity,state);ensureTowerVisual(level,entity,state);
    }
}
