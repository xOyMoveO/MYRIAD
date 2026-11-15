package com.myriad.client;

import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import com.myriad.character.Skill;

/**
 * 技能图标槽：渲染 assets/myriad/textures/skill_icons/generated 下的角色技能图标；
 * 大招（终极技能）金色边框 + 左上角「大」标记。
 */
public class SkillIcon {
    /** 图标本身已有角色化金属边框；这里仅提供克制的外轮廓与状态色。 */
    private static final int FRAME_OUTER = 0xD0141821;
    private static final int FRAME_SHADOW = 0x80000000;
    private static final int ACCENT_READY = 0xB8B9C5D0;
    private static final int ACCENT_CD = 0xA0646A76;
    private static final int ACCENT_ULTIMATE = 0xC9E2B94B;
    private static final Map<String, String> GENERATED_ICON_PATHS = Map.ofEntries(
            Map.entry("keheng_kehen", "keheng/kehen"),
            Map.entry("keheng_hengbu", "keheng/hengbu"),
            Map.entry("keheng_chizhen", "keheng/chizhen"),
            Map.entry("keheng_cuopaibu", "keheng/cuopaibu"),
            Map.entry("keheng_chijie", "keheng/chijie"),
            Map.entry("keheng_suixing", "keheng/suixing"),
            Map.entry("keheng_yimiao", "keheng/yimiao"),
            Map.entry("keheng_zhongke", "keheng/zhongke"),

            Map.entry("shuo_cangfeng", "shuo/passive"),
            Map.entry("shuo_jianqie", "shuo/shuo_jianqie"),
            Map.entry("shuo_nadao", "shuo/shuo_nadao"),
            Map.entry("shuo_qiaofan", "shuo/shuo_qiaofan"),
            Map.entry("shuo_qiaofan_2", "shuo/shuo_qiaofan_2"),
            Map.entry("shuo_yishan", "shuo/shuo_yishan"),
            Map.entry("shuo_duanliu", "shuo/shuo_duanliu"),
            Map.entry("shuo_wusheng", "shuo/shuo_wusheng"),
            Map.entry("shuo_canyue", "shuo/shuo_canyue"),
            Map.entry("shuo_canyue_2", "shuo/shuo_canyue_2"),
            Map.entry("yingxiao_passive", "yingxiao/passive"),
            Map.entry("yingxi", "yingxiao/yingxi"),
            Map.entry("huaying", "yingxiao/huaying"),
            Map.entry("yingsha", "yingxiao/yingsha"),
            Map.entry("lingdun", "yingxiao/lingdun"),
            Map.entry("xuanchen_passive", "xuanchen/passive"),
            Map.entry("lingfu", "xuanchen/lingfu"),
            Map.entry("chuansongzhen", "xuanchen/chuansongzhen"),
            Map.entry("moyanghuzhen", "xuanchen/moyanghuzhen"),
            Map.entry("zhoutianfuzhen", "xuanchen/zhoutianfuzhen"),
            Map.entry("sulan_passive", "sulan/passive"),
            Map.entry("jifengtuci", "sulan/jifengtuci"),
            Map.entry("lingfengbiaoji", "sulan/lingfengbiaoji"),
            Map.entry("fengqiang", "sulan/fengqiang"),
            Map.entry("yufengqianli", "sulan/yufengqianli"),
            Map.entry("fenjue_passive", "fenjue/passive"),
            Map.entry("lieyanzhan", "fenjue/lieyanzhan"),
            Map.entry("rongyanlaolong", "fenjue/rongyanlaolong"),
            Map.entry("fenjuebaofa", "fenjue/fenjuebaofa"),
            Map.entry("fentianhuohai", "fenjue/fentianhuohai"),
            Map.entry("tianxing_passive", "tianxing/passive"),
            Map.entry("shanlei", "tianxing/shanlei"),
            Map.entry("leijiezhan", "tianxing/leijiezhan"),
            Map.entry("tianleihuti", "tianxing/tianleihuti"),
            Map.entry("jiutianshenlei", "tianxing/jiutianshenlei"),
            Map.entry("lingyuan_passive", "lingyuan/passive"),
            Map.entry("poyunshi", "lingyuan/poyunshi"),
            Map.entry("huaxingweijian", "lingyuan/huaxingweijian"),
            Map.entry("wanjianjue", "lingyuan/wanjianjue"),
            Map.entry("sanqianguizong", "lingyuan/sanqianguizong"),
            Map.entry("hanze_passive", "hanze/passive"),
            Map.entry("bingzhuishu", "hanze/bingzhuishu"),
            Map.entry("shuangdun", "hanze/shuangdun"),
            Map.entry("jueduilingdu", "hanze/jueduilingdu"),
            Map.entry("qianlibingfeng", "hanze/qianlibingfeng"),
            Map.entry("qingluo_passive", "qingluo/passive"),
            Map.entry("qingtengyu", "qingluo/qingtengyu"),
            Map.entry("huxindanzhen", "qingluo/huxindanzhen"),
            Map.entry("qiansiduoe", "qingluo/qiansiduoe"),
            Map.entry("wanmuchunhui", "qingluo/wanmuchunhui"),
            Map.entry("chixiao_passive", "chixiao/passive"),
            Map.entry("duanyuezhan", "chixiao/duanyuezhan"),
                        Map.entry("rivet_station_armor", "rivet/rivet_station_armor"),
            Map.entry("rivet_breach_rhythm", "rivet/rivet_breach_rhythm"),
            Map.entry("rivet_impact_hammer", "rivet/rivet_impact_hammer"),
            Map.entry("rivet_tactical_anchor", "rivet/rivet_tactical_anchor"),
            Map.entry("rivet_emergency_strut", "rivet/rivet_emergency_strut"),
            Map.entry("rivet_tow_cable", "rivet/rivet_tow_cable"),
            Map.entry("rivet_tow_cable_2", "rivet/rivet_tow_cable_2"),
            Map.entry("rivet_hold_protocol", "rivet/rivet_hold_protocol"),
            Map.entry("rivet_breach_charge", "rivet/rivet_breach_charge"),
            Map.entry("yike_rainbow_mark", "yike/yike_rainbow_mark"),
            Map.entry("yike_steady_barrier", "yike/yike_steady_barrier"),
            Map.entry("yike_rainbow_shell", "yike/yike_rainbow_shell"),
            Map.entry("yike_refraction_denial", "yike/yike_refraction_denial"),
            Map.entry("yike_fixed_artillery", "yike/yike_fixed_artillery"),
            Map.entry("yike_mobile_array", "yike/yike_mobile_array"),
            Map.entry("yike_distant_bombardment", "yike/yike_distant_bombardment"),
            Map.entry("yike_prismatic_beam", "yike/yike_prismatic_beam"),
            Map.entry("zhuihunta", "chixiao/zhuihunta"),
            Map.entry("chixiaofanfeng", "chixiao/chixiaofanfeng"),
            Map.entry("pojunwuhui", "chixiao/pojunwuhui"),
            Map.entry("yunsheng_passive", "yunsheng/passive"),
            Map.entry("zhihechuanyin", "yunsheng/zhihechuanyin"),
            Map.entry("dingqisuo", "yunsheng/dingqisuo"),
            Map.entry("huiyinjiebiao", "yunsheng/huiyinjiebiao"),
            Map.entry("zhongshengguizong", "yunsheng/zhongshengguizong"));

