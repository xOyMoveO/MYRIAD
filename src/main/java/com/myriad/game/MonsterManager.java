package com.myriad.game;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.player.Player;
import com.myriad.character.ability.ActionTime;
import com.myriad.character.skills.keheng.KehengTimeFields;
import com.myriad.combat.NpcCombat;
import com.myriad.network.RiftCrabMarker;
import com.myriad.player.PlayerData;
import com.myriad.player.PlayerDataProvider;

/**
 * 野怪系统（服务端）：
 * 寻敌半径 = 召唤点 10 格；攻击者超出 10 格后启动仇恨值系统（每秒 -20，总仇恨每野怪不同），
 * 仇恨归零后不断回血并返回召唤点；击杀后召唤点上方显示刷新倒计时（ArmorStand）。
 */
public final class MonsterManager {
    private MonsterManager() {}

    public static com.google.gson.JsonObject snapshot() {
        var root=new com.google.gson.JsonObject();
        root.addProperty("start",activeMatchStart);root.addProperty("nextBoss",nextBossSpawnAt);
        root.add("generations",MatchProgress.JSON.toJsonTree(SPAWN_GENERATIONS));
        root.add("initial",MatchProgress.JSON.toJsonTree(INITIAL_SPAWNED_POINTS));
        var alive=new com.google.gson.JsonArray();
        for(State state:STATES.values())if(state.entity!=null&&state.entity.isAlive()) {
            var row=MatchProgress.JSON.toJsonTree(state).getAsJsonObject();
            row.add("position",MatchProgress.JSON.toJsonTree(state.entity.position()));alive.add(row);
        }
        root.add("alive",alive);
        var respawns=new com.google.gson.JsonArray();
        RESPAWN.forEach((pos,pending)->{var row=MatchProgress.JSON.toJsonTree(pending).getAsJsonObject();
            row.add("position",MatchProgress.JSON.toJsonTree(pos));respawns.add(row);});
        root.add("respawns",respawns);return root;
    }
    public static void restore(ServerLevel level,com.google.gson.JsonObject root) {
        clearRuntime(level);activeMatchStart=root.get("start").getAsLong();nextBossSpawnAt=root.get("nextBoss").getAsLong();
        SPAWN_GENERATIONS.clear();SPAWN_GENERATIONS.putAll(MatchProgress.JSON.fromJson(root.get("generations"),
                new com.google.gson.reflect.TypeToken<Map<String,Integer>>(){}.getType()));
        INITIAL_SPAWNED_POINTS.clear();INITIAL_SPAWNED_POINTS.addAll(MatchProgress.JSON.fromJson(root.get("initial"),
                new com.google.gson.reflect.TypeToken<Set<String>>(){}.getType()));
        for(var row:root.getAsJsonArray("alive")) {
            State saved=MatchProgress.JSON.fromJson(row,State.class);
            Vec3 position=MatchProgress.JSON.fromJson(row.getAsJsonObject().get("position"),Vec3.class);
            Set<UUID> before=Set.copyOf(STATES.keySet());
            spawnInternal(level,saved.spawnPos,saved.type,saved.spawnPointId,saved.randomBoss,saved.respawnGeneration);
            for(var entry:STATES.entrySet())if(!before.contains(entry.getKey())) {
                State live=entry.getValue();live.modHealth=live.modMaxHealth*Math.max(0,Math.min(1,saved.modHealth/saved.modMaxHealth));
                live.entity.setPos(position.x,position.y,position.z);live.returning=position.distanceToSqr(Vec3.atBottomCenterOf(saved.spawnPos))>4;
                live.lastAttackTick=saved.lastAttackTick;live.attackRecovery=saved.attackRecovery;CombatChunks.prepare(level,BlockPos.containing(position));
            }
        }
        for(var row:root.getAsJsonArray("respawns")) {
            BlockPos pos=MatchProgress.JSON.fromJson(row.getAsJsonObject().get("position"),BlockPos.class);
            RESPAWN.put(pos,MatchProgress.JSON.fromJson(row,PendingRespawn.class));
        }
    }

    static double growthMultiplier(int generation) { return 1.0 + Math.max(0,generation-2)*.03; }
    private static double initialHealth(MonsterType type,int generation) { return type.getHealth()*growthMultiplier(generation); }
    static final long FIRST_BOSS_SPAWN_TICKS = 8L * 60L * 20L;
    static final long BOSS_SPAWN_INTERVAL_TICKS = 5L * 60L * 20L;
    static final List<MonsterType> LARGE_BOSSES = List.of(
            MonsterType.AZURE_HORN_GUARDIAN, MonsterType.VERMILION_FLAME_TYRANT,
            MonsterType.GALE_ROC_MATRIARCH, MonsterType.OBSIDIAN_TORTOISE_WARDEN
    );
    static final long BOSS_BUFF_TICKS = 90L * 20L;

    /** 写入存档配置文件的刷新点。type=null 表示大型随机刷新点。 */
    record SpawnPoint(String id, BlockPos pos, MonsterType type) {
        boolean largeRandom() { return type == null; }
    }

    public record SpawnPointInfo(String id, BlockPos pos, String typeId, boolean largeRandom) {
        public boolean boss() {
            MonsterType type = MonsterType.byId(typeId);
            return largeRandom || (type != null && type.isLargeBoss());
        }
    }

    private record PendingRespawn(MonsterType type, long at, String pointId, Set<Faction> seenBy) {
        private PendingRespawn {
            // Saves written before team knowledge existed may only reveal this timer upon observation.
            seenBy = seenBy == null ? new HashSet<>() : new HashSet<>(seenBy);
        }

