package com.myriad.character.rework;

import static com.myriad.character.rework.AttributeSpecializations.Option.*;

import com.myriad.character.CharacterType;
import com.myriad.game.*;
import com.myriad.player.*;
import com.myriad.registry.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import java.util.*;

/** Per-match attribute purchases and the roster's baseline growth. */
public final class FirstGroupGrowth {
    private FirstGroupGrowth(){}
    public static final String ATTACK_SPEED = "speed";
    public static final String MOVEMENT_SPEED = "movement_speed";
    public static final List<String> ATTRIBUTES =
            List.of("strength", "wisdom", ATTACK_SPEED, "defense", "cooldown", MOVEMENT_SPEED);
    public static final int MOVEMENT_SPEED_PRICE = 600;
    public static final int MOVEMENT_SPEED_UPGRADE_TICK = 8 * 60 * 20;
    public static final double OPENING_MOVEMENT_SPEED_GAIN = .0025;
    public static final double LATE_MOVEMENT_SPEED_GAIN = .005;
    public static final String MOVEMENT_SPEED_RATE_KEY = "myriad.movement_speed_per_point";
    private static final Identifier MOVEMENT_SPEED_MODIFIER = Identifier.parse("myriad:g1_attribute_speed_ms");
    public static final long PRICE_OVERFLOW = -1;
    public static final int STARTING_PRICE = 360;
    public static final int PRICE_STEP_DIVISOR = 5;
    public static final double HASTE_PER_POINT = 12.5;
    public static final double BASE_ATTACK_SPEED = .70;
    public static final double BASE_MOVEMENT_SPEED = .10;
    public static final double BASE_DEFENSE = 45;
    public static final double BASE_MANA_REGEN = 4;
    public static final double BASE_HEALTH_REGEN = 2;
    public static final double BASE_CRIT_DAMAGE = 150;
    public static final double SPEED_ATTACK_GAIN = .06;
    public static final double DEFENSE_GAIN = 5.0;
    private static final List<Long> PRICES = purchasePrices();

    public record InnateStats(double physicalAttack, double spellPower, double physicalDefense,
                              double magicResistance, double attackSpeed, double healthRegen, double manaRegen) {}

    private record PerLevel(int physicalAttack, int spellPower, int physicalDefense, int magicResistance,
                            double attackSpeed, double healthRegen, double manaRegen) {}

    public static InnateStats innateStats(CharacterType role, int level) {
        int upgrades = Math.clamp(level, 1, 20) - 1;
        if (upgrades == 0) {
            return new InnateStats(baselinePhysicalAttack(role), baselineSpellPower(), BASE_DEFENSE, BASE_DEFENSE,
                    BASE_ATTACK_SPEED, BASE_HEALTH_REGEN, BASE_MANA_REGEN);
        }
        var growth = switch (role) {
            case YINGXIAO -> new PerLevel(19, 0, 6, 5, .030, .35, .30);
            case SULAN    -> new PerLevel(17, 0, 7, 6, .040, .45, .50);
            case SHUO     -> new PerLevel(16, 0, 8, 6, .030, .50, .30);
            case TIANXING -> new PerLevel(6, 12, 6, 6, .035, .50, .40);
            case KEHENG   -> new PerLevel(4, 15, 6, 5, .025, .40, .50);
            case RIVET    -> new PerLevel(12, 0, 10, 8, .018, .75, .35);
            case CHIXIAO  -> new PerLevel(13, 0, 10, 7, .022, .70, .35);
            case FENJUE   -> new PerLevel(13, 0, 9, 8, .020, .65, .45);
            case YIKE     -> new PerLevel(3, 18, 5, 4, .015, .30, .50);
            case LINGYUAN -> new PerLevel(4, 14, 6, 6, .025, .50, .35);
            case HANZE    -> new PerLevel(0, 14, 8, 8, .015, .65, .45);
            case XUANCHEN -> new PerLevel(0, 19, 5, 6, .015, .35, .55);
            case LUOXIA   -> new PerLevel(20, 0, 6, 4, .045, .35, .30);
            case QINGLUO  -> new PerLevel(0, 15, 7, 8, .012, .40, .60);
            case YUNSHENG -> new PerLevel(0, 16, 7, 7, .015, .40, .60);
        };
        return new InnateStats(
                baselinePhysicalAttack(role) + growth.physicalAttack() * upgrades,
                baselineSpellPower() + growth.spellPower() * upgrades,
                BASE_DEFENSE + growth.physicalDefense() * upgrades,
                BASE_DEFENSE + growth.magicResistance() * upgrades,
                BASE_ATTACK_SPEED + growth.attackSpeed() * upgrades,
                BASE_HEALTH_REGEN + growth.healthRegen() * upgrades,
                BASE_MANA_REGEN + growth.manaRegen() * upgrades);
    }

