package com.myriad.character.skills.keheng;
import com.myriad.character.ability.*;
/** Entry edges and the independent real-time internal cooldown belong to the field owner. */
public final class ConstantStepSkill extends KehengSkill {
    public ConstantStepSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    public static void entered(net.minecraft.server.level.ServerPlayer p){
        var skill=com.myriad.character.rework.FirstGroupSkills.node("keheng",1);
        var data=com.myriad.player.PlayerDataProvider.get(p);if(data.skillLevel(skill.getId())<=0)return;
        var c=AbilityRuntime.context(p,skill);if(c.memory().cooldown>c.now())return;
        c.memory().cooldown=c.now()+240;c.shield(p,c.value(75,15,.3f),40);c.buff("g1_keheng_step_cd",240);KehengAudio.play(p,"shield");
    }
}
