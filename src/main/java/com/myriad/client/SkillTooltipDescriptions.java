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

        if ("tianxing_passive".equals(skill.getId())) {
            values.add(physicalAttack * (0.03 + Math.max(1, heroLevel) * 0.01));
            types.add("真实");
            labels.add("当前第三击附伤");
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
