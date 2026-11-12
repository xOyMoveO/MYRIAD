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
                live.lastAttackTick=saved.lastAttackTick;CombatChunks.prepare(level,BlockPos.containing(position));
            }
        }
        for(var row:root.getAsJsonArray("respawns")) {
            BlockPos pos=MatchProgress.JSON.fromJson(row.getAsJsonObject().get("position"),BlockPos.class);
            RESPAWN.put(pos,MatchProgress.JSON.fromJson(row,PendingRespawn.class));
        }
    }

    static double growthMultiplier(int generation) { return 1.0 + Math.max(0,generation-2)*.03; }
    private static double initialHealth(MonsterType type,int generation) { return type.getHealth()*growthMultiplier(generation); }
    private static final long SMALL_MONSTER_FIRST_SPAWN_TICKS = 30L * 20L;
    private static final long MEDIUM_MONSTER_FIRST_SPAWN_TICKS = 60L * 20L;
    private static final long FIRST_BOSS_SPAWN_TICKS = 8L * 60L * 20L;
    private static final long BOSS_SPAWN_INTERVAL_TICKS = 5L * 60L * 20L;
    private static final MonsterType[] LARGE_BOSSES = {
            MonsterType.AZURE_HORN_GUARDIAN, MonsterType.VERMILION_FLAME_TYRANT,
            MonsterType.GALE_ROC_MATRIARCH, MonsterType.OBSIDIAN_TORTOISE_WARDEN
    };

    /** 写入存档配置文件的刷新点。type=null 表示大型随机刷新点。 */
    private record SpawnPoint(String id, BlockPos pos, MonsterType type) {
        boolean largeRandom() { return type == null; }
    }

    public record SpawnPointInfo(String id, BlockPos pos, String typeId, boolean largeRandom) {}

    private record PendingRespawn(MonsterType type, long at, String pointId) {}

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
        spawnInternal(level, pos, type, null, false, 0);
    }

    private static void spawnConfigured(ServerLevel level, SpawnPoint point, MonsterType type,
            boolean randomBoss) {
        int generation = SPAWN_GENERATIONS.getOrDefault(point.id(), 0);
        spawnInternal(level, point.pos(), type, point.id(), randomBoss, generation);
        SPAWN_GENERATIONS.put(point.id(), generation + 1);
    }

    private static void spawnInternal(ServerLevel level, BlockPos pos, MonsterType type,
            String pointId, boolean randomBoss, int generation) {
        CombatChunks.prepare(level, pos);
        // 26.2 rejects hostile entity creation itself on peaceful, before checkDespawn runs.
        net.minecraft.world.entity.Entity e = type.getEntityType().create(level,
                new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.MOB_SUMMONED, true));
        if (!(e instanceof Mob mob)) {
            return;
        }
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
        if (scale != null && type.isLargeBoss()) scale.setBaseValue(1.35);
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
            return;
        }
        State state = new State(pos, type, pointId, randomBoss, generation);
        state.entity = mob;
        STATES.put(mob.getUUID(), state);
        // 移除重生倒计时显示
        ArmorStand stand = RESPAWN_DISPLAY.remove(pos);
        if (stand != null) {
            stand.discard();
        }
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
                        spawnConfigured(level, point, pending.type(), false);
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
                long at = entry.getValue().at();
                int remain = (int) Math.max(0, (at - now) / 20);
                ArmorStand stand = RESPAWN_DISPLAY.get(entry.getKey());
                if (stand == null || !stand.isAlive()) {
                    stand = createRespawnStand(level, entry.getKey());
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

    /** 小型点在开局 30 秒、中型点在开局 60 秒完成本局首次生成。 */
    private static void spawnInitialConfiguredMonsters(ServerLevel level, long elapsed) {
        for (SpawnPoint point : SPAWN_POINTS.values()) {
            MonsterType type = point.type();
            if (point.largeRandom() || type == null || INITIAL_SPAWNED_POINTS.contains(point.id())) continue;
            long firstSpawnAt = type.isSmallMonster() ? SMALL_MONSTER_FIRST_SPAWN_TICKS
                    : type.isMediumMonster() ? MEDIUM_MONSTER_FIRST_SPAWN_TICKS : Long.MAX_VALUE;
            if (elapsed < firstSpawnAt || !isPointFree(point.id())) continue;
            spawnConfigured(level, point, type, false);
            INITIAL_SPAWNED_POINTS.add(point.id());
        }
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
        spawnConfigured(level, freePoints.getFirst(), candidates.getFirst(), true);
        return true;
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
        mob.setDeltaMovement(0,mob.getDeltaMovement().y,0);
        Vec3 home=Vec3.atBottomCenterOf(s.spawnPos);
        s.damageTable.entrySet().removeIf(e -> !(level.getEntity(e.getKey()) instanceof LivingEntity target)
                || !com.myriad.combat.NpcCombat.canTarget(target));
        if(s.returning){
            mob.setTarget(null);
            s.modHealth=Math.min(s.modMaxHealth,s.modHealth+(float)s.regenPerTick);
            NpcNavigation.move(level,mob,s.navigation,home,s.type.getMoveSpeed()*growthMultiplier(s.respawnGeneration)/12.0);
            if(mob.position().distanceToSqr(home)<2.25){
                s.returning=false;s.damageTable.clear();s.target=null;s.modHealth=s.modMaxHealth;
            }
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
        double range=s.type.getAttackRange()*growthMultiplier(s.respawnGeneration)+target.getBbWidth()*.25;
        if(mob.distanceToSqr(target)<=range*range && NpcNavigation.visible(mob,target)){
            mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,target.getEyePosition());
            if(now-s.lastAttackTick>=Math.max(1,Math.round(20/(growthMultiplier(s.respawnGeneration))))){
                s.lastAttackTick=now;
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                applyNpcDamage(target,mob,(float)(s.type.getAttack()*growthMultiplier(s.respawnGeneration)));
            }
        }else NpcNavigation.move(level,mob,s.navigation,target.position(),s.type.getMoveSpeed()*growthMultiplier(s.respawnGeneration)/12.0);
    }

    /** 野怪伤害使用模组双防/护盾，不受和平难度的原版缩放影响。 */
    private static void applyNpcDamage(LivingEntity target, Mob attacker, float damage) {
        com.myriad.combat.NpcCombat.hit(target, attacker, damage,
                com.myriad.combat.DamageCalc.Kind.PHYSICAL);
    }

    /** 创建隐形倒计时 ArmorStand */
    private static ArmorStand createRespawnStand(ServerLevel level, BlockPos pos) {
        ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (stand == null) {
            return null;
        }
        stand.setPos(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
        stand.setNoGravity(true);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setCustomNameVisible(true);
        stand.setCustomName(Component.literal("刷新: --s"));
        level.addFreshEntity(ModEntityTracker.markGenerated(stand));
        return stand;
    }

    /** 野怪死亡：清理状态 + 安排重生 + 倒计时显示 */
    public static void onDeath(LivingEntity entity) {
        State s = STATES.remove(entity.getUUID());
        if (s == null) {
            return;
        }
        // 大型随机首领不在原地点直接复活，由全局 5 分钟轮次重新抽取位置和类型。
        if (!s.randomBoss && s.type.getRespawnSeconds() > 0 && entity.level() instanceof ServerLevel level) {
            long at = level.getGameTime() + s.type.getRespawnSeconds() * 20L;
            RESPAWN.put(s.spawnPos, new PendingRespawn(s.type, at, s.spawnPointId));
            // 立即创建倒计时显示
            ArmorStand stand = createRespawnStand(level, s.spawnPos);
            if (stand != null) {
                stand.setCustomName(Component.literal("刷新: " + s.type.getRespawnSeconds() + "s"));
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
        loadSpawnPoints();
        activeMatchStart = Long.MIN_VALUE;
    }

    public static void reloadArena(net.minecraft.server.MinecraftServer minecraftServer) {
        clearRuntime(minecraftServer.overworld());
        SPAWN_GENERATIONS.clear();
        INITIAL_SPAWNED_POINTS.clear();
        nextBossSpawnAt = Long.MAX_VALUE;
        initialize(minecraftServer);
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
        if (!largeRandom && (type == null || type.isLargeBoss())) return false;
        SPAWN_POINTS.put(id, new SpawnPoint(id, pos.immutable(), largeRandom ? null : type));
        saveSpawnPoints();
        return true;
    }

    public static boolean removeSpawnPoint(String id) {
        if (SPAWN_POINTS.remove(id) == null) return false;
        saveSpawnPoints();
        return true;
    }

    public static List<SpawnPointInfo> spawnPointInfos() {
        List<SpawnPointInfo> result = new ArrayList<>();
        for (SpawnPoint point : SPAWN_POINTS.values()) {
            result.add(new SpawnPointInfo(point.id(), point.pos(),
                    point.largeRandom() ? "large_random" : point.type().getId(), point.largeRandom()));
        }
        return List.copyOf(result);
    }

    private static void saveSpawnPoints() {
        if (server == null) return;
        com.google.gson.JsonArray points = new com.google.gson.JsonArray();
        for (SpawnPoint point : SPAWN_POINTS.values()) {
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
        try {
            java.nio.file.Files.writeString(spawnPointConfigPath(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (java.io.IOException e) {
            com.myriad.Myriad.LOGGER.error("[monster] failed to save spawn points", e);
        }
    }

    private static void loadSpawnPoints() {
        SPAWN_POINTS.clear();
        java.nio.file.Path path = spawnPointConfigPath();
        if (!java.nio.file.Files.isRegularFile(path)) return;
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(path)).getAsJsonObject();
            com.google.gson.JsonArray points = root.getAsJsonArray("points");
            if (points == null) return;
            for (com.google.gson.JsonElement element : points) {
                com.google.gson.JsonObject value = element.getAsJsonObject();
                String id = value.get("id").getAsString();
                String typeId = value.get("type").getAsString();
                MonsterType type = "large_random".equals(typeId) ? null : MonsterType.byId(typeId);
                if (!id.matches("[A-Za-z0-9_-]{1,32}") || (type == null && !"large_random".equals(typeId))
                        || (type != null && type.isLargeBoss())) continue;
                BlockPos pos = new BlockPos(value.get("x").getAsInt(), value.get("y").getAsInt(),
                        value.get("z").getAsInt());
                SPAWN_POINTS.put(id, new SpawnPoint(id, pos, type));
            }
        } catch (Exception e) {
            com.myriad.Myriad.LOGGER.error("[monster] failed to load spawn points", e);
        }
    }

    private static java.nio.file.Path spawnPointConfigPath() {
        return MatchArena.configPath(server, "myriad_monster_spawns.json");
    }

    /** 击杀奖励：经验/金币（金钱猪为击杀阵营全员金币）/ 掉落 buff */
    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        State s = STATES.get(victim.getUUID());
        if (s == null) {
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
            for (ServerPlayer p : ((ServerLevel) victim.level()).getServer().getPlayerList().getPlayers()) {
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
                grantTeamBossBuff(killer, type.getBuff(), now + 90L * 20L);
            }
        }
        PlayerDataProvider.syncToClient(killer);
    }

    /** 大型野怪奖励只发给击杀者所在阵营；未开对局时仅奖励击杀者。 */
    private static void grantTeamBossBuff(ServerPlayer killer, String buff, long until) {
        GameManager manager = GameManager.get();
        Faction faction = manager == null ? null : manager.factionOf(killer);
        for (ServerPlayer player : ((ServerLevel) killer.level()).getServer().getPlayerList().getPlayers()) {
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
                Component.translatable("message.myriad.buff_" + buff));
    }
}