    /** 技能图标以 GUI sprite 渲染；sprite 文件指向 generated 目录中的图标。 */
    public static boolean hasIcon(String skillId) {
        var ability = com.myriad.character.rework.FirstGroupSkills.byId(skillId);
        if (ability != null) skillId = ability.iconAlias();
        String path = GENERATED_ICON_PATHS.get(skillId);
        return path != null && hasResource("textures/gui/sprites/skill_icons/generated/" + path + ".png");
    }

    private static Identifier iconSprite(String skillId) {
        var ability = com.myriad.character.rework.FirstGroupSkills.byId(skillId);
        if (ability != null) skillId = ability.iconAlias();
        return Identifier.fromNamespaceAndPath("myriad",
                "skill_icons/generated/" + GENERATED_ICON_PATHS.get(skillId));
    }

    /** 二段技能在服务器同步的再施放窗口内切换为独立图标。 */
    private static String displayedSkillId(String skillId) {
        String recastBuff = switch (skillId) {
            case "rivet_tow_cable" -> "rivet_tow_cable_recast";
            case "shuo_qiaofan" -> "shuo_qiaofan_recast";
            case "shuo_canyue" -> "shuo_canyue_recast";
            default -> null;
        };
        return recastBuff != null && ClientDataHolder.buffs().getOrDefault(recastBuff, 0) > 0
                ? skillId + "_2" : skillId;
    }