        boolean isFixedCrab() {
            return pointId != null && type == MonsterType.CHERRY_RIFT_CRAB;
        }
    }

    private record CherryStrike(UUID target, CherryRiftAttackTiming timing, ActionTime.Progress progress) {}

    /** 野怪状态 */
    private static final class State {
        transient Mob entity;
        final transient NpcNavigation.Route navigation = new NpcNavigation.Route();
        long nextTargetScan;
        BlockPos spawnPos;
        MonsterType type;
        /** 仇恨表：玩家 UUID -> 累计造成伤害（仇恨 = 伤害量） */
        Map<UUID, Double> damageTable = new HashMap<>();
        /** 当前目标（仇恨最高玩家） */
        UUID target;
        long lastHatredTick;
        boolean returning;
        double regenPerTick;
        long lastAttackTick;
        double attackRecovery = 20;
        transient long lastRecoveryTick = Long.MIN_VALUE;
        transient CherryStrike cherryStrike;
        String spawnPointId;
        boolean randomBoss;
        int respawnGeneration;
        /** 独立模组生命池，不受原版 MAX_HEALTH 1024 上限影响。 */
        float modHealth;
        final float modMaxHealth;

        State(BlockPos spawnPos, MonsterType type, String spawnPointId, boolean randomBoss,
                int respawnGeneration) {
            this.spawnPos = spawnPos;
            this.type = type;
            this.spawnPointId = spawnPointId;
            this.randomBoss = randomBoss;
            this.respawnGeneration = respawnGeneration;
            this.lastHatredTick = 0;
            this.returning = false;
            double scaledHealth = initialHealth(type,respawnGeneration);
            this.regenPerTick = scaledHealth * 0.001; // 每秒回 2% 最大生命
            this.lastAttackTick = 0;
            this.modMaxHealth = (float) scaledHealth;
            this.modHealth = this.modMaxHealth;
        }

