package com.myriad.character;

import java.util.ArrayList;
import java.util.List;

import com.myriad.character.skills.xuanchen.ChuanSongZhenSkill;
import com.myriad.character.skills.fenjue.FenJueBaoFaSkill;
import com.myriad.character.skills.fenjue.FenJuePassiveSkill;
import com.myriad.character.skills.fenjue.FenTianHuoHaiSkill;
import com.myriad.character.skills.yingxiao.HuaYingSkill;
import com.myriad.character.skills.fenjue.LieYanZhanSkill;
import com.myriad.character.skills.fenjue.RongYanLaoLongSkill;
import com.myriad.character.skills.tianxing.JiuTianShenLeiSkill;
import com.myriad.character.skills.tianxing.LeiJieZhanSkill;
import com.myriad.character.skills.yingxiao.LingDunSkill;
import com.myriad.character.skills.xuanchen.LingFuSkill;
import com.myriad.character.skills.xuanchen.MoYangHuZhenSkill;
import com.myriad.character.skills.tianxing.ShanLeiSkill;
import com.myriad.character.skills.tianxing.TianLeiHuTiSkill;
import com.myriad.character.skills.tianxing.TianXingPassiveSkill;
import com.myriad.character.skills.xuanchen.XuanChenPassiveSkill;
import com.myriad.character.skills.yingxiao.YingShaSkill;
import com.myriad.character.skills.yingxiao.YingXiSkill;
import com.myriad.character.skills.yingxiao.YingXiaoPassiveSkill;
import com.myriad.character.skills.xuanchen.ZhouTianFuZhenSkill;
import com.myriad.character.skills.sulan.SuLanPassiveSkill;
import com.myriad.character.skills.sulan.JiFengTuCiSkill;
import com.myriad.character.skills.sulan.LingFengBiaoJiSkill;
import com.myriad.character.skills.sulan.FengQiangSkill;
import com.myriad.character.skills.sulan.YuFengQianLiSkill;
import com.myriad.character.skills.lingyuan.LingYuanSkills;
import com.myriad.character.skills.hanze.HanzeSkills;
import com.myriad.character.skills.qingluo.QingLuoSkills;
import com.myriad.character.skills.chixiao.ChiXiaoSkills;
import com.myriad.character.skills.yunsheng.YunShengSkills;
import com.myriad.character.skills.lige.LiGeSkills;
import com.myriad.character.skills.guanlan.GuanLanSkills;
import com.myriad.character.skills.xuanjia.XuanJiaSkills;
import com.myriad.character.skills.wuxiang.WuxiangSkills;
import com.myriad.character.skills.feiyu.FeiYuSkills;
import com.myriad.character.skills.shenli.ShenLiSkills;
import com.myriad.character.skills.wanyue.WanyueSkills;
import com.myriad.character.skills.luoheng.LuoHengSkills;
import com.myriad.character.skills.zhechuan.ZheChuanSkills;
import com.myriad.game.Faction;
import com.myriad.character.skills.mingzhu.MingZhuSkills;

/**
 * 角色表（策划案第三节）：1 被动 + 若干主动技能。
 * 技能槽位通用按键：0=Q, 1=W, 2=E, 3=R, 4=F，预留至 5 个；
 * 未填写的槽位返回 null，由调用方跳过。
 * 当前角色池由玄霄宗与幽冥宗共享，旧归属字段仅保留阵营侧展示位置。
 */
public enum CharacterType {
    YINGXIAO("yingxiao", Faction.XUANXIAO, new Skill[] { new YingXiaoPassiveSkill() },
            new YingXiSkill(), new HuaYingSkill(), new YingShaSkill(), new LingDunSkill()) {
        @Override public double initialHpRegen() { return 3.5; }
        @Override public double initialManaRegen() { return 9.1; }
        @Override public double initialMs() { return 0.0966; }
        @Override public double initialSp() { return 0.0; }
        @Override
        public double initialHp() {
            return 1260.0;
        }

        @Override
        public double initialAd() {
            return 56.0;
        }

        @Override
        public double initialMp() {
            return 700.0;
        }

        @Override
        public double initialPd() {
            return 56.0;
        }

        @Override
        public double initialMr() {
            return 48.0;
        }

        @Override
        public double initialAs() {
            return 0.58;
        }

        /** 升级成长：以下各项均为每级实际增加值。 */
        @Override
        public double growthHp() {
            return 472.5;
        }
        @Override
        public double growthAd() {
            return 22.848;
        }

        @Override
        public double growthSp() {
            return 0.0;
        }

        @Override
        public double growthAs() {
            return 0.028;
        }

        @Override
        public double growthMs() {
            return 0.0046;
        }

        @Override
        public double growthPd() {
            return 6.0;
        }

        @Override
        public double growthMr() {
            return 4.0;
        }

        @Override
        public double growthMp() {
            return 56.0;
        }
    },