    private static List<Long> purchasePrices() {
        var prices = new ArrayList<Long>();
        long price = STARTING_PRICE;
        while (true) {
            prices.add(price);
            // Round the extra fifth of the previous price without multiplying a large integer.
            long increase = price / PRICE_STEP_DIVISOR
                    + (price % PRICE_STEP_DIVISOR >= (PRICE_STEP_DIVISOR + 1) / 2 ? 1 : 0);
            if (price > Long.MAX_VALUE - increase) return List.copyOf(prices);
            price += increase;
        }
    }

    /** A rank counts all previous purchases, including free training purchases. */
    public static long price(int rank) {
        return rank < PRICES.size() ? PRICES.get(Math.max(0, rank)) : PRICE_OVERFLOW;
    }

    public static long price(String key, int rank) {
        return MOVEMENT_SPEED.equals(key) ? MOVEMENT_SPEED_PRICE : price(rank);
    }

    public static double skillHaste(int purchasedPoints) {
        return HASTE_PER_POINT * purchasedPoints;
    }

    public static double nextAttackSpeedGain(CharacterType role, int level, int rank, int specialization) {
        return nextAttackSpeedGain(innateStats(role, level), rank, specialization);
    }

    private static double nextAttackSpeedGain(InnateStats innate, int rank, int specialization) {
        return nextGain(ModAttributes.ATTACK_SPEED, innate.attackSpeed(), SPEED_ATTACK_GAIN, rank,
                FLURRY.value(rank, specialization), FLURRY.value(rank + 1, specialization));
    }

    public static double nextMovementSpeedGain(int rank, double perPoint) {
        return nextGain(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED, perPoint, rank);
    }

    public static double movementSpeedPerPoint(long matchElapsedTicks) {
        return matchElapsedTicks >= MOVEMENT_SPEED_UPGRADE_TICK ? LATE_MOVEMENT_SPEED_GAIN : OPENING_MOVEMENT_SPEED_GAIN;
    }

    public static double movementSpeedPerPoint(ServerPlayer player) {
        var match = GameManager.get();
        if (match == null || match.getPhase() != GameManager.Phase.PLAYING || TrainingGround.isTraining(player)
                || !match.isParticipant(player.getUUID())) return OPENING_MOVEMENT_SPEED_GAIN;
        return movementSpeedPerPoint(match.getMatchElapsedTicks(player.level().getServer().overworld().getGameTime()));
    }

    public static void refreshMovementSpeed(ServerPlayer player, PlayerData data) {
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        var modifier = attribute.getModifier(MOVEMENT_SPEED_MODIFIER);
        double bonus = FirstGroupSkills.active(data.getCharacterId())
                ? data.firstGroupAttribute(MOVEMENT_SPEED) * movementSpeedPerPoint(player) : 0;
        if (bonus == (modifier == null ? 0 : modifier.amount())) return;
        attribute.removeModifier(MOVEMENT_SPEED_MODIFIER);
        if (bonus != 0) attribute.addTransientModifier(
                new AttributeModifier(MOVEMENT_SPEED_MODIFIER, bonus, AttributeModifier.Operation.ADD_VALUE));
    }

    private static double nextGain(Holder<Attribute> attribute, double base, double perPoint, int rank) {
        return nextGain(attribute, base, perPoint, rank, 0, 0);
    }

