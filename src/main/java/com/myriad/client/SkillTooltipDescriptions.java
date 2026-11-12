package com.myriad.client;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.myriad.character.Skill;

/** 角色技能面板使用的完整易读说明；机制与数值按当前技能等级融合展开。 */
public final class SkillTooltipDescriptions {
    private SkillTooltipDescriptions() {}

    public static String describe(Skill skill, int rawLevel) {
        if (skill instanceof com.myriad.character.ability.AbilitySkill ability) {
            double strength=ClientDataHolder.attrValue(com.myriad.registry.ModAttributes.SPELL_POWER.value().getDescriptionId());
            String form="";
            if(ability.role().equals("shuo")&&!ability.isPassive()){
                boolean recast=ClientDataHolder.buffTicks(ability.getId()+"_recast")>0;
                form="当前招式："+com.myriad.character.skills.shuo.ShuoForms.name(ability.getId(),ClientDataHolder.syncedCounter("g1_shuo_drawn")>0,recast)+"。";
            }
            return "Lv."+Math.max(1,rawLevel)+" / "+ability.maxRank()+" · "+form
                    +com.myriad.character.ability.AbilityFormulaText.evaluate(ability.description(),rawLevel,strength);
        }
        int lv = Math.max(1, rawLevel);
        return switch (skill.getId()) {
            // 影刹·夜影
            case "yingxiao_passive" -> "从敌人背后攻击时，普攻和技能伤害提高30%（原伤害的130%）。根据命中时双方位置与敌人的水平朝向判定；不作用于防御塔。";
            case "yingxi" -> "向前突进，造成" + value(60 + 32 * (lv - 1), 80 + 20 * (lv - 1), "物攻")
                    + "物理伤害；背击伤害翻倍并眩晕1秒；同次突进后续命中的目标仅受到50%伤害。";
            case "huaying" -> "隐身并获得加速，持续" + decimal(4 + .5 * (lv - 1)) + "秒；携带灵源时持续时间翻倍。";
            case "yingsha" -> "投出影刺，造成" + value(48 + 40 * (lv - 1), 240, "物攻")
                    + "物理伤害，并定身目标3秒；影刺可被风墙等投射物阻挡效果拦截。";
            case "lingdun" -> "获得" + (560 + 210 * (lv - 1))
                    + " + 150%物攻的模组护盾，持续4秒；携带灵源时护盾提高50%，但自身会被减速。";

            // 玄阵·墨尘
            case "xuanchen_passive" -> "8格内有可见存活队友时，技能冷却额外缩短20%；施法为自身与8格内存活队友回复5法力。";
            case "lingfu" -> "释放连锁灵符，造成" + value(40 + 40 * (lv - 1), 200 + 20 * (lv - 1), "法强")
                    + "魔法伤害，并强减速或定身目标。";
            case "chuansongzhen" -> "在25格内放置一对传送阵，持续8秒；自己和队友可通过阵点传送。";
            case "moyanghuzhen" -> "布置半径6格、持续5秒的护阵：友军减伤30%并加速15%，敌军减速30%。";
            case "zhoutianfuzhen" -> "引导2秒后展开8格法阵，持续" + (6 + lv - 1)
                    + "秒；保护友军，并每0.5秒对敌军造成"
                    + value(40 + 80 * (lv - 1), 100, "法强") + "魔法伤害。";

            // 风部·素岚
            case "sulan_passive" -> "常驻移速提高15%；移动100格后，下次普攻附加"
                    + value(12, 30 + 3 * (lv - 1), "物攻") + "物理伤害；强化普攻就绪时被动图标常亮，命中消耗后熄灭。";
            case "jifengtuci" -> "向前突进8格，路径敌人受到"
                    + value(48 + 32 * (lv - 1), 200 + 32 * (lv - 1), "物攻") + "物理伤害。";
            case "lingfengbiaoji" -> "标记20格内目标10秒：显形、受到伤害提高10%，并减速"
                    + (15 + 5 * (lv - 1)) + "% 。";
            case "fengqiang" -> "生成持续4秒的风墙，阻挡投射物；穿墙敌人减速50%，持续2秒。";
            case "yufengqianli" -> "蓄力1秒后长距离冲刺，造成"
                    + value(124 + 64 * (lv - 1), 240 + 80 * (lv - 1), "物攻")
                    + "物理伤害；结束后加速并免疫减速3秒。";

            // 火部·焚诀
            case "fenjue_passive" -> "生命不高于20%时物攻提高35%、每秒回复2法力；击杀敌方英雄回复30%已损生命。";
            case "lieyanzhan" -> "斩击前方5格扇形，造成"
                    + value(56 + 20 * (lv - 1), 240 + 20 * (lv - 1), "物攻")
                    + "物理伤害，并灼烧3秒（每0.5秒造成10%物攻真实伤害）。";
            case "rongyanlaolong" -> "在选点生成4格熔岩牢笼5秒；触墙受到"
                    + value(52 + 12 * (lv - 1), 280 + 12 * (lv - 1), "物攻")
                    + "物理伤害，内部每秒受到1%最大生命物伤。";
            case "fenjuebaofa" -> "蓄力1秒后对8格内造成"
                    + value(120, 400 + 20 * (lv - 1), "物攻")
                    + "物理伤害；自身生命越低，伤害越高，最多提高50%。";
            case "fentianhuohai" -> "腾空砸地，对8格内造成"
                    + value(200 + 80 * (lv - 1), 120 + 40 * (lv - 1), "物攻")
                    + "物理伤害并灼烧；随后物攻提高140%，持续5秒。";

            // 雷部·天刑
            case "tianxing_passive" -> "普攻附加" + value(3, 25, "物攻")
                    + "魔法伤害，并向3格内最近的另一名敌人溅射本次普攻10%的魔法伤害；每第3次普攻额外造成（3%+角色等级%）物攻真实伤害。";
            case "shanlei" -> "闪现约6格，对落点4格内造成"
                    + value(60 + 20 * (lv - 1), 160 + 32 * (lv - 1), "物攻")
                    + "物理伤害，并提高攻速3秒。";
            case "leijiezhan" -> "蓄力斩出18格雷弧，造成"
                    + value(80 + 20 * (lv - 1), 320 + 40 * (lv - 1), "物攻")
                    + "物理伤害，并减速30%持续2秒。";
            case "tianleihuti" -> "获得20%减伤；每秒对周围5格造成"
                    + value(52 + 20 * (lv - 1), 80, "物攻") + "魔法伤害，持续5秒。";
            case "jiutianshenlei" -> "在9格内召唤12道雷，每道造成"
                    + value(80, 160 + 60 * (lv - 1), "物攻") + "物理伤害并强减速0.5秒。";

            // 御剑宗师·凌渊
            case "lingyuan_passive" -> "每次普攻获得10点剑罡；剑罡每达到100点凝成1把飞剑，最多4把同时出击。";
            case "poyunshi" -> "向当前方向突进6格，造成"
                    + value(40 + 40 * (lv - 1), 240, "物攻") + "物理伤害；贴近英雄时获得20剑罡。";
            case "huaxingweijian" -> "需要至少3把飞剑；进入1.5秒剑势，清除控制并格挡一次伤害，随后增加3把飞剑。";
            case "wanjianjue" -> "向前射出5把剑，每把造成"
                    + value(48 + 24 * (lv - 1), 120 + 4 * (lv - 1), "物攻")
                    + "物理伤害；命中获得剑罡并施加破绽。";
            case "sanqianguizong" -> "消耗全部飞剑在选点舞动6秒，每次接触造成"
                    + value(100, 80, "物攻") + "物理伤害；同时进入人剑合一6秒。";

            // 霜部·寒泽
            case "hanze_passive" -> "伤害叠加寒霜，4层冰冻3秒；对减速目标增伤15%，攻击冰冻目标额外造成10%最大生命真实伤害并解除冰冻。";
            case "bingzhuishu" -> "依次发射3枚冰锥，首枚造成"
                    + value(52 + 20 * (lv - 1), 120 + 40 * (lv - 1), "法强")
                    + "魔法伤害，后两枚伤害减半，并减速2秒。";
            case "shuangdun" -> "为最近友军或自己提供"
                    + value(30 + 10 * (lv - 1), 15 + 5 * (lv - 1), "法强") + "护盾，持续4秒。";
            case "jueduilingdu" -> "选点蓄力1秒，对10格内造成" + value(100, 200, "法强")
                    + "魔法伤害，并冰冻1.2秒。";
            case "qianlibingfeng" -> "原地展开8格冰封，持续" + (3 + lv - 1)
                    + "秒；每秒造成" + value(100, 80, "法强") + "魔法伤害并减速。";

            // 丹心·青萝
            case "qingluo_passive" -> "技能保护低于60%生命的友军时，回复3%已损生命并加速2秒；同目标冷却4秒。";
            case "qingtengyu" -> "释放3枚生命法球：命中低生命友军时治疗"
                    + value(10 + 8 * (lv - 1), 45, "法强") + "生命；没有受伤友军时追踪敌人，造成"
                    + value(48 + 32 * (lv - 1), 180, "法强") + "魔法伤害。";
            case "huxindanzhen" -> "选点吟唱1.5秒，令4格内友军获得"
                    + value(10 + 12 * (lv - 1), 45, "法强") + "护盾和抗性。";
            case "qiansiduoe" -> "连接友军4秒，将自己受到伤害的"
                    + (20 + 3 * (lv - 1)) + "%转移给目标护盾，并帮助其解除控制。";
            case "wanmuchunhui" -> "吟唱1秒后展开8格领域，持续" + (6 + lv - 1)
                    + "秒；每秒治疗友军" + value(10 + 4 * (lv - 1), 40, "法强") + "生命并回复4法力。";

            // 破军·赤霄
            case "chixiao_passive" -> "连续攻击同一英雄叠3层；满层后下个伤害技能附加"
                    + value(48, 50, "物攻") + "真实伤害，并按技能伤害治疗25%。";
            case "duanyuezhan" -> "斩击前方4格，造成" + value(48 + 48 * (lv - 1), 300, "物攻")
                    + "物理伤害；目标低于半血时伤害提高30%。";
            case "zhuihunta" -> "向前突进8格，造成" + value(100, 240, "物攻")
                    + "物理伤害并减速；2秒内再次释放可返回起点。";
            case "chixiaofanfeng" -> "招架1秒，正面减伤70%；成功后反击，造成"
                    + value(80 + 60 * (lv - 1), 250, "物攻") + "物理伤害并定身。";
            case "pojunwuhui" -> "选点跳斩14格，对落点3格内造成"
                    + value(140 + 100 * (lv - 1), 440, "物攻") + "物理伤害；击杀目标刷新冷却。";

            // 灵契·云笙
            case "yunsheng_passive" -> "8格内友军获得5%技能急速；连续保护同一目标两次，使其下次受伤降低20%。";
            case "zhihechuanyin" -> "纸鹤沿随机弧线飞行；命中友军回复" + value(20 + 6 * (lv - 1), 30, "法强")
                    + "法力；命中敌人造成" + value(88 + 28 * (lv - 1), 180, "法强") + "魔法伤害。";
            case "dingqisuo" -> "对敌人先定身后减速；对友军提供20%移速和一次减速免疫，持续2.5秒。";
            case "huiyinjiebiao" -> "放置8秒界标；敌人首次进入受到"
                    + value(100 + 40 * (lv - 1), 160, "法强") + "魔法伤害并显形。";
            case "zhongshengguizong" -> "选点聚拢友军并治疗"
                    + value(40 + 12 * (lv - 1), 50, "法强") + "生命；敌人受到"
                    + value(320 + 72 * (lv - 1), 280, "法强") + "魔法伤害并沉默。";

            // 影弦·离歌
            case "lige_passive" -> "射手普攻基础按80%物攻结算。手持影弦弓按住右键拉弓、松开射出粒子箭，攻速会缩短拉弓时间；对20格外玩家仅造成5%伤害。连续普攻同一目标叠3层，每层伤害提高4%，满层命中回复10%已损生命。";
            case "lieyujian" -> "立即刷新普攻，并强化接下来3次普攻：额外造成"
                    + value(18 + 9 * (lv - 1), 45 + 5 * (lv - 1), "物攻")
                    + "物理伤害，命中后分裂3支子箭。";
            case "huifengbu" -> "向移动方向位移5格，攻速提高" + (35 + 4 * (lv - 1))
                    + "%持续2秒；下次普攻附带减速。";
            case "yingxianfu" -> "射出束缚箭，造成" + value(95.2 + 37.4 * (lv - 1), 170, "物攻")
                    + "物理伤害；目标靠墙时眩晕，否则减速。";
            case "baixianheyi" -> "4秒内发射" + (8 + lv - 1) + "支追踪箭，每支造成"
                    + value(119 + 40.8 * (lv - 1), 187, "物攻") + "物理伤害。";

            // 星罗·观澜
            case "guanlan_passive" -> "技能命中叠1层星轨，4层强化下一技能：额外造成"
                    + value(80, 120, "法强") + "魔法伤害并回复15%最大法力。";
            case "liuxingluozi" -> "选点落下一颗星，造成" + value(112 + 40 * (lv - 1), 220, "法强")
                    + "魔法伤害并减速；最多储存2次。";
            case "nizhuanxingpan" -> "吟唱1秒展开3格星盘，持续" + (3 + Math.min(1, (lv - 1) / 3))
                    + "秒；首次进入的敌人受到" + value(96 + 36 * (lv - 1), 140, "法强") + "魔法伤害，范围内持续受到吸引。";
            case "tianmufengxian" -> "连接两点生成4秒星线；敌人穿过时受到"
                    + value(160 + 56 * (lv - 1), 200, "法强") + "魔法伤害并禁锢。";
            case "zhoutianzhuixing" -> "在12格内选点，于约6.5格区域召唤" + (9 + lv - 1)
                    + "颗星陨；普通星陨爆炸半径4格，终结星陨半径5.5格。每颗造成"
                    + value(180 + 40 * (lv - 1), 140, "法强") + "魔法伤害；最后一颗眩晕1.5秒。";

            // 山岳·玄甲
            case "xuanjia_passive" -> "脱战每5秒获得1层山甲，最多3层；受伤时消耗1层并减免40%伤害。";
            case "hanshanquan" -> "蓄力1秒后朝当前方向攻击4格扇形，造成"
                    + value(96 + 40 * (lv - 1), 260, "物攻") + "物理伤害并击退。";
            case "yanbihuti" -> "获得" + (60 + 20 * (lv - 1))
                    + " + 8%最大生命护盾4秒；护盾结束时对周围造成"
                    + (80 + 32 * (lv - 1)) + " + 16%最大生命物理伤害。";
            case "duanmaichongfeng" -> "向前冲锋8格，造成"
                    + value(140 + 48 * (lv - 1), 280, "物攻") + "物理伤害并推行目标；撞墙时眩晕1.5秒。";
            case "zhenyuetianmen" -> "吸附13格内敌人并造成120 + 8%最大生命物伤；生成封锁领域，自身减伤"
                    + (25 + 3 * (lv - 1)) + "% 。";

            // 镜魇·无相
            case "wuxiang_passive" -> "目标周围按世界方向固定分为正相、左魇、右魇三个120°区域，不随任何玩家视角旋转。5秒内从三个区域命中同一英雄，触发56 + 8×角色等级 + 140%物攻真实伤害，并回复1层踏镜；已命中的相会显示为目标脚下对应方向的三分之一圆环。";
            case "tajingliuguang" -> "双充能突进5格，路径造成"
                    + value(60 + 36 * (lv - 1), 220, "物攻") + "物理伤害；命中新方位缩短充能2秒。";
            case "liuxingyiwei" -> "留下3点完整度镜像并最多换位2次；延迟镜线造成"
                    + value(44 + 24 * (lv - 1), 120, "物攻") + "物理伤害。";
            case "nizhaohuifeng" -> "镜刃造成" + value(72 + 36 * (lv - 1), 240, "物攻")
                    + "物理伤害；再施放按移动输入从左侧或右侧绕身，并以"
                    + value(48 + 28 * (lv - 1), 140, "物攻") + "物理伤害回斩。";
            case "baixiangwugui" -> "展开5秒镜域；三个镜门使用与被动相同的世界固定方位，穿过全部镜门后造成"
                    + value(248 + 120 * (lv - 1), 260, "物攻") + "+40%已损生命真实伤害。";

            // 惊鸿·绯羽
            case "feiyu_passive" -> "使用不同招式命中英雄可提升舞阶，每阶提供1%移速；舞谱超时会逐阶衰减，达到凰阶后才能使用凤舞。";
            case "liuxiashuangyanfan" -> "按移动方向掠斩，造成" + value(68 + 32 * (lv - 1), 200, "物攻")
                    + "物理伤害；命中英雄后可再次释放燕返，造成" + value(48 + 32 * (lv - 1), 152, "物攻") + "物理伤害。";
            case "yuanguangzhefengwu" -> "展开圆光刃幕并造成" + value(84 + 28 * (lv - 1), 180, "物攻")
                    + "物理伤害；前4 tick成功格挡英雄攻击后，可再次释放折锋，造成"
                    + value(64 + 28 * (lv - 1), 168, "物攻") + "物理伤害。";
            case "tahongzhuiyue" -> "沿移动方向踏虹腾空，路径造成" + value(60 + 28 * (lv - 1), 128, "物攻")
                    + "物理伤害；腾空期间再次释放坠月，落点造成" + value(96 + 40 * (lv - 1), 200, "物攻")
                    + "物理伤害，精准落点额外提高20%。";
            case "jiuxiaofengwuliancheng" -> "凰阶时开启五拍凤舞；每拍有1.5秒衔接时间，最后0.60秒输入为完美拍。每拍造成"
                    + value(44 + 16 * (lv - 1), 80, "物攻")
                    + "物理伤害；五拍结束时累计至少3次完美节奏，追加凤落连城，造成"
                    + value(240 + 112 * (lv - 1), 240, "物攻") + "物理伤害；达到3次时不会提前释放。";

            // 月轮·绾月
            case "wanyue_passive" -> "射手普攻基础按80%物攻结算。月轮离体且未公转时，普攻从月轮位置发出并造成90%本次普攻伤害；接住月轮后，下次普攻改为20格穿透月华，造成118% + 每角色等级1%（最多15%）的普攻伤害。月华每多穿透1个目标伤害降低33%，最低33%；踏弦接轮再提高20%伤害和弹速。";
            case "zhiyuehuixian" -> "向前掷出月轮，去程造成" + value(72 + 32 * (lv - 1), 180, "物攻")
                    + "物理伤害；再次释放令月轮折返，回程造成" + value(48 + 24 * (lv - 1), 120, "物攻") + "物理伤害。";
            case "zheguangliuguang" -> "拥有2层充能；月轮离体时令其朝准星方向最多移动12格，沿途造成"
                    + value(56 + 24 * (lv - 1), 112, "物攻") + "物理伤害；大招期间改为调整公转。";
            case "taxianzuiyue" -> "沿移动方向突进5格；途中接住月轮时获得"
                    + value(72 + 32 * (lv - 1), 100, "物攻") + "护盾，并返还1层折轨。";
            case "manyuexuntian" -> "令月轮环绕前方区域" + (4 + lv) + "秒；每次接触敌人造成"
                    + value(88 + 40 * (lv - 1), 100, "物攻") + "物理伤害，期间仍可使用折轨和离体普攻。";

            // 天算·洛衡
            case "luoheng_passive" -> "管理三枚天算筹；5秒内用三条不同的筹边命中同一英雄，会触发闭环验算。";
            case "luochouyixing" -> "拥有3层充能；选点放置或移动一枚算筹，落点造成"
                    + value(48 + 24 * (lv - 1), 100, "法强") + "魔法伤害。";
            case "gouchenxian" -> "先后选择两枚算筹，短暂预告后沿两点之间发射贯线，造成"
                    + value(100 + 40 * (lv - 1), 220, "法强") + "魔法伤害，并记录本次使用的边。";
            case "yigonghuanshu" -> "瞄准一枚算筹并与其安全换位；起点与终点各造成"
                    + value(72 + 32 * (lv - 1), 120, "法强") + "魔法伤害。";
            case "sanyuantianluo" -> "以三枚算筹展开动态三角杀阵，边线每次造成"
                    + value(120 + 60 * (lv - 1), 80, "法强") + "魔法伤害；结束时向重心收束，造成"
                    + value(280 + 120 * (lv - 1), 260, "法强") + "魔法伤害。";

            // 机铳·沈砺
            case "shenli_passive" -> "射手普攻基础按80%物攻结算。右键切换瞄准，左键立即发射，发射后进入受攻速影响的射击后摇。瞄准时镜头略微放大并降低35%移速，沿出枪时视线准确发射，射程22格、弹速每秒50格；未瞄准时弹道随机偏离2°～10°，射程16格。六发灵丸按赤、青、金循环，空仓自动装填，连续4次命中英雄进入机心。";
            case "guanxingjiaoshe" -> "消耗当前灵丸发射高速校射弹，造成"
                    + value(24 + 9 * (lv - 1), 70, "物攻") + "物理伤害且可暴击；赤、青、金弹分别强化穿甲、减速与溅射。";
            case "xuanjidingtang" -> "开始24 tick节奏装填；再次释放按当前指针选择首发颜色，精准定膛会立即装满并获得20%攻速。";
            case "tuihuofanchong" -> "朝准星发射当前灵丸，造成" + value(72 + 40 * (lv - 1), 220, "物攻")
                    + "物理伤害，同时沿视角反方向移动约4格；向下射击可以向上反冲。";
            case "tiangongbuxilun" -> "立即装满弹仓并启动不息轮" + (5 + lv)
                    + "秒，获得25%攻速；命中英雄会复制已消耗弹色，集齐三色可强化下一发校射。";

            // 游隙·折川
            case "zhechuan_passive" -> "从游标去程命中英雄后，3秒内沿相反方向折返命中会造成56+8×角色等级+140%额外物攻物理伤害；每次触发使本局后续回文斩永久增加3伤害。";
            case "tourendingjie" -> "拥有3层充能；在12格内选点投放游标，落点造成"
                    + value(56 + 28 * (lv - 1), 120, "额外物攻") + "物理伤害，游标持续12秒。";
            case "xunbiaoguoxi" -> "瞄准16格内游标并沿路径突进，沿途造成"
                    + value(96 + 40 * (lv - 1), 260, "额外物攻") + "物理伤害；到达后平滑停在游标落点并开启3秒折返窗口。";
            case "chefengnidu" -> "没有折返路线时向后撤步并留下临时游标；有路线时逆向返回起点，沿途造成"
                    + value(64 + 32 * (lv - 1), 180, "额外物攻") + "物理伤害并缩短循标冷却。";
            case "zhubiaowujinhuilang" -> (10 + lv - 1) + "秒内把游标上限提高到5并重置W/E；连续折返3次解锁终结回浪，造成"
                    + value(260 + 100 * (lv - 1), 280, "额外物攻") + "物理伤害。";

            // 曜枢·明烛
            case "mingzhu_passive" -> "光球命中英雄获得曜能，非英雄获得较少；曜能上限100且死亡清零，只有主动开启大招后才会消耗。";
            case "liuguangguanzhu" -> "发射15格贯穿光球，造成" + value(96 + 40 * (lv - 1), 200, "法强")
                    + "魔法伤害；穿过折镜后提高伤害和弹速，并重置15格飞行距离。";
            case "juyaozhejing" -> "在8格内放置持续4秒的折镜；光球穿镜会强化，曜步穿镜会延长并加盾，光线穿镜会散射；再次释放可免费转动一次镜面。";
            case "yaobuhuihuan" -> "沿移动方向突进5格，无输入时向后；获得"
                    + value(20 + 10 * (lv - 1), 50, "法强") + "护盾，穿过折镜再延长4格并追加10%法强护盾。";
            case "yixiantianming" -> "至少30曜能时主动开启光线，每0.25秒造成"
                    + value(24 + 12 * (lv - 1), 16, "法强") + "魔法伤害；命中英雄耗能减半并积累光耀，10光耀使下一跳变为更粗更远的双倍伤害激光。";
            default -> skill.levelEffects(lv);
        };
    }