    TIANXING("tianxing", Faction.YOUMING, new Skill[] { new TianXingPassiveSkill() },
            new ShanLeiSkill(), new LeiJieZhanSkill(), new TianLeiHuTiSkill(), new JiuTianShenLeiSkill()) {
        @Override public double initialSp() { return 0.0; }
        @Override
        public double initialHp() {
            return 1512.0;
        }

        @Override
        public double initialAd() {
            return 52.0;
        }

        @Override
        public double initialMp() {
            return 910.0;
        }

        @Override
        public double initialPd() {
            return 68.0;
        }

        @Override
        public double initialMr() {
            return 48.0;
        }

        @Override
        public double initialAs() {
            return 0.54;
        }

        @Override
        public double initialMs() {
            return 0.092;
        }

        @Override
        public double initialManaRegen() {
            return 9.1;
        }

        @Override
        public double initialHpRegen() {
            return 4.55;
        }

        /** 升级成长：以下各项均为每级实际增加值。 */
        @Override
        public double growthHp() {
            return 604.8;
        }
        @Override
        public double growthAd() {
            return 22.848;
        }

        @Override
        public double growthSp() {
            return 0.0;
        }

        @Override
        public double growthAs() {
            return 0.028;
        }

        @Override
        public double growthMs() {
            return 0.000736;
        }

        @Override
        public double growthPd() {
            return 8.0;
        }

        @Override
        public double growthMr() {
            return 2.0;
        }

        @Override
        public double growthMp() {
            return 42.0;
        }
    },

    FENJUE("fenjue", Faction.YOUMING, new Skill[] { new FenJuePassiveSkill() },
            new LieYanZhanSkill(), new RongYanLaoLongSkill(), new FenJueBaoFaSkill(), new FenTianHuoHaiSkill()) {
        @Override public double initialSp() { return 0.0; }
        @Override
        public double initialHp() {
            return 1554.0;
        }

        @Override
        public double initialAd() {
            return 48.0;
        }

        @Override
        public double initialMp() {
            return 980.0;
        }

        @Override
        public double initialPd() {
            return 72.0;
        }

        @Override
        public double initialMr() {
            return 48.0;
        }

        @Override
        public double initialAs() {
            return 0.5;
        }

        @Override
        public double initialMs() {
            return 0.0874;
        }

        @Override
        public double initialManaRegen() {
            return 9.8;
        }

        @Override
        public double initialHpRegen() {
            return 4.2;
        }

        /** 升级成长：以下各项均为每级实际增加值。 */
        @Override
        public double growthHp() {
            return 661.5;
        }

        @Override
        public double growthAd() {
            return 26.656;
        }

        @Override
        public double growthSp() {
            return 0.0;
        }

        @Override
        public double growthAs() {
            return 0.0224;
        }

        @Override
        public double growthMs() {
            return 0.000736;
        }

        @Override
        public double growthPd() {
            return 8.0;
        }

        @Override
        public double growthMr() {
            return 4.0;
        }

        @Override
        public double growthMp() {
            return 56.0;
        }
    },

    XUANCHEN("xuanchen", Faction.XUANXIAO, new Skill[] { new XuanChenPassiveSkill() },
            new LingFuSkill(), new ChuanSongZhenSkill(), new MoYangHuZhenSkill(), new ZhouTianFuZhenSkill()) {
        @Override
        public double initialHp() {
            return 1260.0;
        }

        @Override
        public double initialAd() {
            return 12.0;
        }

        @Override
        public double initialSp() {
            return 48.0;
        }

        @Override
        public double initialMp() {
            return 910.0;
        }

        @Override
        public double initialPd() {
            return 44.0;
        }

        @Override
        public double initialMr() {
            return 56.0;
        }

        @Override
        public double initialAs() {
            return 0.5;
        }

        @Override
        public double initialMs() {
            return 0.092;
        }

        @Override
        public double initialManaRegen() {
            return 14.0;
        }

        @Override
        public double initialHpRegen() {
            return 3.15;
        }

        /** 升级成长：以下各项均为每级实际增加值。 */
        @Override
        public double growthHp() {
            return 491.4;
        }
        @Override
        public double growthAd() {
            return 3.808;
        }

        @Override
        public double growthSp() {
            return 23.8;
        }

        @Override
        public double growthAs() {
            return 0.0168;
        }

        @Override
        public double growthMs() {
            return 0.000828;
        }

        @Override
        public double growthPd() {
            return 4.0;
        }

        @Override
        public double growthMr() {
            return 6.0;
        }

        @Override
        public double growthMp() {
            return 112.0;
        }
    },

