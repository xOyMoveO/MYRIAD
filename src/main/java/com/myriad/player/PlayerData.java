package com.myriad.player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.phys.Vec3;

import com.myriad.game.Faction;

/**
 * 玩家个人数据（Capability 承载）：
 * 成长（等级/经验/金币）、法力、阵营、汲取/携带灵源状态、角色与技能冷却。
 */
public class PlayerData {
    private static final com.google.gson.Gson SAVE_JSON = new com.google.gson.Gson();
    /** Persist the full value object, including progression and skill state. */
    public static final com.mojang.serialization.Codec<PlayerData> CODEC =
            com.mojang.serialization.Codec.STRING.xmap(PlayerData::readSave, SAVE_JSON::toJson);
    /** 旧存档的符石字段由 Gson 忽略；只清理符石 buff，不影响角色技能和野怪增益。 */
    private static PlayerData readSave(String json) {
        PlayerData data = SAVE_JSON.fromJson(json, PlayerData.class);
        if (data == null) data = new PlayerData();
        data.discardRetiredRuneBuffs();
        return data;
    }

    public void discardRetiredRuneBuffs() {
        buffs.keySet().removeIf(key -> key.startsWith("rune_"));
    }

    // ---- 成长 ----
    private int level = 1;
    private int xp = 0;
    private int gold = 0;
    /** 本局累计获得金币；消费不减少，开局时由 GameManager 重置。 */
    private int matchGoldEarned = 0;

    // ---- 阵营 ----
    private Faction faction = null;

    // ---- 法力 ----
    private float mana = 50.0f;

    // ---- 模组生命（最大值独立于原版 MAX_HEALTH，突破 1024） ----
    private float modMaxHealth = 20.0f;
    private boolean modHealthInitialized = false;
    /** 服务端权威的模组护盾总值；不再借用原版吸收生命。 */
    private transient float modShield = 0.0f;

    public float getModMaxHealth() { return modMaxHealth; }
    public void setModMaxHealth(float value) { modMaxHealth = Math.max(1.0f, value); }
    public boolean isModHealthInitialized() { return modHealthInitialized; }
    public void setModHealthInitialized(boolean value) { modHealthInitialized = value; }
    public float getModShield() { return modShield; }
    public void setModShield(float value) { modShield = Math.max(0.0f, value); }

// ---- 角色与技能（阶段2） ----
    /** 角色 id（CharacterType），未选择为 null */
    private String characterId = null;
    /** 技能 id -> 剩余冷却刻数（服务端维护） */
    private final Map<String, Integer> cooldowns = new HashMap<>();
    /** 化影破隐后的增伤待触发标记 */
    private boolean stealthBonus = false;
    /** 天刑被动【雷殛之体】普攻计数（每第三次附带真实伤害） */
    private int passiveAttackCount = 0;
    /** 最近一次进入/造成伤害的游戏刻（暗影猎手脱战判定） */
    private long lastCombatGameTime = -1;
    /** 凌渊专属资源：剑罡（0~100）与已凝成、尚未出击的飞剑数量（0~12）。 */
    private int swordQi = 0;
    private int flyingSwords = 0;

    // ---- 技能升级（技能点体系） ----
    /** 可用技能点（升级普通技能/大招，每升1级 +1） */
    private int skillPoints = 0;
    /** 技能 id -> 技能等级（默认 0 = 未学习，加点后才可释放） */
    private final Map<String, Integer> skillLevels = new HashMap<>();
    /** First-group purchased ranks persist with the existing version-tolerant Gson attachment. */
    private final Map<String,Integer> firstGroupAttributes=new LinkedHashMap<>();
    public int firstGroupAttribute(String key){return Math.clamp(firstGroupAttributes.getOrDefault(key,0),0,10);}
    public void setFirstGroupAttribute(String key,int rank){firstGroupAttributes.put(key,Math.clamp(rank,0,10));}

    // ---- 汲取/运输 ----
    private transient boolean channeling = false;
    private transient int channelTicks = 0;
    private transient Vec3 channelStart = Vec3.ZERO;
    private int carrying = 0;
    /** 最近一次受击的游戏刻，用于打断汲取 */
    private long lastHurtGameTime = -1;