    /** 资源是否存在（通用，供装备图标等复用） */
    public static boolean hasResource(String path) {
        return net.minecraft.client.Minecraft.getInstance().getResourceManager()
                .getResource(Identifier.fromNamespaceAndPath("myriad", path))
                .isPresent();
    }

    public static void draw(GuiGraphicsExtractor gui, Font font, int x, int y, int size,
            Skill skill, boolean ultimate, boolean ready, String keyLabel) {
        boolean effectiveReady = ready;

        int accent = ultimate && effectiveReady ? ACCENT_ULTIMATE : (effectiveReady ? ACCENT_READY : ACCENT_CD);
        // 用阴影和深色细描边承托图标，避免原先高亮四边框与图标自带边框互相抢眼。
        gui.fill(x + 1, y + 1, x + size + 2, y + size + 2, FRAME_SHADOW);
        gui.fill(x - 1, y - 1, x + size + 1, y + size + 1, FRAME_OUTER);
        String displayedId = displayedSkillId(skill.getId());
        if (hasIcon(displayedId)) {
            gui.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, iconSprite(displayedId),
                    x, y, size, size);
        } else if (ultimate) {
            gui.text(font, "大", x + (size - font.width("大")) / 2,
                    y + (size - font.lineHeight) / 2, ACCENT_ULTIMATE);
        } else {
            int color = effectiveReady ? 0xFFFFFFFF : 0xFF9A9A9A;
            gui.text(font, keyLabel, x + (size - font.width(keyLabel)) / 2,
                    y + (size - font.lineHeight) / 2, color);
        }
        // 只在外轮廓上保留两条短状态线，冷却/大招状态仍清晰，但不遮挡图标画面。
        gui.fill(x + 3, y - 1, x + size - 3, y, accent);
        gui.fill(x + 3, y + size, x + size - 3, y + size + 1, accent);
        if (skill instanceof com.myriad.character.ability.AbilitySkill ability) {
            int index = ability.index();
            if(ability.role().equals("shuo")&&!ability.isPassive()){
                boolean recast=ClientDataHolder.buffTicks(skill.getId()+"_recast")>0;
                boolean drawn=ClientDataHolder.syncedCounter("g1_shuo_drawn")>0;
                String form=recast?"续":drawn?"刃":"鞘";
                gui.fill(x+size-11,y+size-12,x+size,y+size,0xDD101C29);
                gui.text(font,form,x+size-10,y+size-11,drawn?0xFFCEEEFF:0xFFE8D6A7);
            }
            if (index < 2 || index >= 4) gui.text(font, (index & 1) == 0 ? "A" : "B", x + 2, y + 1, 0xFFFFFFC2);
            if (ClientDataHolder.buffs().getOrDefault(skill.getId()+"_recast",0) > 0) {
                gui.fill(x,y+size-2,x+size,y+size,0xFFFF93D9);
                gui.text(font,"↩",x+size-8,y+1,0xFFFFA7DD);
            }
        }
    }

    /** Buff 栏的紧凑技能图标；沿用技能栏同一贴图，不绘制按键、等级和冷却状态。 */
    public static void drawBuff(GuiGraphicsExtractor gui, Font font, int x, int y, int size, Skill skill) {
        gui.fill(x - 1, y - 1, x + size + 1, y + size + 1, FRAME_OUTER);
        if (hasIcon(skill.getId())) {
            gui.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, iconSprite(skill.getId()),
                    x, y, size, size);
        } else {
            String name = net.minecraft.network.chat.Component.translatable("skill.myriad." + skill.getId()).getString();
            String mark = name.isEmpty() ? "◆" : name.substring(0, 1);
            gui.text(font, mark, x + (size - font.width(mark)) / 2, y + (size - font.lineHeight) / 2, 0xFFBFEFFF);
        }
    }

    /** 野怪奖励 Buff 的独立图标；资源位于 gui/sprites/buff_icons。 */
    public static boolean hasMonsterBuffIcon(String buffId) {
        return hasResource("textures/gui/sprites/buff_icons/" + buffId + ".png");
    }

    public static void drawMonsterBuff(GuiGraphicsExtractor gui, int x, int y, int size, String buffId) {
        gui.fill(x - 1, y - 1, x + size + 1, y + size + 1, FRAME_OUTER);
        gui.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                Identifier.fromNamespaceAndPath("myriad", "buff_icons/" + buffId), x, y, size, size);
    }

}