    SULAN("sulan", Faction.YOUMING, new Skill[] { new SuLanPassiveSkill() },
            new JiFengTuCiSkill(), new LingFengBiaoJiSkill(), new FengQiangSkill(), new YuFengQianLiSkill()) {
        @Override public double initialSp() { return 0.0; }
        @Override
        public double initialHp() {
            return 1218.0;
        }

        @Override
        public double initialAd() {
            return 48.0;
        }

        @Override
        public double initialMp() {
            return 910.0;
        }

        @Override
        public double initialPd() {
            return 48.0;
        }

        @Override
        public double initialMr() {
            return 44.0;
        }

        @Override
        public double initialAs() {
            return 0.62;
        }

        @Override
        public double initialMs() {
            return 0.1012;
        }

        @Override
        public double initialManaRegen() {
            return 9.8;
        }

        @Override
        public double initialHpRegen() {
            return 2.8;
        }

        /** 升级成长：以下各项均为每级实际增加值。 */
        @Override
        public double growthHp() {
            return 415.8;
        }

        @Override
        public double growthAd() {
            return 19.04;
        }

        @Override
        public double growthSp() {
            return 0.0;
        }

        @Override
        public double growthAs() {
            return 0.0336;
        }

        @Override
        public double growthMs() {
            return 0.001012;
        }

        @Override
        public double growthPd() {
            return 4.0;
        }

        @Override
        public double growthMr() {
            return 4.0;
        }

        @Override
        public double growthMp() {
            return 70.0;
        }
    },

    LINGYUAN("lingyuan", Faction.XUANXIAO, new Skill[] { new LingYuanSkills(LingYuanSkills.Type.PASSIVE) },
            new LingYuanSkills(LingYuanSkills.Type.Q), new LingYuanSkills(LingYuanSkills.Type.W),
            new LingYuanSkills(LingYuanSkills.Type.E), new LingYuanSkills(LingYuanSkills.Type.R)) {
        @Override public double initialSp() { return 30.0; }
        @Override public double initialHp() { return 650.0; }
        @Override public double initialAd() { return 65.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 45.0; }
        @Override public double initialMr() { return 45.0; }
        @Override public double initialAs() { return 0.70; }
        @Override public double initialMs() { return 0.10; }
        @Override public double initialManaRegen() { return 4.0; }
        @Override public double initialHpRegen() { return 2.0; }
        @Override public double growthHp() { return 100.0; }
        @Override public double growthAd() { return 0.0; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0; }
        @Override public double growthMs() { return 0.0; }
        @Override public double growthPd() { return 0.0; }
        @Override public double growthMr() { return 0.0; }
        @Override public double growthMp() { return 0.0; }
    },

    HANZE("hanze", Faction.YOUMING, new Skill[] { new HanzeSkills(HanzeSkills.Type.PASSIVE) },
            new HanzeSkills(HanzeSkills.Type.Q), new HanzeSkills(HanzeSkills.Type.W),
            new HanzeSkills(HanzeSkills.Type.E), new HanzeSkills(HanzeSkills.Type.R)) {
        @Override public double initialHp() { return 740.0; }
        @Override public double initialAd() { return 65.0; }
        @Override public double initialSp() { return 30.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 45.0; }
        @Override public double initialMr() { return 45.0; }
        @Override public double initialAs() { return 0.70; }
        @Override public double initialMs() { return 0.10; }
        @Override public double initialManaRegen() { return 4.0; }
        @Override public double initialHpRegen() { return 2.0; }
        @Override public double growthHp() { return 115.0; }
        @Override public double growthAd() { return 0.0; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0; }
        @Override public double growthMs() { return 0.0; }
        @Override public double growthPd() { return 0.0; }
        @Override public double growthMr() { return 0.0; }
        @Override public double growthMp() { return 0.0; }
    },

    QINGLUO("qingluo", Faction.XUANXIAO, new Skill[] { new QingLuoSkills(QingLuoSkills.Type.PASSIVE) },
            new QingLuoSkills(QingLuoSkills.Type.Q), new QingLuoSkills(QingLuoSkills.Type.W),
            new QingLuoSkills(QingLuoSkills.Type.E), new QingLuoSkills(QingLuoSkills.Type.R)) {
        @Override public double initialHp() { return 580.0; }
        @Override public double initialAd() { return 65.0; }
        @Override public double initialSp() { return 30.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 45.0; }
        @Override public double initialMr() { return 45.0; }
        @Override public double initialAs() { return 0.70; }
        @Override public double initialMs() { return 0.10; }
        @Override public double initialManaRegen() { return 4.0; }
        @Override public double initialHpRegen() { return 2.0; }
        @Override public double growthHp() { return 86.0; }
        @Override public double growthAd() { return 0.0; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0; }
        @Override public double growthMs() { return 0.0; }
        @Override public double growthPd() { return 0.0; }
        @Override public double growthMr() { return 0.0; }
        @Override public double growthMp() { return 0.0; }
    },

