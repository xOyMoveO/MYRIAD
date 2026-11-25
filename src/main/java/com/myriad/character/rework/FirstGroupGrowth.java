package com.myriad.character.rework;

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
    public static final List<String> ATTRIBUTES=List.of("strength","wisdom","speed","defense","cooldown");
    public static final long PRICE_OVERFLOW = -1;
    public static final int HASTE_PER_POINT = 3;
    private static final List<Long> PRICES = purchasePrices();

    private static List<Long> purchasePrices() {
        var prices = new ArrayList<Long>();
        long price = 360;
        while (true) {
            prices.add(price);
            // Round the extra fifth of the previous price without multiplying a large integer.
            long increase = price / 5 + (price % 5 >= 3 ? 1 : 0);
            if (price > Long.MAX_VALUE - increase) return List.copyOf(prices);
            price += increase;
        }
    }

    /** A rank counts all previous purchases, including free training purchases. */
    public static long price(int rank) {
        return rank < PRICES.size() ? PRICES.get(Math.max(0, rank)) : PRICE_OVERFLOW;
    }

    public static String purchaseError(int rank, int gold, boolean free) {
        if (rank == Integer.MAX_VALUE) return "message.myriad.g1.attribute_rank_overflow";
        if (free) return null;
        long price = price(rank);
        if (price == PRICE_OVERFLOW) return "message.myriad.g1.attribute_price_overflow";
        return price > gold ? "message.myriad.equip_no_gold" : null;
    }

    public static double purchasedPhysicalAttack(int strengthPoints) {
        return 15.0 * strengthPoints;
    }

    public static double purchasedSpellPower(int wisdomPoints) {
        return 20.0 * wisdomPoints;
    }

    /** Innate physical attack for the active roster, before purchases and modifiers. */
    public static double baselinePhysicalAttack(CharacterType role) {
        return role == CharacterType.LUOXIA ? role.initialAd() : 65.0;
    }

    /** The active roster has no innate spell power growth with character level. */
    public static double baselineSpellPower() {
        return 30.0;
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
        if(!TrainingGround.canFight(p)||!BaseRules.canShop(p))return false;
        int rank=d.firstGroupAttribute(key);
        boolean free=TrainingMode.isNoGoldCost(p);
        String error=purchaseError(rank,d.getGold(),free);
        if(error!=null){p.sendSystemMessage(Component.translatable(error));return false;}
        if(!free)d.spendGold((int)price(rank));
        d.setFirstGroupAttribute(key,rank+1);apply(p,d);PlayerDataProvider.syncToClient(p);return true;
    }
    public static void initialize(ServerPlayer p,PlayerData d){
        if(!FirstGroupSkills.active(d.getCharacterId()))return;
        if(FirstGroupSkills.SECOND_GROUP.contains(d.getCharacterId())&&SecondGroupMigration.RETIRED.stream().anyMatch(k->d.getBuff(k)>0))
            SecondGroupMigration.clearLegacy(p,d);
        // One-time migration only: old skill ranks cannot unlock new mutually exclusive branches.
        if(d.getSkillLevels().keySet().stream().anyMatch(id->{var n=FirstGroupSkills.byId(id);return n==null||!n.role().equals(d.getCharacterId());})){
            d.refundAllSkillPointsForTraining();com.myriad.character.ability.AbilityRuntime.clear(p);p.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            com.myriad.character.rework.SecondGroupMigration.clearLegacy(p,d);
        }
        d.setLevel(Math.clamp(d.getLevel(),1,20));d.ensureLevelRewards(d.getLevel());
        var role=CharacterType.byId(d.getCharacterId());
        base(p,ModAttributes.PHYSICAL_ATTACK,baselinePhysicalAttack(role));
        base(p,ModAttributes.SPELL_POWER,baselineSpellPower());
        base(p,ModAttributes.ATTACK_SPEED,.70);base(p,Attributes.MOVEMENT_SPEED,.10);
        base(p,ModAttributes.PHYSICAL_DEFENSE,45);base(p,ModAttributes.MAGIC_RESISTANCE,45);
        base(p,ModAttributes.MAX_MANA,700);base(p,ModAttributes.MANA_REGEN,4);base(p,ModAttributes.HEALTH_REGEN,2);
        base(p,ModAttributes.CRIT_DAMAGE,150);
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
        modifier(p,ModAttributes.ATTACK_SPEED,"speed_as",.06*d.firstGroupAttribute("speed"));
        modifier(p,Attributes.MOVEMENT_SPEED,"speed_ms",.0015*d.firstGroupAttribute("speed"));
        modifier(p,ModAttributes.PHYSICAL_DEFENSE,"defense_pd",5.0*d.firstGroupAttribute("defense"));
        modifier(p,ModAttributes.MAGIC_RESISTANCE,"defense_mr",5.0*d.firstGroupAttribute("defense"));
        modifier(p,ModAttributes.COOLDOWN_REDUCTION,"cooldown",(double)HASTE_PER_POINT*d.firstGroupAttribute("cooldown"));
        for(String key:ATTRIBUTES)d.setBuff("g1_attribute_"+key,d.firstGroupAttribute(key));
        PlayerHealthSystem.recalculate(p);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket(p.getId(),p.getAttributes().getSyncableAttributes()));
    }
}