        /** 当前仇恨值（最高玩家的累计伤害） */
        double currentHatred() {
            double max = 0;
            for (double d : damageTable.values()) {
                max = Math.max(max, d);
            }
            return max;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 待重生（位置 -> 类型、时间、配置点 ID）。 */
    private static final Map<BlockPos, PendingRespawn> RESPAWN = new HashMap<>();
    /** 重生倒计时显示（位置 -> ArmorStand） */
    private static final Map<BlockPos, ArmorStand> RESPAWN_DISPLAY = new HashMap<>();
    private static final Map<String, SpawnPoint> SPAWN_POINTS = new LinkedHashMap<>();
    /** 首刷为 0；第 3 次重生起，每次战斗属性增加基础值的 3%。 */
    private static final Map<String, Integer> SPAWN_GENERATIONS = new HashMap<>();
    /** 本局已经完成首次生成的普通/中型刷新点。 */
    private static final Set<String> INITIAL_SPAWNED_POINTS = new HashSet<>();
    private static net.minecraft.server.MinecraftServer server;
    private static long activeMatchStart = Long.MIN_VALUE;
    private static long nextBossSpawnAt = Long.MAX_VALUE;

    public static MonsterType typeOf(LivingEntity entity) {
        State s = STATES.get(entity.getUUID());
        return s == null ? null : s.type;
    }

    public static boolean isMonster(LivingEntity entity) {
        return STATES.containsKey(entity.getUUID());
    }

    /** 生成野怪：记录召唤点与状态 */
    public static void spawn(ServerLevel level, BlockPos pos, MonsterType type) {
        if (TrainingGround.isTraining(level)) return;
        spawnInternal(level, pos, type, null, false, 0);
    }

    private static boolean spawnConfigured(ServerLevel level, SpawnPoint point, MonsterType type,
            boolean randomBoss) {
        int generation = SPAWN_GENERATIONS.getOrDefault(point.id(), 0);
        if (!spawnInternal(level, point.pos(), type, point.id(), randomBoss, generation)) return false;
        SPAWN_GENERATIONS.put(point.id(), generation + 1);
        return true;
    }

    private static boolean spawnInternal(ServerLevel level, BlockPos pos, MonsterType type,
            String pointId, boolean randomBoss, int generation) {
        CombatChunks.prepare(level, pos);
        // 26.2 rejects hostile entity creation itself on peaceful, before checkDespawn runs.
        net.minecraft.world.entity.Entity e = type.getEntityType().create(level,
                new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.MOB_SUMMONED, true));
        if (!(e instanceof Mob mob)) {
            return false;
        }
        if (mob instanceof CherryRiftMonsterEntity cherry) cherry.configure(type);
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // 原版生命仅作为存活代理；真实生命由 State.modHealth 管理。
        double scaledHealth = initialHealth(type,generation);
        double growth = growthMultiplier(generation);
        float proxyHealth = (float) Math.min(1024.0, scaledHealth);
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(proxyHealth);
        }
        AttributeInstance atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(type.getAttack()*growth);
        }
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null && type.isLargeBoss() && type.cherryModel() == null) scale.setBaseValue(1.35);
        AttributeInstance physicalDefense = mob.getAttribute(com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE);
        if (physicalDefense != null) physicalDefense.setBaseValue(type.getPhysicalDefense()*growth);
        AttributeInstance magicDefense = mob.getAttribute(com.myriad.registry.ModAttributes.MAGIC_RESISTANCE);
        if (magicDefense != null) magicDefense.setBaseValue(type.getMagicDefense()*growth);
        mob.setHealth(proxyHealth);
        mob.setCustomName(Component.translatable(type.getNameKey()));
        mob.setCustomNameVisible(true);
        // 禁用原版 AI（不乱跑、不逃跑），移动由本模组手动控制
        mob.setNoAi(true);
        if (ignoresSunlightFire(type)) mob.clearFire();
        mob.setPersistenceRequired();
        if (!level.addFreshEntity(ModEntityTracker.markGenerated(mob))) {
            com.myriad.Myriad.LOGGER.error("[monster] failed to spawn {} at {}", type, pos);
            return false;
        }
        State state = new State(pos, type, pointId, randomBoss, generation);
        state.entity = mob;
        STATES.put(mob.getUUID(), state);
        // 移除重生倒计时显示
        ArmorStand stand = RESPAWN_DISPLAY.remove(pos);
        if (stand != null) {
            stand.discard();
        }
        return true;
    }

    /** 每 tick：野怪仇恨/寻敌/回血回点 + 重生 + 倒计时显示刷新 */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        tickConfiguredSpawns(level, now);
        // 存活野怪
        for (Map.Entry<UUID, State> entry : new HashMap<>(STATES).entrySet()) {
            net.minecraft.world.entity.Entity e = level.getEntity(entry.getKey());
            if (e == null) e = entry.getValue().entity;
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                STATES.remove(entry.getKey());
                continue;
            }
            State s = entry.getValue();
            if (now % 20 == 0) CombatChunks.hold(level, mob.blockPosition());
            // 溺尸与幻翼只是野怪外观载体，不继承原版日照燃烧弱点。
            if (ignoresSunlightFire(s.type)) {
                mob.clearFire();
            }
            tickMonster(level, mob, s, now);
        }
        // 重生
        if (now % 20 == 0) {
            java.util.Iterator<Map.Entry<BlockPos, PendingRespawn>> it = RESPAWN.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, PendingRespawn> entry = it.next();
                PendingRespawn pending = entry.getValue();
                if (now >= pending.at()) {
                    SpawnPoint point = pending.pointId() == null ? null : SPAWN_POINTS.get(pending.pointId());
                    if (point != null) {
                        boolean spawned = spawnConfigured(level, point, pending.type(), false);
                        if (!spawned && pending.isFixedCrab()) continue;
                    } else {
                        spawn(level, entry.getKey(), pending.type());
                    }
                    it.remove();
                }
            }
        }
        // 倒计时显示刷新（每秒）
        if (now % 20 == 0) {
            for (Map.Entry<BlockPos, PendingRespawn> entry : RESPAWN.entrySet()) {
                var pending = entry.getValue();
                if (isMatchCrab(pending)) {
                    // Reaching the visible camp timer also reveals the respawn to that team.
                    observeCrab(level, pending, Vec3.atBottomCenterOf(entry.getKey()).add(0, 1.5, 0));
                }
                long at = pending.at();
                int remain = (int) Math.max(0, (at - now + 19) / 20);
                ArmorStand stand = RESPAWN_DISPLAY.get(entry.getKey());
                if (stand == null || !stand.isAlive()) {
                    stand = createRespawnStand(level, entry.getKey(), pending);
                    if (stand == null) continue;
                    RESPAWN_DISPLAY.put(entry.getKey(), stand);
                }
                stand.setCustomName(Component.literal("刷新: " + remain + "s"));
            }
        }
    }

    /** 使用会受日照影响的原版实体作为载体，但玩法上不应在白天自燃的野怪。 */
    public static boolean ignoresSunlightFire(MonsterType type) {
        return type == MonsterType.SPRING_REEF_GUARDIAN
                || type == MonsterType.GALE_ROC_MATRIARCH;
    }

    /** 按当前对局时间驱动地图配置刷新点。 */
    private static void tickConfiguredSpawns(ServerLevel level, long now) {
        GameManager manager = GameManager.get();
        long matchStart = manager == null ? -1 : manager.getMatchStartTime();
        if (matchStart < 0) {
            activeMatchStart = Long.MIN_VALUE;
            return;
        }
        if (activeMatchStart != matchStart) {
            resetForMatch(level, matchStart);
        }
        long elapsed = Math.max(0, now - activeMatchStart);
        spawnInitialConfiguredMonsters(level, elapsed);
        if (now >= nextBossSpawnAt && trySpawnRandomBoss(level)) {
            nextBossSpawnAt = now + BOSS_SPAWN_INTERVAL_TICKS;
        }
    }

    /** 首次生成时间按营地类型区分。 */
    private static void spawnInitialConfiguredMonsters(ServerLevel level, long elapsed) {
        for (SpawnPoint point : SPAWN_POINTS.values()) {
            MonsterType type = point.type();
            if (point.largeRandom() || type == null || INITIAL_SPAWNED_POINTS.contains(point.id())) continue;
            long firstSpawnAt = firstSpawnDelay(type);
            if (elapsed < firstSpawnAt || !isPointFree(point.id())) continue;
            if (spawnConfigured(level, point, type, false)) INITIAL_SPAWNED_POINTS.add(point.id());
        }
    }

    private static long firstSpawnDelay(MonsterType type) {
        return GameManager.combatTempo().firstSpawnTicks(type);
    }

    static List<RiftCrabMarker> crabMapMarkers(ServerLevel level, Faction team) {
        GameManager manager = GameManager.get();
        if (team == null || manager == null || manager.getPhase() != GameManager.Phase.PLAYING
                || activeMatchStart != manager.getMatchStartTime()) return List.of();
        List<RiftCrabMarker> markers = new ArrayList<>();
        for (SpawnPoint point : SPAWN_POINTS.values()) {
            if (point.type != MonsterType.CHERRY_RIFT_CRAB) continue;
            float x = point.pos.getX() + 0.5F, z = point.pos.getZ() + 0.5F;
            if (!MapSystem.contains(x, z)) continue;
            long spawnAt = 0;
            RiftCrabMarker.Status status = RiftCrabMarker.Status.UNKNOWN;
            if (!INITIAL_SPAWNED_POINTS.contains(point.id)) {
                status = RiftCrabMarker.Status.WAITING;
                spawnAt = activeMatchStart + firstSpawnDelay(point.type);
            } else {
                PendingRespawn pending = RESPAWN.get(point.pos);
                if (pending != null && point.id.equals(pending.pointId) && pending.seenBy.contains(team)) {
                    status = RiftCrabMarker.Status.WAITING;
                    spawnAt = pending.at;
                } else if (pending == null) {
                    for (State state : STATES.values()) {
                        if (point.id.equals(state.spawnPointId) && state.entity != null && state.entity.isAlive()
                                && VisionSystem.seesObjective(level, team, state.entity.getEyePosition())) {
                            status = RiftCrabMarker.Status.ALIVE;
                            break;
                        }
                    }
                }
            }
            markers.add(new RiftCrabMarker(point.id, x, z, status, spawnAt));
        }
        return List.copyOf(markers);
    }

    /** Preview follows normal camp ownership and respawn rules; a real match resets it. */
    public static int previewFixedSpawnPoints(ServerLevel level) {
        GameManager manager = GameManager.get();
        if (!ModState.isEnabled() || !TrainingMode.isEnabled() || manager == null
                || manager.getPhase() != GameManager.Phase.WAITING || level != level.getServer().overworld()) return 0;
        int spawned = 0;
        for (SpawnPoint point : SPAWN_POINTS.values()) {
            if (!point.largeRandom() && isPointFree(point.id())
                    && spawnConfigured(level, point, point.type(), false)) spawned++;
        }
        return spawned;
    }

    private static void resetForMatch(ServerLevel level, long matchStart) {
        clearRuntime(level);
        activeMatchStart = matchStart;
        nextBossSpawnAt = matchStart + FIRST_BOSS_SPAWN_TICKS;
        SPAWN_GENERATIONS.clear();
        INITIAL_SPAWNED_POINTS.clear();
    }

    /** 在一个空闲大型点生成一种当前场上不存在的随机首领。 */
    private static boolean trySpawnRandomBoss(ServerLevel level) {
        List<SpawnPoint> freePoints = new ArrayList<>();
        for (SpawnPoint point : SPAWN_POINTS.values()) {
            if (point.largeRandom() && isPointFree(point.id())) freePoints.add(point);
        }
        if (freePoints.isEmpty()) return false;

        Set<MonsterType> activeTypes = new HashSet<>();
        for (State state : STATES.values()) {
            if (state.type.isLargeBoss()) activeTypes.add(state.type);
        }
        List<MonsterType> candidates = new ArrayList<>();
        for (MonsterType type : LARGE_BOSSES) {
            if (!activeTypes.contains(type)) candidates.add(type);
        }
        if (candidates.isEmpty()) return false;
        Collections.shuffle(freePoints);
        Collections.shuffle(candidates);
        return spawnConfigured(level, freePoints.getFirst(), candidates.getFirst(), true);
    }

    private static boolean isPointFree(String pointId) {
        if (pointId == null) return true;
        for (State state : STATES.values()) {
            if (pointId.equals(state.spawnPointId)) return false;
        }
        for (PendingRespawn pending : RESPAWN.values()) {
            if (pointId.equals(pending.pointId())) return false;
        }
        return true;
    }

    /** 野怪受伤：按伤害量累计仇恨（仇恨 = 玩家对其造成的伤害） */
    public static void onAttacked(LivingEntity monster, LivingEntity attacker, float amount) {
        State s = STATES.get(monster.getUUID());
        if (s == null || !(attacker instanceof ServerPlayer sp) || amount <= 0) {
            return;
        }
        s.damageTable.merge(sp.getUUID(), (double) amount, Double::sum);
        s.returning = false;
        if (monster instanceof Mob mob) {
            mob.setTarget(sp);
        }
    }

    /** 扣除真实模组生命；返回 true 表示本次应交给原版完成死亡事件与掉落链路。 */
    public static boolean damageModHealth(LivingEntity monster, float amount) {
        State s = STATES.get(monster.getUUID());
        if (s == null || amount <= 0) return false;
        s.modHealth = Math.max(0.0f, s.modHealth - amount);
        return s.modHealth <= 0.0f;
    }

    public static float modHealth(LivingEntity monster) {
        State s = STATES.get(monster.getUUID());
        return s == null ? monster.getHealth() : s.modHealth;
    }

    public static float modMaxHealth(LivingEntity monster) {
        State s = STATES.get(monster.getUUID());
        return s == null ? monster.getMaxHealth() : s.modMaxHealth;
    }

    /** Neutral camps retaliate against valid attackers, with stable threat selection and a hard leash. */
    private static void tickMonster(ServerLevel level, Mob mob, State s, long now) {
        double cooldown=Math.max(1,Math.round(20/growthMultiplier(s.respawnGeneration)));
        double actionRate = KehengTimeFields.managedNpcRate(mob, now);
        if(now>s.lastRecoveryTick){
            s.attackRecovery=Math.min(cooldown,s.attackRecovery+actionRate);
            s.lastRecoveryTick=now;
        }
        mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);
        Vec3 home=Vec3.atBottomCenterOf(s.spawnPos);
        s.damageTable.entrySet().removeIf(e -> !(level.getEntity(e.getKey()) instanceof LivingEntity target)
                || !NpcCombat.canTarget(target));
        if(s.returning){
            endCherryStrike(s);
            mob.setTarget(null);
            s.modHealth=Math.min(s.modMaxHealth,s.modHealth+(float)s.regenPerTick);
            NpcNavigation.move(level,mob,s.navigation,home,s.type.getMoveSpeed()*growthMultiplier(s.respawnGeneration)/12.0);
            if(mob.position().distanceToSqr(home)<2.25){
                s.returning=false;s.damageTable.clear();s.target=null;s.modHealth=s.modMaxHealth;
            }
            return;
        }
        if (s.cherryStrike != null && tickCherryStrike(level, (CherryRiftMonsterEntity) mob, s, home, now, actionRate)) {
            return;
        }
        if(s.damageTable.isEmpty()){
            mob.setTarget(null);s.target=null;
            if(mob.position().distanceToSqr(home)>4)s.returning=true;
            return;
        }
        LivingEntity target=s.target==null?null:level.getEntity(s.target) instanceof LivingEntity living?living:null;
        if(target==null || now>=s.nextTargetScan){
            s.nextTargetScan=now+5;
            LivingEntity best=null;double score=-1;
            for(var threat:s.damageTable.entrySet()){
                if(!(level.getEntity(threat.getKey()) instanceof LivingEntity candidate))continue;
                if(candidate.position().distanceToSqr(home)>400)continue;
                double value=threat.getValue()+(NpcNavigation.visible(mob,candidate)?5:0);
                if(candidate==target)value*=1.25; // avoids twitching between almost-equal targets
                if(value>score){score=value;best=candidate;}
            }
            target=best;s.target=target==null?null:target.getUUID();
        }
        if(target==null||mob.position().distanceToSqr(home)>400||target.position().distanceToSqr(home)>400){
            s.returning=true;s.target=null;s.damageTable.clear();mob.setTarget(null);return;
        }
        mob.setTarget(target);
        boolean crabChasingPlayer = isCrabPlayerPair(s, target);
        Vec3 gap = crabChasingPlayer ? shellGap(mob, target) : Vec3.ZERO;
        if ((!crabChasingPlayer || gap.lengthSqr() <= .55 * .55) && canReachByMelee(mob, s, target)) {
            mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,target.getEyePosition());
            if(s.attackRecovery+1E-9>=cooldown){
                s.attackRecovery=0;
                s.lastAttackTick=now;
                if (mob instanceof CherryRiftMonsterEntity cherry) {
                    s.cherryStrike = new CherryStrike(target.getUUID(), CherryRiftAttackTiming.forType(s.type),
                            new ActionTime.Progress(now));
                    cherry.publishAttackProgress(0F);
                } else {
                    mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                    applyNpcDamage(target,mob,(float)(s.type.getAttack()*growthMultiplier(s.respawnGeneration)));
                }
            }
        } else {
            double step = s.type.getMoveSpeed() * growthMultiplier(s.respawnGeneration) / 12.0;
            Vec3 goal = target.position();
            if (crabChasingPlayer && gap.lengthSqr() > .55 * .55) {
                double travel = gap.length() - .45;
                Vec3 edge = mob.position().add(gap.normalize().scale(travel));
                goal = new Vec3(edge.x, target.getY(), edge.z);
                step = Math.min(step, travel);
            }
            NpcNavigation.move(level, mob, s.navigation, goal, step);
        }
    }

    private static boolean tickCherryStrike(ServerLevel level, CherryRiftMonsterEntity mob, State state,
            Vec3 home, long now, double rate) {
        CherryStrike strike = state.cherryStrike;
        LivingEntity target = level.getEntity(strike.target()) instanceof LivingEntity living
                && NpcCombat.canTarget(living) ? living : null;
        double previous = strike.progress().completed();
        boolean beforeHit = previous + 1E-9 < strike.timing().hit();
        if (mob.position().distanceToSqr(home) > 400 || (target == null && beforeHit)
                || (target != null && target.position().distanceToSqr(home) > 400)) {
            endCherryStrike(state);
            state.target = null;
            return false;
        }

        mob.setTarget(target);
        // A lethal hit can complete its follow-through even though the target is gone.
        if (target != null) {
            mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        }
        boolean finished = strike.progress().advance(now, rate, strike.timing().duration());
        double completed = strike.progress().completed();
        mob.publishAttackProgress((float) (completed / strike.timing().duration()));
        if (target != null && beforeHit && completed + 1E-9 >= strike.timing().hit()
                && canReachByMelee(mob, state, target)) {
            applyNpcDamage(target, mob, (float) (state.type.getAttack() * growthMultiplier(state.respawnGeneration)));
        }
        if (finished) endCherryStrike(state);
        return !finished;
    }

    private static boolean isCrabPlayerPair(State state, LivingEntity target) {
        return state.type == MonsterType.CHERRY_RIFT_CRAB && target instanceof Player;
    }

    /** Shortest horizontal displacement from the shell box to the player's box. */
    private static Vec3 shellGap(Mob mob, LivingEntity target) {
        var shell = mob.getBoundingBox();
        var body = target.getBoundingBox();
        double x = Math.max(0, body.minX - shell.maxX) - Math.max(0, shell.minX - body.maxX);
        double z = Math.max(0, body.minZ - shell.maxZ) - Math.max(0, shell.minZ - body.maxZ);
        return new Vec3(x, 0, z);
    }

    private static boolean canReachByMelee(Mob mob, State state, LivingEntity target) {
        if (isCrabPlayerPair(state, target)) {
            return crabCanReach(mob, target, state.respawnGeneration);
        }
        double range = state.type.getAttackRange() * growthMultiplier(state.respawnGeneration) + target.getBbWidth() * .25;
        return mob.distanceToSqr(target) <= range * range && NpcNavigation.visible(mob, target);
    }

    static boolean crabCanReach(Mob mob, LivingEntity target, int generation) {
        // Keep the original range gained by later generations, measured from the calibrated shell edge.
        double reach = .8 + MonsterType.CHERRY_RIFT_CRAB.getAttackRange() * (growthMultiplier(generation) - 1);
        return shellGap(mob, target).lengthSqr() <= reach * reach
                && Math.abs(mob.getY() - target.getY()) <= 1.05
                && NpcNavigation.visible(mob, target) && continuousCrabGround(mob, target);
    }

    /** The wide shell can overhang a bank. Check the ground between the two bodies before striking. */
    private static boolean continuousCrabGround(Mob mob, LivingEntity target) {
        Vec3 from = mob.position(), to = target.position();
        var level = mob.level();
        var lowRay = new net.minecraft.world.level.ClipContext(from.add(0, .65, 0), to.add(0, .65, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mob);
        if (level.clip(lowRay).getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
        int steps = Math.max(1, (int) Math.ceil(from.subtract(to).horizontalDistance() * 2));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(to, (double) i / steps);
            var ground = level.clip(new net.minecraft.world.level.ClipContext(at.add(0, .5, 0), at.add(0, -1.25, 0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, mob));
            if (ground.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                    || !level.getFluidState(ground.getBlockPos()).isEmpty()) return false;
        }
        return true;
    }

    private static void endCherryStrike(State state) {
        if (state.cherryStrike == null) return;
        state.cherryStrike = null;
        ((CherryRiftMonsterEntity) state.entity).publishAttackProgress(-1F);
    }

    /** 野怪伤害使用模组双防/护盾，不受和平难度的原版缩放影响。 */
    private static void applyNpcDamage(LivingEntity target, Mob attacker, float damage) {
        NpcCombat.hit(target, attacker, damage,
                com.myriad.combat.DamageCalc.Kind.PHYSICAL);
    }

    /** 创建隐形倒计时 ArmorStand */
    private static boolean isMatchCrab(PendingRespawn pending) {
        var manager = GameManager.get();
        return pending.isFixedCrab() && manager != null && manager.getPhase() == GameManager.Phase.PLAYING;
    }

    private static void observeCrab(ServerLevel level, PendingRespawn pending, Vec3 position) {
        for (Faction team : Faction.values()) {
            if (!pending.seenBy.contains(team) && VisionSystem.seesObjective(level, team, position)) {
                pending.seenBy.add(team);
            }
        }
    }

    private static ArmorStand createRespawnStand(ServerLevel level, BlockPos pos, PendingRespawn pending) {
        ArmorStand stand = isMatchCrab(pending)
                ? new ArmorStand(EntityTypes.ARMOR_STAND, level) {
                    @Override public boolean broadcastToPlayer(ServerPlayer player) {
                        var manager = GameManager.get();
                        return manager != null && manager.getPhase() == GameManager.Phase.PLAYING
                                && manager.isParticipant(player.getUUID()) && !TrainingMode.isEnabled(player)
                                && pending.seenBy.contains(manager.factionOf(player)) && super.broadcastToPlayer(player);
                    }
                }
                : EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (stand == null) {
            return null;
        }
        stand.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        stand.setNoGravity(true);
        stand.setInvisible(true);
        stand.setPermanentlyInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(Component.literal("刷新: --s"));
        level.addFreshEntity(ModEntityTracker.markGenerated(stand));
        return stand;
    }

    /** 野怪死亡：清理状态 + 安排重生 + 倒计时显示 */
    public static void onDeath(LivingEntity entity, ServerPlayer killer) {
        State s = STATES.remove(entity.getUUID());
        if (s == null) {
            return;
        }
        endCherryStrike(s);
        // 大型随机首领不在原地点直接复活，由全局 5 分钟轮次重新抽取位置和类型。
        if (!s.randomBoss && s.type.getRespawnSeconds() > 0 && entity.level() instanceof ServerLevel level) {
            int seconds = s.spawnPointId == null ? s.type.getRespawnSeconds() : GameManager.combatTempo().respawnSeconds(s.type);
            long at = level.getGameTime() + seconds * 20L;
            PendingRespawn pending = new PendingRespawn(s.type, at, s.spawnPointId, Set.of());
            if (isMatchCrab(pending)) {
                var manager = GameManager.get();
                manager.recordCrabKill(level.getGameTime());
                if (killer != null && killer.level() == level && manager.isParticipant(killer.getUUID())
                        && !TrainingMode.isEnabled(killer)) {
                    Faction team = manager.factionOf(killer);
                    if (team != null) pending.seenBy.add(team);
                }
                observeCrab(level, pending, entity.getEyePosition());
            }
            RESPAWN.put(s.spawnPos, pending);
            // 立即创建倒计时显示
            ArmorStand stand = createRespawnStand(level, s.spawnPos, pending);
            if (stand != null) {
                stand.setCustomName(Component.literal("刷新: " + seconds + "s"));
                RESPAWN_DISPLAY.put(s.spawnPos, stand);
            }
        }
    }

    /** 新一轮模组玩法开启时移除上一轮野怪、刷新倒计时和运行时状态。 */
    public static void clearGenerated(net.minecraft.server.MinecraftServer server) {
        for (State state : STATES.values()) if (state.entity != null) state.entity.discard();
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (STATES.containsKey(entity.getUUID())) entity.discard();
            }
        }
        for (ArmorStand stand : RESPAWN_DISPLAY.values()) {
            if (stand.isAlive()) stand.discard();
        }
        STATES.clear();
        RESPAWN.clear();
        RESPAWN_DISPLAY.clear();
        activeMatchStart = Long.MIN_VALUE;
        nextBossSpawnAt = Long.MAX_VALUE;
        SPAWN_GENERATIONS.clear();
        INITIAL_SPAWNED_POINTS.clear();
    }

    private static void clearRuntime(ServerLevel level) {
        for (State state : STATES.values()) if (state.entity != null) state.entity.discard();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (STATES.containsKey(entity.getUUID())) entity.discard();
        }
        for (ArmorStand stand : RESPAWN_DISPLAY.values()) {
            if (stand.isAlive()) stand.discard();
        }
        STATES.clear();
        RESPAWN.clear();
        RESPAWN_DISPLAY.clear();
    }

    /** 服务端启动时读取世界存档中的野怪刷新点。 */
    public static void initialize(net.minecraft.server.MinecraftServer minecraftServer) {
        server = minecraftServer;
        ArenaConfigFile.readOptional(spawnPointConfigPath(), MonsterManager::decodeSpawns).ifPresent(MonsterManager::activateSpawns);
        activeMatchStart = Long.MIN_VALUE;
    }

    public static void reloadArena(net.minecraft.server.MinecraftServer minecraftServer) {
        resetArenaRuntime(minecraftServer);
        initialize(minecraftServer);
    }

    static void resetArenaRuntime(net.minecraft.server.MinecraftServer minecraftServer) {
        clearRuntime(minecraftServer.overworld());
        SPAWN_GENERATIONS.clear();
        INITIAL_SPAWNED_POINTS.clear();
        nextBossSpawnAt = Long.MAX_VALUE;
        activeMatchStart = Long.MIN_VALUE;
    }

    public static void shutdown() {
        server = null;
        STATES.clear();
        RESPAWN.clear();
        RESPAWN_DISPLAY.clear();
        SPAWN_POINTS.clear();
        SPAWN_GENERATIONS.clear();
        INITIAL_SPAWNED_POINTS.clear();
        activeMatchStart = Long.MIN_VALUE;
    }

    public static boolean setSpawnPoint(String id, BlockPos pos, MonsterType type, boolean largeRandom) {
        if (server == null || id == null || !id.matches("[A-Za-z0-9_-]{1,32}") || pos == null) return false;
        if (!largeRandom && (type == null || !type.canUseFixedSpawn())) return false;
        var next = spawnEditBase();
        if (next == null) return false;
        next.put(id, new SpawnPoint(id, pos.immutable(), largeRandom ? null : type));
        return saveSpawnPoints(next);
    }

    public static boolean removeSpawnPoint(String id) {
        if (server == null) return false;
        var next = spawnEditBase();
        if (next == null) return false;
        return next.remove(id) != null && saveSpawnPoints(next);
    }

    private static Map<String, SpawnPoint> spawnEditBase() {
        return ArenaConfigFile.readForEdit(spawnPointConfigPath(), MonsterManager::decodeSpawns,
                () -> new LinkedHashMap<>(SPAWN_POINTS)).orElse(null);
    }

    public static List<SpawnPointInfo> spawnPointInfos() {
        List<SpawnPointInfo> result = new ArrayList<>();
        for (SpawnPoint point : SPAWN_POINTS.values()) {
            result.add(new SpawnPointInfo(point.id(), point.pos(),
                    point.largeRandom() ? "large_random" : point.type().getId(), point.largeRandom()));
        }
        return List.copyOf(result);
    }

    private static boolean saveSpawnPoints(Map<String, SpawnPoint> next) {
        com.google.gson.JsonArray points = new com.google.gson.JsonArray();
        for (SpawnPoint point : next.values()) {
            com.google.gson.JsonObject value = new com.google.gson.JsonObject();
            value.addProperty("id", point.id());
            value.addProperty("type", point.largeRandom() ? "large_random" : point.type().getId());
            value.addProperty("x", point.pos().getX());
            value.addProperty("y", point.pos().getY());
            value.addProperty("z", point.pos().getZ());
            points.add(value);
        }
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.add("points", points);
        if (!ArenaConfigFile.save(spawnPointConfigPath(), root)) return false;
        activateSpawns(next);
        return true;
    }

    static void activateSpawns(Map<String, SpawnPoint> next) {
        SPAWN_POINTS.clear();
        SPAWN_POINTS.putAll(next);
    }

    static Map<String, SpawnPoint> decodeSpawns(com.google.gson.JsonObject root) {
        var next = new LinkedHashMap<String, SpawnPoint>();
        var points = ArenaConfigFile.array(root, "points");
        for (com.google.gson.JsonElement element : points) {
            com.google.gson.JsonObject value = element.getAsJsonObject();
            String id = value.get("id").getAsString();
            String typeId = value.get("type").getAsString();
            MonsterType type = "large_random".equals(typeId) ? null : MonsterType.byId(typeId);
            if (!id.matches("[A-Za-z0-9_-]{1,32}") || (type == null && !"large_random".equals(typeId))
                    || (type != null && !type.canUseFixedSpawn())) continue;
            BlockPos pos = new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(),
                    value.get("z").getAsInt());
            next.put(id, new SpawnPoint(id, pos, type));
        }
        return next;
    }

    private static java.nio.file.Path spawnPointConfigPath() {
        return MatchArena.configPath(server, "myriad_monster_spawns.json");
    }

    /** 击杀奖励：经验/金币（金钱猪为击杀阵营全员金币）/ 掉落 buff */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        State s = STATES.get(victim.getUUID());
        if (s == null || killer.level() != victim.level() || !eligibleForReward(killer)) {
            return;
        }
        MonsterType type = s.type;
        PlayerData data = PlayerDataProvider.get(killer);
        if (data == null) {
            return;
        }
        int gold=type==MonsterType.GOLD_PIG?MatchBalance.pigGold(MatchBalance.elapsed(killer)):type.getGold();
        if(!type.isTeamGold())gold=MatchBalance.farmReward(killer,gold,false);
        gold=GlobalBalance.goldIncome(gold);
        int xp=MatchBalance.farmReward(killer,type.getXp(),true);
        if (type.isTeamGold()) {
            GameManager manager = GameManager.get();
            Faction killerFaction = manager == null ? null : manager.factionOf(killer);
            long windUntil = victim.level().getGameTime() + 90L * 20L;
            for (ServerPlayer p : killer.level().players()) {
                if (!eligibleForReward(p)) continue;
                if (killerFaction != null && manager.factionOf(p) != killerFaction) continue;
                if (killerFaction == null && p != killer) continue;
                PlayerData pd = PlayerDataProvider.get(p);
                if (pd != null) {
                    pd.addGold(gold);
                    if(p != killer) com.myriad.network.RewardFloatPayload.send(p, victim, gold, 0);
                    if (type == MonsterType.GOLD_PIG) {
                        pd.setGoldWindUntil(windUntil);
                    }
                    PlayerDataProvider.syncToClient(p);
                }
            }
            MatchMessages.send(killer, MatchMessages.Tone.OBJECTIVE,
                    Component.translatable("message.myriad.mob_team_gold",
                            Component.translatable(type.getNameKey()), gold));
            if (type == MonsterType.GOLD_PIG) {
                MatchMessages.send(killer, MatchMessages.Tone.SUCCESS,
                        Component.translatable("message.myriad.buff_gold_wind"));
            }
        } else {
            data.addGold(gold);
            MatchMessages.send(killer, MatchMessages.Tone.SUCCESS,
                    Component.translatable("message.myriad.mob_kill",
                            Component.translatable(type.getNameKey()), xp, gold));
        }
        com.myriad.player.LevelSystem.addXp(killer, data, xp);
        com.myriad.network.RewardFloatPayload.send(killer, victim, gold, xp);
        if (type.getBuff() != null) {
            long now = victim.level().getGameTime();
            if ("spirit".equals(type.getBuff())) {
                data.setSpiritRuneUntil(now + 90L * 20L);
                MatchMessages.send(killer, MatchMessages.Tone.SUCCESS,
                        Component.translatable("message.myriad.buff_spirit"));
            } else if ("spring_mana".equals(type.getBuff())) {
                data.setSpringWellUntil(now + 90L * 20L);
                MatchMessages.send(killer, MatchMessages.Tone.SUCCESS,
                        Component.translatable("message.myriad.buff_spring_mana"));
            } else if ("beast".equals(type.getBuff())) {
                data.setBeastRuneUntil(now + 90L * 20L);
                MatchMessages.send(killer, MatchMessages.Tone.SUCCESS,
                        Component.translatable("message.myriad.buff_beast"));
            } else {
                grantTeamBossBuff(killer, type, now + BOSS_BUFF_TICKS);
            }
        }
        PlayerDataProvider.syncToClient(killer);
    }

    private static boolean eligibleForReward(ServerPlayer player) {
        var match = GameManager.get();
        return !TrainingGround.isTraining(player.level()) && (match == null || match.getPhase() != GameManager.Phase.PLAYING
                || match.isParticipant(player.getUUID()));
    }

    /** 大型野怪奖励只发给击杀者所在阵营；未开对局时仅奖励击杀者。 */
    private static void grantTeamBossBuff(ServerPlayer killer, MonsterType type, long until) {
        String buff = type.getBuff();
        GameManager manager = GameManager.get();
        Faction faction = manager == null ? null : manager.factionOf(killer);
        for (ServerPlayer player : killer.level().players()) {
            if (!eligibleForReward(player)) continue;
            if (faction != null && manager.factionOf(player) != faction) continue;
            if (faction == null && player != killer) continue;
            PlayerData data = PlayerDataProvider.get(player);
            if (data == null) continue;
            switch (buff) {
                case "azure_attack" -> data.setAzureHornUntil(until);
                case "vermilion_spell" -> data.setVermilionFlameUntil(until);
                case "gale_haste" -> data.setGaleRocUntil(until);
                case "obsidian_guard" -> data.setObsidianTortoiseUntil(until);
                default -> { continue; }
            }
            PlayerDataProvider.syncToClient(player);
        }
        MatchMessages.send(killer, MatchMessages.Tone.SUCCESS,
                Component.translatable(type == MonsterType.CHERRY_RIFT_CRAB
                        ? "message.myriad.cherry_rift_crab_buff" : "message.myriad.buff_" + buff));
    }
}