    CHIXIAO("chixiao", Faction.YOUMING, new Skill[] { new ChiXiaoSkills(ChiXiaoSkills.Type.PASSIVE) },
            new ChiXiaoSkills(ChiXiaoSkills.Type.Q), new ChiXiaoSkills(ChiXiaoSkills.Type.W),
            new ChiXiaoSkills(ChiXiaoSkills.Type.E), new ChiXiaoSkills(ChiXiaoSkills.Type.R)) {
        @Override public double initialHp() { return 700.0; }
        @Override public double initialAd() { return 65.0; }
        @Override public double initialSp() { return 30.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 45.0; }
        @Override public double initialMr() { return 45.0; }
        @Override public double initialAs() { return 0.70; }
        @Override public double initialMs() { return 0.10; }
        @Override public double initialManaRegen() { return 4.0; }
        @Override public double initialHpRegen() { return 2.0; }
        @Override public double growthHp() { return 110.0; }
        @Override public double growthAd() { return 0.0; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0; }
        @Override public double growthMs() { return 0.0; }
        @Override public double growthPd() { return 0.0; }
        @Override public double growthMr() { return 0.0; }
        @Override public double growthMp() { return 0.0; }
    },

    YUNSHENG("yunsheng", Faction.XUANXIAO, new Skill[] { new YunShengSkills(YunShengSkills.Type.PASSIVE) },
            new YunShengSkills(YunShengSkills.Type.Q), new YunShengSkills(YunShengSkills.Type.W),
            new YunShengSkills(YunShengSkills.Type.E), new YunShengSkills(YunShengSkills.Type.R)) {
        @Override public double initialHp() { return 600.0; }
        @Override public double initialAd() { return 65.0; }
        @Override public double initialSp() { return 30.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 45.0; }
        @Override public double initialMr() { return 45.0; }
        @Override public double initialAs() { return 0.70; }
        @Override public double initialMs() { return 0.10; }
        @Override public double initialManaRegen() { return 4.0; }
        @Override public double initialHpRegen() { return 2.0; }
        @Override public double growthHp() { return 90.0; }
        @Override public double growthAd() { return 0.0; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0; }
        @Override public double growthMs() { return 0.0; }
        @Override public double growthPd() { return 0.0; }
        @Override public double growthMr() { return 0.0; }
        @Override public double growthMp() { return 0.0; }
    },

    LIGE("lige", Faction.YOUMING, new Skill[] { new LiGeSkills(LiGeSkills.Type.PASSIVE) },
            new LiGeSkills(LiGeSkills.Type.Q), new LiGeSkills(LiGeSkills.Type.W),
            new LiGeSkills(LiGeSkills.Type.E), new LiGeSkills(LiGeSkills.Type.R)) {
        @Override public double initialHp() { return 1218.0; }
        @Override public double initialAd() { return 52.0; }
        @Override public double initialSp() { return 0.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 40.0; }
        @Override public double initialMr() { return 44.0; }
        @Override public double initialAs() { return 0.66; }
        @Override public double initialMs() { return 0.09177; }
        @Override public double initialManaRegen() { return 10.5; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 434.7; }
        @Override public double growthAd() { return 22.848; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.036; }
        @Override public double growthMs() { return 0.000874; }
        @Override public double growthPd() { return 4.0; }
        @Override public double growthMr() { return 4.0; }
        @Override public double growthMp() { return 70.0; }
    },

    GUANLAN("guanlan", Faction.YOUMING, new Skill[] { new GuanLanSkills(GuanLanSkills.Type.PASSIVE) },
            new GuanLanSkills(GuanLanSkills.Type.Q), new GuanLanSkills(GuanLanSkills.Type.W),
            new GuanLanSkills(GuanLanSkills.Type.E), new GuanLanSkills(GuanLanSkills.Type.R)) {
        @Override public double initialHp() { return 1134.0; }
        @Override public double initialAd() { return 8.0; }
        @Override public double initialSp() { return 60.0; }
        @Override public double initialMp() { return 1015.0; }
        @Override public double initialPd() { return 36.0; }
        @Override public double initialMr() { return 52.0; }
        @Override public double initialAs() { return 0.48; }
        @Override public double initialMs() { return 0.092; }
        @Override public double initialManaRegen() { return 16.1; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 189.0; }
        @Override public double growthAd() { return 3.808; }
        @Override public double growthSp() { return 33.32; }
        @Override public double growthAs() { return 0.0112; }
        @Override public double growthMs() { return 0.000644; }
        @Override public double growthPd() { return 3.6; }
        @Override public double growthMr() { return 6.0; }
        @Override public double growthMp() { return 126.0; }
    },

    WUXIANG("wuxiang", Faction.XUANXIAO, new Skill[] { new WuxiangSkills(WuxiangSkills.Type.PASSIVE) },
            new WuxiangSkills(WuxiangSkills.Type.Q), new WuxiangSkills(WuxiangSkills.Type.W),
            new WuxiangSkills(WuxiangSkills.Type.E), new WuxiangSkills(WuxiangSkills.Type.R)) {
        @Override public double initialHp() { return 1218.0; }
        @Override public double initialAd() { return 60.0; }
        @Override public double initialSp() { return 0.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 48.0; }
        @Override public double initialMr() { return 44.0; }
        @Override public double initialAs() { return 0.6; }
        @Override public double initialMs() { return 0.09752; }
        @Override public double initialManaRegen() { return 9.8; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 453.6; }
        @Override public double growthAd() { return 28.56; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0308; }
        @Override public double growthMs() { return 0.00092; }
        @Override public double growthPd() { return 5.4; }
        @Override public double growthMr() { return 3.6; }
        @Override public double growthMp() { return 49.0; }
    },