    // ---- 墨尘 布阵阶段标记（0=无，1=E 墨灵护阵，2=W 传送阵；预览圆跟随准星，左键确认/右键取消） ----
    private transient int placementType = 0;

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public void addGold(int amount) {
        this.gold = (int)Math.clamp((long)this.gold + amount, 0, Integer.MAX_VALUE);
        if (amount > 0) this.matchGoldEarned = Math.min(Integer.MAX_VALUE,
                (int)Math.min(Integer.MAX_VALUE, (long)this.matchGoldEarned + amount));
    }

    public int getMatchGoldEarned() { return matchGoldEarned; }
    public void resetMatchGoldEarned(int initialGold) { matchGoldEarned = Math.max(0, initialGold); }

    public void spendGold(int amount) {
        this.gold = Math.max(0, this.gold - amount);
    }

    public Faction getFaction() {
        return faction;
    }

    public void setFaction(Faction faction) {
        this.faction = faction;
    }

    public float getMana() {
        return mana;
    }

    public void setMana(float mana) {
        this.mana = mana;
    }

    public boolean isChanneling() {
        return channeling;
    }

    public void setChanneling(boolean channeling) {
        this.channeling = channeling;
    }

    public int getChannelTicks() {
        return channelTicks;
    }

    public void setChannelTicks(int channelTicks) {
        this.channelTicks = channelTicks;
    }

    public Vec3 getChannelStart() {
        return channelStart;
    }

    public void setChannelStart(Vec3 channelStart) {
        this.channelStart = channelStart;
    }

    public int getCarrying() {
        return carrying;
    }

    public void setCarrying(int carrying) {
        this.carrying = carrying;
    }

    public long getLastHurtGameTime() {
        return lastHurtGameTime;
    }

    public void setLastHurtGameTime(long lastHurtGameTime) {
        this.lastHurtGameTime = lastHurtGameTime;
    }

    public int getPlacementType() {
        return placementType;
    }

    public void setPlacementType(int placementType) {
        this.placementType = placementType;
    }

    // ---- 角色与技能 ----

    public String getCharacterId() {
        return characterId;
    }

    public void setCharacterId(String characterId) {
        this.characterId = characterId;
    }

    public boolean isStealthBonus() {
        return stealthBonus;
    }

    public void setStealthBonus(boolean stealthBonus) {
        this.stealthBonus = stealthBonus;
    }

    public int getPassiveAttackCount() {
        return passiveAttackCount;
    }

    public void setPassiveAttackCount(int passiveAttackCount) {
        this.passiveAttackCount = passiveAttackCount;
    }

    public long getLastCombatGameTime() {
        return lastCombatGameTime;
    }

    public void setLastCombatGameTime(long lastCombatGameTime) {
        this.lastCombatGameTime = lastCombatGameTime;
    }

    public int getSwordQi() {
        return swordQi;
    }

    public void setSwordQi(int swordQi) {
        this.swordQi = Math.max(0, Math.min(100, swordQi));
    }

    public int getFlyingSwords() {
        return flyingSwords;
    }

    public void setFlyingSwords(int flyingSwords) {
        this.flyingSwords = Math.max(0, Math.min(12, flyingSwords));
    }

    /** 剩余冷却刻数 */
    public int getCooldown(String skillId) {
        return cooldowns.getOrDefault(skillId, 0);
    }

    public void setCooldown(String skillId, int ticks) {
        cooldowns.put(skillId, ticks);
    }