    /** 将长说明按完整语义分行，避免技能浮窗出现一整条难以阅读的长句。 */
    public static List<String> describeLines(Skill skill, int rawLevel) {
        return readableLines(describe(skill, rawLevel));
    }

    /**
     * 将任意技能说明按标点和可读宽度分行。中文优先保留完整分句，英文则优先在空格处换行。
     * 这里不依赖界面像素宽度，确保角色选择页和个人信息页能共享相同的稳定结果。
     */
    public static List<String> readableLines(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        List<String> lines = new ArrayList<>();
        for (String part : text.split("(?<=[；。！？!?])")) {
            String remaining = part.trim();
            while (remaining.length() > 42) {
                int cut = readableCut(remaining, 42);
                lines.add(remaining.substring(0, cut).trim());
                remaining = remaining.substring(cut).trim();
            }
            if (!remaining.isEmpty()) lines.add(remaining);
        }
        if (lines.isEmpty() && !text.isEmpty()) lines.add(text);
        return lines;
    }

    private static int readableCut(String text, int maximum) {
        int cut = -1;
        for (char marker : new char[] {'，', '、', '：', ',', ':', ' '}) {
            int candidate = text.lastIndexOf(marker, maximum - 1);
            if (candidate >= 12) {
                int included = marker == ' ' ? candidate : candidate + 1;
                cut = Math.max(cut, included);
            }
        }
        return cut >= 12 ? cut : maximum;
    }