    FEIYU("feiyu", Faction.XUANXIAO, new Skill[] { new FeiYuSkills(FeiYuSkills.Type.PASSIVE) },
            new FeiYuSkills(FeiYuSkills.Type.Q), new FeiYuSkills(FeiYuSkills.Type.W),
            new FeiYuSkills(FeiYuSkills.Type.E), new FeiYuSkills(FeiYuSkills.Type.R)) {
        @Override public double initialHp() { return 1302.0; }
        @Override public double initialAd() { return 56.0; }
        @Override public double initialSp() { return 0.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 52.0; }
        @Override public double initialMr() { return 48.0; }
        @Override public double initialAs() { return 0.6; }
        @Override public double initialMs() { return 0.09752; }
        @Override public double initialManaRegen() { return 9.8; }
        @Override public double initialHpRegen() { return 3.15; }
        @Override public double growthHp() { return 491.4; }
        @Override public double growthAd() { return 25.8944; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0252; }
        @Override public double growthMs() { return 0.00092; }
        @Override public double growthPd() { return 5.6; }
        @Override public double growthMr() { return 4.0; }
        @Override public double growthMp() { return 56.0; }
    },

    WANYUE("wanyue", Faction.YOUMING, new Skill[] { new WanyueSkills(WanyueSkills.Type.PASSIVE) },
            new WanyueSkills(WanyueSkills.Type.Q), new WanyueSkills(WanyueSkills.Type.W),
            new WanyueSkills(WanyueSkills.Type.E), new WanyueSkills(WanyueSkills.Type.R)) {
        @Override public double initialHp() { return 1176.0; }
        @Override public double initialAd() { return 48.0; }
        @Override public double initialSp() { return 0.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 40.0; }
        @Override public double initialMr() { return 44.0; }
        @Override public double initialAs() { return 0.64; }
        @Override public double initialMs() { return 0.09752; }
        @Override public double initialManaRegen() { return 11.2; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 368.55; }
        @Override public double growthAd() { return 21.3248; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.0336; }
        @Override public double growthMs() { return 0.001012; }
        @Override public double growthPd() { return 3.8; }
        @Override public double growthMr() { return 4.6; }
        @Override public double growthMp() { return 84.0; }
    },

    XUANJIA("xuanjia", Faction.XUANXIAO, new Skill[] { new XuanJiaSkills(XuanJiaSkills.Type.PASSIVE) },
            new XuanJiaSkills(XuanJiaSkills.Type.Q), new XuanJiaSkills(XuanJiaSkills.Type.W),
            new XuanJiaSkills(XuanJiaSkills.Type.E), new XuanJiaSkills(XuanJiaSkills.Type.R)) {
        @Override public double initialHp() { return 1638.0; }
        @Override public double initialAd() { return 36.0; }
        @Override public double initialSp() { return 0.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 84.0; }
        @Override public double initialMr() { return 60.0; }
        @Override public double initialAs() { return 0.46; }
        @Override public double initialMs() { return 0.0874; }
        @Override public double initialManaRegen() { return 9.8; }
        @Override public double initialHpRegen() { return 5.6; }
        @Override public double growthHp() { return 396.9; }
        @Override public double growthAd() { return 15.232; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.014; }
        @Override public double growthMs() { return 0.00046; }
        @Override public double growthPd() { return 10.0; }
        @Override public double growthMr() { return 5.0; }
        @Override public double growthMp() { return 56.0; }
    },

    LUOHENG("luoheng", Faction.XUANXIAO, new Skill[] { new LuoHengSkills(LuoHengSkills.Type.PASSIVE) },
            new LuoHengSkills(LuoHengSkills.Type.Q), new LuoHengSkills(LuoHengSkills.Type.W),
            new LuoHengSkills(LuoHengSkills.Type.E), new LuoHengSkills(LuoHengSkills.Type.R)) {
        @Override public double initialHp() { return 1159.2; }
        @Override public double initialAd() { return 12.0; }
        @Override public double initialSp() { return 56.0; }
        @Override public double initialMp() { return 980.0; }
        @Override public double initialPd() { return 40.0; }
        @Override public double initialMr() { return 52.0; }
        @Override public double initialAs() { return 0.48; }
        @Override public double initialMs() { return 0.092; }
        @Override public double initialManaRegen() { return 15.4; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 274.05; }
        @Override public double growthAd() { return 3.808; }
        @Override public double growthSp() { return 30.94; }
        @Override public double growthAs() { return 0.01232; }
        @Override public double growthMs() { return 0.000736; }
        @Override public double growthPd() { return 3.6; }
        @Override public double growthMr() { return 5.4; }
        @Override public double growthMp() { return 119.0; }
    },