    private static double nextGain(Holder<Attribute> attribute, double base, double perPoint, int rank,
                                   double specialization, double nextSpecialization) {
        return attribute.value().sanitizeValue(base + perPoint * ((double) rank + 1) + nextSpecialization)
                - attribute.value().sanitizeValue(base + perPoint * rank + specialization);
    }

    private static boolean improvesPermanentStats(InnateStats innate, String key, int rank,
                                                  int specialization, double movementPerPoint) {
        return switch (key) {
            case "strength" -> nextGain(ModAttributes.PHYSICAL_ATTACK, innate.physicalAttack(), purchasedPhysicalAttack(1), rank) > 0;
            case "wisdom" -> nextGain(ModAttributes.SPELL_POWER, innate.spellPower(), purchasedSpellPower(1), rank) > 0;
            case ATTACK_SPEED -> nextAttackSpeedGain(innate, rank, specialization) > 0;
            case MOVEMENT_SPEED -> nextMovementSpeedGain(rank, movementPerPoint) > 0;
            case "defense" -> nextGain(ModAttributes.PHYSICAL_DEFENSE, innate.physicalDefense(), DEFENSE_GAIN, rank,
                    STEELBONE.value(rank, specialization), STEELBONE.value(rank + 1, specialization)) > 0
                    || nextGain(ModAttributes.MAGIC_RESISTANCE, innate.magicResistance(), DEFENSE_GAIN, rank,
                    SPIRIT_ARMOR.value(rank, specialization), SPIRIT_ARMOR.value(rank + 1, specialization)) > 0;
            case "cooldown" -> true;
            default -> false;
        };
    }

    public static String purchaseError(CharacterType role, String key, int rank, int gold, boolean free) {
        return purchaseError(role, 1, key, rank, gold, free, 0, OPENING_MOVEMENT_SPEED_GAIN);
    }

    public static String purchaseError(CharacterType role, int level, String key, int rank, int gold, boolean free,
                                       int specialization, double movementPerPoint) {
        if (rank == Integer.MAX_VALUE) return "message.myriad.g1.attribute_rank_overflow";
        if (!improvesPermanentStats(innateStats(role, level), key, rank, specialization, movementPerPoint))
            return "message.myriad.g1.attribute_no_gain";
        if (free) return null;
        long price = price(key, rank);
        if (price == PRICE_OVERFLOW) return "message.myriad.g1.attribute_price_overflow";
        return price > gold ? "message.myriad.equip_no_gold" : null;
    }

    public static double purchasedPhysicalAttack(int strengthPoints) {
        return 15.0 * strengthPoints;
    }

    public static double purchasedSpellPower(int wisdomPoints) {
        return 20.0 * wisdomPoints;
    }

    /** Level-one innate physical attack, the reference for bonus physical damage. */
    public static double baselinePhysicalAttack(CharacterType role) {
        return role == CharacterType.LUOXIA ? role.initialAd() : 65.0;
    }

    public static double bonusPhysicalAttack(CharacterType role, double effectiveAttack) {
        return effectiveAttack - baselinePhysicalAttack(role);
    }

    /** Level-one innate spell power, also used to preserve migrated damage baselines. */
    public static double baselineSpellPower() {
        return 30.0;
    }

    public static double maxMana(CharacterType role, int level) {
        int perLevel = switch (role) {
            case YINGXIAO, LINGYUAN, LUOXIA, SHUO -> 24;
            case TIANXING, RIVET, CHIXIAO, HANZE -> 28;
            case FENJUE, XUANCHEN, YIKE -> 32;
            case SULAN, QINGLUO, YUNSHENG, KEHENG -> 36;
        };
        return 700 + perLevel * (Math.clamp(level, 1, 20) - 1);
    }