    private static final Pattern ATTRIBUTE_DAMAGE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*\\+\\s*(\\d+(?:\\.\\d+)?)%\\s*(额外物攻|物攻|法强)");
    private static final Pattern MAX_HEALTH_DAMAGE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*\\+\\s*(\\d+(?:\\.\\d+)?)%\\s*(?:模组)?最大生命");

    /** 根据当前角色属性计算技能浮窗中的原始伤害；不预判目标防御、暴击或条件增伤。 */
    public static List<String> currentDamageLines(Skill skill, int rawLevel, int heroLevel,
            double physicalAttack, double spellPower, double bonusPhysicalAttack, double maxHealth) {
        String text = describe(skill, rawLevel);
        List<Double> values = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Matcher attribute = ATTRIBUTE_DAMAGE.matcher(text);
        while (attribute.find()) {
            String tail = clauseTail(text, attribute.end(), 40);
            if (!tail.contains("伤害") && !tail.contains("物伤")) continue;
            double stat = switch (attribute.group(3)) {
                case "法强" -> spellPower;
                case "额外物攻" -> bonusPhysicalAttack;
                default -> physicalAttack;
            };
            values.add(Double.parseDouble(attribute.group(1))
                    + Double.parseDouble(attribute.group(2)) * .01 * stat);
            types.add(damageType(tail));
            labels.add(null);
        }
        Matcher health = MAX_HEALTH_DAMAGE.matcher(text);
        while (health.find()) {
            String tail = clauseTail(text, health.end(), 32);
            if (!tail.contains("伤害") && !tail.contains("物伤")) continue;
            values.add(Double.parseDouble(health.group(1))
                    + Double.parseDouble(health.group(2)) * .01 * maxHealth);
            types.add(damageType(tail));
            labels.add(null);
        }
        if ("zhechuan_passive".equals(skill.getId())) {
            values.add(14.0 + heroLevel * 2.0 + bonusPhysicalAttack * .35);
            types.add("物理");
            labels.add("当前回文斩伤害");
        }
        if ("wuxiang_passive".equals(skill.getId())) {
            values.add(14.0 + heroLevel * 2.0 + physicalAttack * .35);
            types.add("真实");
            labels.add("当前破相伤害");
        }
        if ("tianxing_passive".equals(skill.getId())) {
            values.add(physicalAttack * (0.03 + Math.max(1, heroLevel) * 0.01));
            types.add("真实");
            labels.add("当前第三击附伤");
        }
        if ("wanyue_passive".equals(skill.getId())) {
            double normal = physicalAttack * .80 * (1.18 + Math.min(15, heroLevel) * .01);
            values.add(normal);
            types.add("物理");
            labels.add("当前接轮强化普攻");
            values.add(physicalAttack * .80 * (1.38 + Math.min(15, heroLevel) * .01));
            types.add("物理");
            labels.add("当前踏弦接轮强化普攻");
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String prefix = labels.get(i) != null ? labels.get(i)
                    : values.size() == 1 ? "当前原始伤害" : "当前原始伤害" + (i + 1);
            result.add(prefix + "：" + decimal(values.get(i)) + "（" + types.get(i)
                    + "，未计防御/暴击/目标条件）");
        }
        return result;
    }

    private static String damageType(String tail) {
        if (tail.contains("真实")) return "真实";
        if (tail.contains("魔法")) return "魔法";
        return "物理";
    }

    private static String clauseTail(String text, int from, int maxLength) {
        int end = Math.min(text.length(), from + maxLength);
        int semicolon = text.indexOf('；', from);
        int period = text.indexOf('。', from);
        if (semicolon >= 0) end = Math.min(end, semicolon);
        if (period >= 0) end = Math.min(end, period);
        return text.substring(from, end);
    }

    private static String value(double base, double ratio, String stat) {
        return decimal(base) + " + " + decimal(ratio) + "%" + stat;
    }

    private static String decimal(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
