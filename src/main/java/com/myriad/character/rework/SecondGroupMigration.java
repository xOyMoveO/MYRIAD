package com.myriad.character.rework;

import com.myriad.player.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.Set;

/** Only retired second-batch skill state; never resets gold, levels, team, equipment or the match. */
public final class SecondGroupMigration {
    private SecondGroupMigration(){}
    public static final Set<String> RETIRED=Set.of("lingyuan_passive","poyunshi","huaxingweijian","wanjianjue","sanqianguizong",
            "hanze_passive","bingzhuishu","shuangdun","jueduilingdu","qianlibingfeng","qingluo_passive","qingtengyu","huxindanzhen","qiansiduoe","wanmuchunhui",
            "chixiao_passive","chixiao_passive_cd","duanyuezhan","zhuihunta","zhuihunta_recast","chixiaofanfeng","pojunwuhui",
            "yunsheng_passive","zhihechuanyin","dingqisuo","huiyinjiebiao","zhongshengguizong");
    public static void clearLegacy(ServerPlayer p,PlayerData d){
        if(!FirstGroupSkills.SECOND_GROUP.contains(d.getCharacterId()))return;
        com.myriad.character.skills.lingyuan.LingYuanSkills.clearFor(p.getUUID());
        com.myriad.character.skills.hanze.HanzeSkills.clearFor(p.getUUID());
        com.myriad.character.skills.qingluo.QingLuoSkills.clearFor(p.getUUID());
        com.myriad.character.skills.chixiao.ChiXiaoSkills.clearFor(p.getUUID());
        com.myriad.character.skills.yunsheng.YunShengSkills.clearFor(p);
        for(String key:RETIRED){d.setBuff(key,0);d.setCooldown(key,0);}
        d.setSwordQi(0);d.setFlyingSwords(0);d.setPlacementType(0);
        ModShieldSystem.removeSource(p,"hanze_shuangdun");ModShieldSystem.removeSource(p,"qingluo_huxindanzhen");
        var as=p.getAttribute(com.myriad.registry.ModAttributes.ATTACK_SPEED);
        if(as!=null)as.removeModifier(net.minecraft.resources.Identifier.fromNamespaceAndPath("myriad","lingyuan_sword_unity"));
    }
}