    SHENLI("shenli", Faction.XUANXIAO, new Skill[] { new ShenLiSkills(ShenLiSkills.Type.PASSIVE) },
            new ShenLiSkills(ShenLiSkills.Type.Q), new ShenLiSkills(ShenLiSkills.Type.W),
            new ShenLiSkills(ShenLiSkills.Type.E), new ShenLiSkills(ShenLiSkills.Type.R)) {
        @Override public double initialHp() { return 1218.0; }
        @Override public double initialAd() { return 52.0; }
        @Override public double initialSp() { return 0.0; }
        @Override public double initialMp() { return 700.0; }
        @Override public double initialPd() { return 44.0; }
        @Override public double initialMr() { return 44.0; }
        @Override public double initialAs() { return 0.64; }
        @Override public double initialMs() { return 0.090022; }
        @Override public double initialManaRegen() { return 11.2; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 387.45; }
        @Override public double growthAd() { return 22.0864; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.032; }
        @Override public double growthMs() { return 0.000874; }
        @Override public double growthPd() { return 4.0; }
        @Override public double growthMr() { return 4.4; }
        @Override public double growthMp() { return 77.0; }
    },

    ZHECHUAN("zhechuan", Faction.XUANXIAO, new Skill[] { new ZheChuanSkills(ZheChuanSkills.Type.PASSIVE) },
            new ZheChuanSkills(ZheChuanSkills.Type.Q), new ZheChuanSkills(ZheChuanSkills.Type.W),
            new ZheChuanSkills(ZheChuanSkills.Type.E), new ZheChuanSkills(ZheChuanSkills.Type.R)) {
        @Override public double initialHp() { return 1218.0; }
        @Override public double initialAd() { return 56.0; }
        @Override public double initialSp() { return 0.0; }
        @Override public double initialMp() { return 735.0; }
        @Override public double initialPd() { return 48.0; }
        @Override public double initialMr() { return 44.0; }
        @Override public double initialAs() { return 0.6; }
        @Override public double initialMs() { return 0.0966; }
        @Override public double initialManaRegen() { return 10.5; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 378.0; }
        @Override public double growthAd() { return 24.752; }
        @Override public double growthSp() { return 0.0; }
        @Override public double growthAs() { return 0.02; }
        @Override public double growthMs() { return 0.000828; }
        @Override public double growthPd() { return 4.6; }
        @Override public double growthMr() { return 4.0; }
        @Override public double growthMp() { return 63.0; }
    },

    MINGZHU("mingzhu", Faction.XUANXIAO, new Skill[] {new MingZhuSkills(MingZhuSkills.Type.PASSIVE)},
            new MingZhuSkills(MingZhuSkills.Type.Q), new MingZhuSkills(MingZhuSkills.Type.W),
            new MingZhuSkills(MingZhuSkills.Type.E), new MingZhuSkills(MingZhuSkills.Type.R)) {
        @Override public double initialHp() { return 1176.0; }
        @Override public double initialAd() { return 12.0; }
        @Override public double initialSp() { return 56.0; }
        @Override public double initialMp() { return 945.0; }
        @Override public double initialPd() { return 40.0; }
        @Override public double initialMr() { return 52.0; }
        @Override public double initialAs() { return 0.52; }
        @Override public double initialMs() { return 0.092; }
        @Override public double initialManaRegen() { return 14.0; }
        @Override public double initialHpRegen() { return 2.8; }
        @Override public double growthHp() { return 302.4; }
        @Override public double growthAd() { return 3.808; }
        @Override public double growthSp() { return 28.56; }
        @Override public double growthMp() { return 112.0; }
        @Override public double growthPd() { return 4.0; }
        @Override public double growthMr() { return 5.0; }
        @Override public double growthAs() { return 0.014; }
        @Override public double growthMs() { return 0.000736; }
    },
    SHUO("shuo",Faction.XUANXIAO,new Skill[0]) {
        @Override public double initialHp(){return 640;}
        @Override public double initialAd(){return 65;}
        @Override public double initialSp(){return 30;}
        @Override public double initialMp(){return 700;}
        @Override public double initialPd(){return 45;}
        @Override public double initialMr(){return 45;}
        @Override public double initialAs(){return .70;}
        @Override public double initialMs(){return .10;}
        @Override public double initialHpRegen(){return 2;}
        @Override public double initialManaRegen(){return 4;}
        @Override public double growthHp(){return 100;}
    };

    public static final int SLOT_Q = 0;
    public static final int SLOT_W = 1;
    public static final int SLOT_E = 2;
    public static final int SLOT_R = 3;
    public static final int SLOT_F = 4;
    public static final int SKILL_SLOTS = 5;

    private final String id;
    private final Faction faction;
    private final Skill[] skills;
    private final Skill[] passives;