    /** 每 tick 递减冷却；返回是否有冷却归零（需要同步） */
    public boolean tickCooldowns() {
        if (cooldowns.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Iterator<Map.Entry<String, Integer>> it = cooldowns.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
                changed = true;
            } else {
                entry.setValue(remaining);
            }
        }
        return changed;
    }

    /** 消耗法力（下限 0） */
    public void spendMana(float amount) {
        this.mana = Math.max(0.0f, this.mana - amount);
    }

    // ---- 技能附加效果（持续时间展示） ----
    /** 技能 id -> 剩余效果刻数（客户端展示） */
    private final Map<String, Integer> buffs = new LinkedHashMap<>();
    /**
     * 这些值是被动层数而不是持续时间。它们复用 buff 同步表供 HUD 读取，
     * 但绝不能在 tickBuffs 中递减，否则任意一次主动同步（例如施法）都会让
     * 客户端看到层数凭空 +1。
     */
    private static final java.util.Set<String> PASSIVE_STACK_COUNTERS = java.util.Set.of(
            "sulan_passive_ready", "chixiao_passive", "g1_keheng_marks", "g1_keheng_rate", "g1_rivet_anchor_active");

    /** 技能附加效果剩余刻数 */
    public int getBuff(String skillId) {
        int remaining = buffs.getOrDefault(skillId, 0);
        if (skillId.equals("g1_root")) for (var entry : buffs.entrySet())
            if (entry.getKey().startsWith("g1_root/")) remaining = Math.max(remaining, entry.getValue());
        return remaining;
    }

    public void setBuff(String skillId, int ticks) {
        if (skillId.equals("g1_root") && ticks <= 0) buffs.keySet().removeIf(key -> key.startsWith("g1_root/"));
        if (skillId.startsWith("skill_charge_")) {
            buffs.put(skillId, Math.max(0, ticks));
            return;
        }
        if (ticks <= 0) {
            buffs.remove(skillId);
        } else {
            buffs.put(skillId, ticks);
        }
    }

    /** 每 tick 递减效果时长；返回是否有变化（需要同步） */
    public boolean tickBuffs() {
        if (buffs.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Iterator<Map.Entry<String, Integer>> it = buffs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            if (PASSIVE_STACK_COUNTERS.contains(entry.getKey())
                    || entry.getKey().startsWith("skill_charge_") || entry.getKey().startsWith("g1_attribute_")) {
                continue;
            }
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
                changed = true;
            } else {
                entry.setValue(remaining);
                changed = true;
            }
        }
        return changed;
    }

    /** 只读快照（同步到客户端用） */
    public Map<String, Integer> getBuffs() {
        return new LinkedHashMap<>(buffs);
    }

    // ---- 技能升级 ----

    public int getSkillPoints() {
        return skillPoints;
    }
    public void setSkillPoints(int skillPoints) {
        this.skillPoints = skillPoints;
    }

    /** 技能等级（默认 0 = 未学习，加点后才可释放） */
    public int skillLevel(String skillId) {
        return skillLevels.getOrDefault(skillId, 0);
    }

    public void setSkillLevel(String skillId, int level) {
        skillLevels.put(skillId, level);
    }

    /** 训练模式切换角色时重置整套加点，并返还当前等级应有的全部技能点。 */
    public void refundAllSkillPointsForTraining() {
        skillLevels.clear();
        skillPoints = Math.max(1, level);
    }

    /** 只读快照（同步到客户端用） */
    public Map<String, Integer> getSkillLevels() {
        return new LinkedHashMap<>(skillLevels);
    }

    /** 按等级补发缺失技能点（登录时调用；已投入的等级计入总额，避免重复发放）。 */
    public void ensureLevelRewards(int level) {
        int spentSkillPoints = skillLevels.values().stream().mapToInt(rank -> Math.max(0, rank)).sum();
        int expectedAvailable = Math.max(0, level - spentSkillPoints);
        if (skillPoints < expectedAvailable) {
            skillPoints = expectedAvailable;
        }
    }

    // ---- 独立装备槽（类似英雄联盟，6 槽） ----
    private final String[] equipment = new String[com.myriad.registry.EquipmentRegistry.SLOT_COUNT];

    {
        java.util.Arrays.fill(equipment, "");
    }

    /** 槽位装备 id（空串 = 空） */
    public String getEquipmentSlot(int slot) {
        return slot >= 0 && slot < equipment.length ? equipment[slot] : "";
    }

    public void setEquipmentSlot(int slot, String equipId) {
        if (slot >= 0 && slot < equipment.length) {
            equipment[slot] = equipId == null ? "" : equipId;
        }
    }

    /** 首个空槽位（无则 -1） */
    public int firstEmptySlot() {
        for (int i = 0; i < equipment.length; i++) {
            if (equipment[i] == null || equipment[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** 只读快照（同步到客户端用） */
    public String[] getEquipmentSlots() {
        return equipment.clone();
    }

    /** 已应用的装备属性加成（attr descriptionId -> 已加到 base 的量），用于重算时还原 */
    private final transient java.util.Map<String, Double> equipApplied = new java.util.HashMap<>();

    public double getEquipApplied(String attrId) {
        return equipApplied.getOrDefault(attrId, 0.0);
    }

    public void setEquipApplied(String attrId, double amount) {
        equipApplied.put(attrId, amount);
    }

    /** 重置装备加成记录（base 被整体重置后调用，避免差值法误还原） */
    public void resetEquipApplied() {
        equipApplied.clear();
    }

    // ---- 野怪 buff（灵石灌顶 / 荒古灌顶） ----
    /** 灵石灌顶到期游戏刻（+10 法力回复[脱战] +20% 冷却缩减，90s） */
    private long spiritRuneUntil = 0;
    /** 荒古灌顶到期游戏刻（攻击减速 15% 3s，90s） */
    private long beastRuneUntil = 0;
    /** 灵泉潮汐到期游戏刻（法力回复 +8/秒，90 秒） */
    private long springWellUntil = 0;
    /** 苍角冰卫·凛锋：物理攻击 +15%（90 秒） */
    private long azureHornUntil = 0;
    /** 朱炎兽王·炎灵：法术强度 +15%（90 秒） */
    private long vermilionFlameUntil = 0;
    /** 青岚鹏王·御风：移速 +15%、攻速 +20%（90 秒） */
    private long galeRocUntil = 0;
    /** 金钱猪·钱风：移速 +10%（90 秒） */
    private long goldWindUntil = 0;
    /** 玄甲地龟·镇岳：伤害减免 15%（90 秒） */
    private long obsidianTortoiseUntil = 0;

    public long getSpiritRuneUntil() {
        return spiritRuneUntil;
    }

    public void setSpiritRuneUntil(long t) {
        spiritRuneUntil = t;
    }

    public long getBeastRuneUntil() {
        return beastRuneUntil;
    }

    public void setBeastRuneUntil(long t) {
        beastRuneUntil = t;
    }

    public long getSpringWellUntil() { return springWellUntil; }
    public void setSpringWellUntil(long t) { springWellUntil = t; }

    public long getAzureHornUntil() { return azureHornUntil; }
    public void setAzureHornUntil(long t) { azureHornUntil = t; }
    public long getVermilionFlameUntil() { return vermilionFlameUntil; }
    public void setVermilionFlameUntil(long t) { vermilionFlameUntil = t; }
    public long getGaleRocUntil() { return galeRocUntil; }
    public void setGaleRocUntil(long t) { galeRocUntil = t; }
    public long getGoldWindUntil() { return goldWindUntil; }
    public void setGoldWindUntil(long t) { goldWindUntil = t; }
    public long getObsidianTortoiseUntil() { return obsidianTortoiseUntil; }
    public void setObsidianTortoiseUntil(long t) { obsidianTortoiseUntil = t; }

    /** 完整复制另一份玩家数据（死亡重生克隆时调用；equipApplied 除外，需重建后重新应用） */
    public void copyStateFrom(PlayerData old) {
        this.level = old.level;
        this.xp = old.xp;
        this.gold = old.gold;
        this.matchGoldEarned = old.matchGoldEarned;
        this.faction = old.faction;
        this.mana = old.mana;
        this.modMaxHealth = old.modMaxHealth;
        this.modHealthInitialized = old.modHealthInitialized;
        // 临时护盾不跨死亡/重生继承。
        this.modShield = 0.0f;
        this.characterId = old.characterId;
        this.stealthBonus = old.stealthBonus;
        this.passiveAttackCount = old.passiveAttackCount;
        this.lastCombatGameTime = old.lastCombatGameTime;
        this.swordQi = old.swordQi;
        this.flyingSwords = old.flyingSwords;
        this.carrying = old.carrying;
        this.lastHurtGameTime = old.lastHurtGameTime;
        this.channeling = old.channeling;
        this.channelTicks = old.channelTicks;
        this.channelStart = old.channelStart;
        this.placementType = old.placementType;
        this.skillPoints = old.skillPoints;
        this.skillLevels.clear();
        this.skillLevels.putAll(old.skillLevels);
        this.firstGroupAttributes.clear();
        this.firstGroupAttributes.putAll(old.firstGroupAttributes);
        this.cooldowns.clear();
        this.cooldowns.putAll(old.cooldowns);
        this.buffs.clear();
        this.buffs.putAll(old.buffs);
        discardRetiredRuneBuffs();
        java.util.Arrays.fill(this.equipment, "");
        for (int i = 0; i < Math.min(old.equipment.length, this.equipment.length); i++) {
            this.equipment[i] = old.equipment[i];
        }
        this.spiritRuneUntil = old.spiritRuneUntil;
        this.beastRuneUntil = old.beastRuneUntil;
        this.springWellUntil = old.springWellUntil;
        this.azureHornUntil = old.azureHornUntil;
        this.vermilionFlameUntil = old.vermilionFlameUntil;
        this.galeRocUntil = old.galeRocUntil;
        this.goldWindUntil = old.goldWindUntil;
        this.obsidianTortoiseUntil = old.obsidianTortoiseUntil;
    }
}
