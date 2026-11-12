package com.myriad.client.gui;
import com.myriad.character.CharacterType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Scrollable role walkthrough, available without consuming a pick. */
public final class CharacterGuideScreen extends Screen {
    private final Screen parent;
    private final CharacterType type;
    private int scroll, maximum;
    public CharacterGuideScreen(Screen parent, CharacterType type) {
        super(Component.translatable(type.nameKey())); this.parent = parent; this.type = type;
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(width/2-50,height-27,100,20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (!com.myriad.client.ClientDataHolder.modEnabled()
                || parent instanceof PickScreen && !com.myriad.client.ClientDataHolder.pickActive()) onClose();
    }
    @Override public void onClose() { net.minecraft.client.Minecraft.getInstance().setScreenAndShow(parent); }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        scroll = Math.clamp(scroll-(int)(dy*24),0,maximum); return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor gui,int mx,int my,float partial) {
        gui.fill(0,0,width,height,0xF0101D30);
        int w=Math.min(440,width-32), x=(width-w)/2;
        com.myriad.client.CharacterPortrait.draw(gui,type,x,6,38,1);
        gui.text(font,title,x+46,20,0xFFFFD77A);
        gui.enableScissor(x,50,x+w,height-34);
        int y=56-scroll;
        var paragraphs=new java.util.ArrayList<Component>();
        paragraphs.add(Component.translatable("hero.guide."+type.getId()));
        boolean reworked=com.myriad.character.rework.FirstGroupSkills.active(type.getId());
        if(reworked){
            paragraphs.add(Component.literal("20级、20技能点。被动、主要、终极各二选一；两个常规技能均可学。先在个人信息中学习，再用自己设置的技能按键释放。力量增强普攻，智慧增强技能；金币用于五属性和补给。"));
            String[] labels={"被动A","被动B","常规一","常规二","主要A","主要B","终极A","终极B"};
            for(var ability:com.myriad.character.rework.FirstGroupSkills.candidates(type.getId())){
                paragraphs.add(Component.literal(labels[ability.index()]+" · "+ability.displayName()+" / 上限"+ability.maxRank()+"级"));
                paragraphs.add(Component.literal(com.myriad.character.ability.AbilityFormulaText.evaluate(ability.description(),1,30)+"（1级、初始技能强度30预览）"));
                if(!ability.isPassive())paragraphs.add(Component.translatable("hero.guide.resource",ability.getManaCost(),ability.getCooldownTicks(1)/20.0));
            }
        }else{
        paragraphs.add(Component.translatable("hero.guide.controls"));
        for (var passive : type.passives()) {
            paragraphs.add(Component.translatable(passive.nameKey()));
            paragraphs.add(Component.literal(com.myriad.client.SkillTooltipDescriptions.describe(passive,1)));
        }
        for (int slot = 0; slot < CharacterType.SKILL_SLOTS; slot++) {
            var skill=type.skill(slot); if(skill==null)continue;
            paragraphs.add(Component.literal("QWERF".charAt(slot)+" · ").append(Component.translatable(skill.nameKey())));
            paragraphs.add(Component.literal(com.myriad.client.SkillTooltipDescriptions.describe(skill,1)));
            paragraphs.add(Component.translatable("hero.guide.resource",skill.getManaCost(),skill.getCooldownTicks(1)/20.0));
        }
        }
        for(var paragraph:paragraphs){for(var line:font.split(paragraph,w-12)){gui.text(font,line,x+4,y,0xFFDAE6EE);y+=12;}y+=10;}
        maximum=Math.max(0,y+scroll-(height-34));
        gui.disableScissor(); super.extractRenderState(gui,mx,my,partial);
    }
}