    CharacterType(String id, Faction faction, Skill[] passives, Skill... skills) {
        this.id = id;
        this.faction = faction;
        this.skills = new Skill[SKILL_SLOTS];
        System.arraycopy(skills, 0, this.skills, 0, Math.min(skills.length, SKILL_SLOTS));
        this.passives = passives == null ? new Skill[0] : passives;
    }

    public String getId() {
        return id;
    }

    // ---- 初始属性（角色专属，选角色后应用；默认 = 统一基础值） ----

    /** 初始生命上限 */
    public double initialHp() {
        return 1260.0;
    }

    /** 初始物理攻击 */
    public double initialAd() {
        return 40.0;
    }

    /** 初始法术强度 */
    public double initialSp() {
        return 0.0;
    }

    /** 初始最大法力 */
    public double initialMp() {
        return 700.0;
    }

    /** 初始物理防御 */
    public double initialPd() {
        return 0.0;
    }

    /** 初始法术防御 */
    public double initialMr() {
        return 0.0;
    }

    /** 初始攻击速度（模组独立属性） */
    public double initialAs() {
        return 4.0;
    }

    /** 初始移动速度（基准 0.1 = 100%；角色专属） */
    public double initialMs() {
        return 0.092;
    }

    /** 初始法力回复（MP/s；角色专属） */
    public double initialManaRegen() {
        return 7.0;
    }

    /** 初始生命回复（HP/s；角色专属） */
    public double initialHpRegen() {
        return 3.5;
    }

    // ---- 升级成长（每级数值加成，默认 0；子类覆盖） ----

    /** 每级生命上限加成 */
    public double growthHp() {
        return 0.0;
    }
    /** 每级物理攻击加成 */
    public double growthAd() {
        return 0.0;
    }

    /** 每级法术强度加成 */
    public double growthSp() {
        return 0.0;
    }

    /** 每级攻击速度加成 */
    public double growthAs() {
        return 0.0;
    }

    /** 每级移动速度加成 */
    public double growthMs() {
        return 0.0;
    }

    /** 每级物理防御加成 */
    public double growthPd() {
        return 0.0;
    }

    /** 每级法术防御加成 */
    public double growthMr() {
        return 0.0;
    }

    /** 每级最大法力加成 */
    public double growthMp() {
        return 0.0;
    }

    public Faction getFaction() {
        return faction;
    }

    /** 名称语言键：character.myriad.<id> */
    public String nameKey() {
        return "character.myriad." + id;
    }

    /** 槽位技能：0=Q, 1=W, 2=E, 3=R, 4=F；未配置返回 null */
    public Skill skill(int slot) {
        return slot < 0 || slot >= skills.length ? null : skills[slot];
    }

    /** 被动技能列表（常驻效果，仅展示） */
    public Skill[] passives() {
        return passives;
    }

