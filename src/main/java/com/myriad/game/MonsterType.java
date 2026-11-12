package com.myriad.game;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

/**
 * 野怪类型表（策划案第十一节扩展）：
 * 血量采用模组控制（直接设置 MAX_HEALTH base，数值满足需求且在上限内）；
 * 击杀奖励：经验/金币（金钱猪为全队金币）、灵石傀/荒古兽掉落强化 buff。
 */
public enum MonsterType {
    FROST_HARE("frost_hare", "monster.myriad.frost_hare", EntityTypes.RABBIT, 770, 30, 25, 70, 90, null, false, 60, 2.4, 1.5, 16, 24),
    EMBER_IMP("ember_imp", "monster.myriad.ember_imp", EntityTypes.PIGLIN, 1050, 42, 35, 90, 90, null, false, 80, 2.1, 1.5, 28, 20),
    SHADOW_FOX("shadow_fox", "monster.myriad.shadow_fox", EntityTypes.FOX, 1400, 54, 50, 120, 90, null, false, 100, 2.7, 1.5, 24, 28),
    THUNDER_BADGER("thunder_badger", "monster.myriad.thunder_badger", EntityTypes.WOLF, 980, 48, 30, 80, 90, null, false, 75, 2.6, 1.5, 32, 24),
    MIASMA_TOAD("miasma_toad", "monster.myriad.miasma_toad", EntityTypes.FROG, 1260, 60, 40, 105, 90, null, false, 90, 1.55, 1.6, 24, 40),
    STONE_SHELL_CRAB("stone_shell_crab", "monster.myriad.stone_shell_crab", EntityTypes.TURTLE, 1820, 39, 55, 140, 90, null, false, 115, 0.95, 1.5, 60, 30),
    GOLD_PIG("glod_pig", "monster.myriad.glod_pig", EntityTypes.PIG, 3500, 24, 180, 400, 0, null, true, 150, 1.75, 2.0, 50, 50),
    SPRING_REEF_GUARDIAN("spring_reef_guardian", "monster.myriad.spring_reef_guardian", EntityTypes.DROWNED, 2870, 57, 165, 150, 120, "spring_mana", false, 135, 1.45, 1.8, 50, 70),
    FROST_WEB_MATRIARCH("frost_web_matriarch", "monster.myriad.frost_web_matriarch", EntityTypes.CAVE_SPIDER, 3150, 63, 175, 155, 120, "beast", false, 145, 1.65, 1.7, 56, 60),
    AZURE_HORN_GUARDIAN("azure_horn_guardian", "monster.myriad.azure_horn_guardian", EntityTypes.RAVAGER, 10080, 78, 350, 180, 300, "azure_attack", false, 180, 1.25, 2.3, 110, 90),
    VERMILION_FLAME_TYRANT("vermilion_flame_tyrant", "monster.myriad.vermilion_flame_tyrant", EntityTypes.HOGLIN, 11340, 96, 420, 220, 300, "vermilion_spell", false, 190, 1.35, 2.0, 90, 120),
    GALE_ROC_MATRIARCH("gale_roc_matriarch", "monster.myriad.gale_roc_matriarch", EntityTypes.PHANTOM, 9450, 72, 380, 200, 300, "gale_haste", false, 170, 1.8, 2.0, 80, 90),
    OBSIDIAN_TORTOISE_WARDEN("obsidian_tortoise_warden", "monster.myriad.obsidian_tortoise_warden", EntityTypes.IRON_GOLEM, 13860, 66, 450, 240, 300, "obsidian_guard", false, 220, 0.9, 2.2, 160, 130);

    private final String id;
    private final String nameKey;
    private final EntityType<?> entityType;
    private final double health;
    private final double attack;
    private final int xp;
    private final int gold;
    /** 重生秒数（0 = 不刷新） */
    private final int respawnSeconds;
    /** 掉落 buff：null=无 spirit=灵石灌顶 beast=荒古灌顶 */
    private final String buff;
    /** 是否为击杀者阵营全员发放金币 */
    private final boolean teamGold;
    /** 仇恨值上限（攻击者超出 10 格后每秒 -20，归零回血回点） */
    private final int hatred;
    /** 移动速度（格/秒） */
    private final double moveSpeed;
    /** 攻击距离（格） */
    private final double attackRange;
    private final double physicalDefense;
    private final double magicDefense;

    MonsterType(String id, String nameKey, EntityType<?> entityType, double health, double attack,
            int xp, int gold, int respawnSeconds, String buff, boolean teamGold, int hatred,
            double moveSpeed, double attackRange, double physicalDefense, double magicDefense) {
        this.id = id;
        this.nameKey = nameKey;
        this.entityType = entityType;
        this.health = health;
        this.attack = attack;
        this.xp = xp;
        this.gold = gold;
        this.respawnSeconds = respawnSeconds;
        this.buff = buff;
        this.teamGold = teamGold;
        this.hatred = hatred;
        this.moveSpeed = moveSpeed;
        this.attackRange = attackRange;
        this.physicalDefense = physicalDefense;
        this.magicDefense = magicDefense;
    }

    public int getHatred() {
        return hatred;
    }

    public double getMoveSpeed() {
        return moveSpeed;
    }

    public double getAttackRange() {
        return attackRange;
    }

    public double getPhysicalDefense() { return physicalDefense; }
    public double getMagicDefense() { return magicDefense; }

    public String getId() {
        return id;
    }

    public String getNameKey() {
        return nameKey;
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    public double getHealth() {
        return health;
    }

    public double getAttack() {
        return attack;
    }

    public int getXp() {
        return xp;
    }

    public int getGold() {
        return gold;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public String getBuff() {
        return buff;
    }

    public boolean isTeamGold() {
        return teamGold;
    }

    /** 会在开局 30 秒后首次生成的六种小型野怪。 */
    public boolean isSmallMonster() {
        return this == FROST_HARE || this == EMBER_IMP || this == SHADOW_FOX
                || this == THUNDER_BADGER || this == MIASMA_TOAD || this == STONE_SHELL_CRAB;
    }

    /** 会在开局 60 秒后首次生成的三种中型野怪。 */
    public boolean isMediumMonster() {
        return this == GOLD_PIG || this == SPRING_REEF_GUARDIAN || this == FROST_WEB_MATRIARCH;
    }

    /** 四种会在大型刷新点随机出现的首领。 */
    public boolean isLargeBoss() {
        return this == AZURE_HORN_GUARDIAN || this == VERMILION_FLAME_TYRANT
                || this == GALE_ROC_MATRIARCH || this == OBSIDIAN_TORTOISE_WARDEN;
    }

    public static MonsterType byId(String id) {
        for (MonsterType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