    public static double health(String role,int level){
        int[] pair=switch(role){
            case "rivet"->new int[]{700,110};
            case "luoxia"->new int[]{575,75};
            case "keheng"->new int[]{600,85};
            case "yike"->new int[]{550,75};
            case "shuo"->new int[]{640,100};
            case "yingxiao"->new int[]{com.myriad.character.skills.yingxiao.rework.YingxiaoRework.INITIAL_HEALTH,
                    com.myriad.character.skills.yingxiao.rework.YingxiaoRework.HEALTH_PER_LEVEL};
            case "tianxing"->new int[]{com.myriad.character.skills.tianxing.rework.TianxingRework.INITIAL_HEALTH,
                    com.myriad.character.skills.tianxing.rework.TianxingRework.HEALTH_PER_LEVEL};
            case "fenjue"->new int[]{com.myriad.character.skills.fenjue.rework.FenjueRework.INITIAL_HEALTH,
                    com.myriad.character.skills.fenjue.rework.FenjueRework.HEALTH_PER_LEVEL};
            case "xuanchen"->new int[]{com.myriad.character.skills.xuanchen.rework.XuanchenRework.INITIAL_HEALTH,
                    com.myriad.character.skills.xuanchen.rework.XuanchenRework.HEALTH_PER_LEVEL};
            case "sulan"->new int[]{com.myriad.character.skills.sulan.rework.SulanRework.INITIAL_HEALTH,
                    com.myriad.character.skills.sulan.rework.SulanRework.HEALTH_PER_LEVEL};
            case "lingyuan"->new int[]{com.myriad.character.skills.lingyuan.rework.LingyuanRework.INITIAL_HEALTH,com.myriad.character.skills.lingyuan.rework.LingyuanRework.HEALTH_PER_LEVEL};
            case "hanze"->new int[]{com.myriad.character.skills.hanze.rework.HanzeRework.INITIAL_HEALTH,com.myriad.character.skills.hanze.rework.HanzeRework.HEALTH_PER_LEVEL};
            case "qingluo"->new int[]{com.myriad.character.skills.qingluo.rework.QingluoRework.INITIAL_HEALTH,com.myriad.character.skills.qingluo.rework.QingluoRework.HEALTH_PER_LEVEL};
            case "chixiao"->new int[]{com.myriad.character.skills.chixiao.rework.ChixiaoRework.INITIAL_HEALTH,com.myriad.character.skills.chixiao.rework.ChixiaoRework.HEALTH_PER_LEVEL};
            case "yunsheng"->new int[]{com.myriad.character.skills.yunsheng.rework.YunshengRework.INITIAL_HEALTH,com.myriad.character.skills.yunsheng.rework.YunshengRework.HEALTH_PER_LEVEL};
            default->throw new IllegalArgumentException(role);};
        return pair[0]+pair[1]*Math.clamp(level-1,0,19);
    }
    public static boolean buy(ServerPlayer p,PlayerData d,String key){
        if(!com.myriad.game.ModState.isEnabled()||!FirstGroupSkills.active(d.getCharacterId())||!ATTRIBUTES.contains(key))return false;
        if(!BaseRules.canBuyAttributes(p))return false;
        int rank=d.firstGroupAttribute(key);
        boolean free=TrainingMode.isNoGoldCost(p);
        String error=purchaseError(CharacterType.byId(d.getCharacterId()),d.getLevel(),key,rank,d.getGold(),free,
                d.specialization(key),movementSpeedPerPoint(p));
        if(error!=null){p.sendSystemMessage(Component.translatable(error));return false;}
        if(!free)d.spendGold((int)price(key,rank));
        d.setFirstGroupAttribute(key,rank+1);apply(p,d);PlayerDataProvider.syncToClient(p);return true;
    }
    public static void initialize(ServerPlayer p,PlayerData d){
        if(!FirstGroupSkills.active(d.getCharacterId()))return;
        if(FirstGroupSkills.SECOND_GROUP.contains(d.getCharacterId())&&SecondGroupMigration.RETIRED.stream().anyMatch(k->d.getBuff(k)>0))
            SecondGroupMigration.clearLegacy(p,d);
        // One-time migration only: old skill ranks cannot unlock new mutually exclusive branches.
        if(d.automaticPassives() && d.getSkillLevels().keySet().stream().anyMatch(id->{var n=FirstGroupSkills.byId(id);return n==null||!n.role().equals(d.getCharacterId());})){
            d.refundAllSkillPointsForTraining();com.myriad.character.ability.AbilityRuntime.clear(p);p.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            com.myriad.character.rework.SecondGroupMigration.clearLegacy(p,d);
        }
        d.setLevel(Math.clamp(d.getLevel(),1,20));d.ensureLevelRewards(d.getLevel());
        var role=CharacterType.byId(d.getCharacterId());
        var innate = innateStats(role, d.getLevel());
        base(p, ModAttributes.PHYSICAL_ATTACK, innate.physicalAttack());
        base(p, ModAttributes.SPELL_POWER, innate.spellPower());
        base(p, ModAttributes.ATTACK_SPEED, innate.attackSpeed());
        base(p, ModAttributes.PHYSICAL_DEFENSE, innate.physicalDefense());
        base(p, ModAttributes.MAGIC_RESISTANCE, innate.magicResistance());
        base(p, ModAttributes.MANA_REGEN, innate.manaRegen());
        base(p, ModAttributes.HEALTH_REGEN, innate.healthRegen());
        base(p, Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED);
        base(p, ModAttributes.MAX_MANA, maxMana(role, d.getLevel()));
        d.setMana((float)Math.min(d.getMana(),p.getAttributeValue(ModAttributes.MAX_MANA)));
        base(p,ModAttributes.CRIT_DAMAGE,BASE_CRIT_DAMAGE);
        apply(p,d);
    }
    private static void base(ServerPlayer p,Holder<Attribute> attr,double amount){var a=p.getAttribute(attr);if(a!=null)a.setBaseValue(amount);}
    private static void modifier(ServerPlayer p,Holder<Attribute> attr,String key,double amount){
        var a=p.getAttribute(attr);if(a==null)return;var id=Identifier.fromNamespaceAndPath("myriad","g1_attribute_"+key);
        a.removeModifier(id);if(amount!=0)a.addTransientModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_VALUE));
    }
    public static void apply(ServerPlayer p,PlayerData d){
        if(!FirstGroupSkills.active(d.getCharacterId()))return;
        modifier(p,ModAttributes.PHYSICAL_ATTACK,"strength",purchasedPhysicalAttack(d.firstGroupAttribute("strength")));
        modifier(p,ModAttributes.SPELL_POWER,"wisdom",purchasedSpellPower(d.firstGroupAttribute("wisdom")));
        modifier(p,ModAttributes.ATTACK_SPEED,"speed_as",SPEED_ATTACK_GAIN*d.firstGroupAttribute(ATTACK_SPEED));
        refreshMovementSpeed(p,d);
        modifier(p,ModAttributes.PHYSICAL_DEFENSE,"defense_pd",DEFENSE_GAIN*d.firstGroupAttribute("defense"));
        modifier(p,ModAttributes.MAGIC_RESISTANCE,"defense_mr",DEFENSE_GAIN*d.firstGroupAttribute("defense"));
        modifier(p, ModAttributes.FLAT_ARMOR_PEN, "specialization_armor_pierce", ARMOR_PIERCE.value(d));
        modifier(p, ModAttributes.FLAT_MAGIC_PEN, "specialization_magic_pierce", MAGIC_PIERCE.value(d));
        modifier(p, ModAttributes.ATTACK_SPEED, "specialization_flurry", FLURRY.value(d));
        modifier(p, Attributes.MOVEMENT_SPEED, "specialization_lightfoot", LIGHTFOOT.value(d));
        modifier(p, ModAttributes.PHYSICAL_DEFENSE, "specialization_steelbone", STEELBONE.value(d));
        modifier(p, ModAttributes.MAGIC_RESISTANCE, "specialization_spirit_armor", SPIRIT_ARMOR.value(d));
        modifier(p, ModAttributes.MANA_REGEN, "specialization_breath", BREATH.value(d));
        for (String key : ATTRIBUTES) {
            d.setBuff("g1_attribute_" + key, d.firstGroupAttribute(key));
            d.setBuff(AttributeSpecializations.COUNTER_PREFIX + key, d.specialization(key));
        }
        PlayerHealthSystem.recalculate(p);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(p.getId(),p.getAttributes().getSyncableAttributes()));
    }
}