    public static CharacterType byId(String id) {
        if (id == null) {
            return null;
        }
        for (CharacterType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }

    public static List<String> ids() {
        List<String> result = new ArrayList<>();
        for (CharacterType type : values()) {
            result.add(type.id);
        }
        return result;
    }

    /** 角色表已存储实际初始生命，模组生命池直接采用同一数值。 */
    public static double balancedInitialHp(CharacterType type) {
        return type == null ? 700.0 : type.initialHp();
    }

    public static double balancedMovementSpeed(CharacterType type) {
        return type == null ? 0.092 : type.initialMs();
    }

    public static double balancedHealthRegen(CharacterType type) {
        return type == null ? 3.5 : type.initialHpRegen();
    }

    /** 当前远程物理射手；其普通攻击只结算 80% 物攻。 */
    public static boolean isMarksman(CharacterType type) {
        return type == LIGE || type == WANYUE || type == SHENLI;
    }

    /** 应用角色专属初始属性（type 为 null 时用统一基础值）；移速 0.1，其余模组属性 0，原版交互属性保持默认 */
    public static void applyInitialAttributes(net.minecraft.world.entity.player.Player player,
            com.myriad.character.CharacterType type) {
        // 只重置模组相关属性（原版交互/生存属性如触及距离、挖掘速度等保持注册默认）
        java.util.List<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>> resetList =
                java.util.List.of(
                        net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH,
                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                        net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK,
                        com.myriad.registry.ModAttributes.PHYSICAL_ATTACK,
                        com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE,
                        com.myriad.registry.ModAttributes.MAGIC_RESISTANCE,
                        com.myriad.registry.ModAttributes.ATTACK_SPEED,
                        com.myriad.registry.ModAttributes.SPELL_POWER,
                        com.myriad.registry.ModAttributes.CRIT_CHANCE,
                        com.myriad.registry.ModAttributes.CRIT_DAMAGE,
                        com.myriad.registry.ModAttributes.PHYSICAL_LIFESTEAL,
                        com.myriad.registry.ModAttributes.TENACITY,
                        com.myriad.registry.ModAttributes.FLAT_ARMOR_PEN,
                        com.myriad.registry.ModAttributes.FLAT_MAGIC_PEN,
                        com.myriad.registry.ModAttributes.PERCENT_ARMOR_PEN,
                        com.myriad.registry.ModAttributes.PERCENT_MAGIC_PEN,
                        com.myriad.registry.ModAttributes.MAX_MANA,
                        com.myriad.registry.ModAttributes.MANA_REGEN,
                        com.myriad.registry.ModAttributes.COOLDOWN_REDUCTION,
                        com.myriad.registry.ModAttributes.HEALTH_REGEN,
                        com.myriad.registry.ModAttributes.SPELL_LIFESTEAL);
        for (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> holder : resetList) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(holder);
            if (inst != null) {
                inst.removeModifiers();
                inst.setBaseValue(0.0);
            }
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance hp =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(balancedInitialHp(type));
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance ms =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (ms != null) {
            ms.setBaseValue(balancedMovementSpeed(type));
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance as =
                player.getAttribute(com.myriad.registry.ModAttributes.ATTACK_SPEED);
        if (as != null) {
            as.setBaseValue(type == null ? 4.0 : type.initialAs());
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance mana =
                player.getAttribute(com.myriad.registry.ModAttributes.MAX_MANA);
        if (mana != null) {
            mana.setBaseValue((type == null ? 700.0 : type.initialMp()));
        }
        // 基础物攻 10（无装备也有基础平砍伤害）
        net.minecraft.world.entity.ai.attributes.AttributeInstance ad =
                player.getAttribute(com.myriad.registry.ModAttributes.PHYSICAL_ATTACK);
        if (ad != null) {
            ad.setBaseValue((type == null ? 40.0 : type.initialAd()));
        }
        // 法术强度（角色专属初始）
        net.minecraft.world.entity.ai.attributes.AttributeInstance sp =
                player.getAttribute(com.myriad.registry.ModAttributes.SPELL_POWER);
        if (sp != null) {
            sp.setBaseValue((type == null ? 0.0 : type.initialSp()));
        }
        // 物防 / 法防
        net.minecraft.world.entity.ai.attributes.AttributeInstance pd =
                player.getAttribute(com.myriad.registry.ModAttributes.PHYSICAL_DEFENSE);
        if (pd != null) {
            pd.setBaseValue((type == null ? 0.0 : type.initialPd()));
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance mr =
                player.getAttribute(com.myriad.registry.ModAttributes.MAGIC_RESISTANCE);
        if (mr != null) {
            mr.setBaseValue((type == null ? 0.0 : type.initialMr()));
        }
        // 原版攻速固定（移除物品影响，攻速统一走模组独立属性；base 为 0 会导致攻击强度异常）
        net.minecraft.world.entity.ai.attributes.AttributeInstance vanillaAs =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        if (vanillaAs != null) {
            // 与模组攻速一致（决定实际攻击频率；角色专属）
            vanillaAs.setBaseValue(type == null ? 4.0 : type.initialAs());
        }
        // 原版攻击力保持 1.0：作为触发伤害事件的载体（0 会被原版 hurt() 直接跳过，导致平砍无伤害事件）
        net.minecraft.world.entity.ai.attributes.AttributeInstance vanillaAd =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (vanillaAd != null) {
            vanillaAd.setBaseValue(1.0);
        }
        // 暴击伤害保持统一初始 130%（resetList 清零后需恢复），法力回复保持角色专属
        net.minecraft.world.entity.ai.attributes.AttributeInstance cd =
                player.getAttribute(com.myriad.registry.ModAttributes.CRIT_DAMAGE);
        if (cd != null) {
            cd.setBaseValue(130.0);
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance regen =
                player.getAttribute(com.myriad.registry.ModAttributes.MANA_REGEN);
        if (regen != null) {
            regen.setBaseValue((type == null ? 7.0 : type.initialManaRegen()));
        }
        // 生命回复：角色专属初始
        net.minecraft.world.entity.ai.attributes.AttributeInstance hpRegen =
                player.getAttribute(com.myriad.registry.ModAttributes.HEALTH_REGEN);
        if (hpRegen != null) {
            hpRegen.setBaseValue(balancedHealthRegen(type));
        }
    }

    /** 重置为原版基础属性（模组关闭时调用）：所有属性恢复注册默认值并清除全部 modifier */
    public static void applyVanillaAttributes(net.minecraft.world.entity.player.Player player) {
        for (net.minecraft.world.entity.ai.attributes.AttributeInstance inst :
                player.getAttributes().getSyncableAttributes()) {
            inst.removeModifiers();
            inst.setBaseValue(inst.getAttribute().value().getDefaultValue());
        }
        // 移动速度基准统一为 0.1（26.2 注册默认 0.7，与策划案 100% 基准不符，开关状态均校准）
        net.minecraft.world.entity.ai.attributes.AttributeInstance ms =
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (ms != null) {
            ms.setBaseValue(0.1);
        }
    }
}
