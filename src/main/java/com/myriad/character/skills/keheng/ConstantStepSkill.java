package com.myriad.character.skills.keheng;
import com.myriad.network.WeaponPosePayload;
import com.myriad.network.WeaponPosePayload.Move;
import com.myriad.character.ability.*;
/** Entry edges and the independent real-time internal cooldown belong to the field owner. */
public final class ConstantStepSkill extends KehengSkill {
    public static final int SHIELD_TICKS = 40, COOLDOWN_TICKS = 240;
    private static final SupportAmount SHIELD = new SupportAmount("shield", 75, 18, .3f);

    @Override public java.util.List<SupportAmount> supportAmounts() { return java.util.List.of(SHIELD); }

    public ConstantStepSkill(Definition d){super(d);}
    @Override public void release(AbilityRuntime.Cast c){}
    public static void entered(net.minecraft.server.level.ServerPlayer p){
        var skill=com.myriad.character.rework.FirstGroupSkills.node("keheng",1);
        var data=com.myriad.player.PlayerDataProvider.get(p);if(data.skillLevel(skill.getId())<=0)return;
        var c=AbilityRuntime.context(p,skill);if(c.memory().cooldown>c.now())return;
        c.memory().cooldown=c.now()+COOLDOWN_TICKS;c.shield(p,SHIELD.value(c),SHIELD_TICKS);WeaponPosePayload.play(p, Move.FIELD_SHIELD, 6);
        KehengVisuals.watchShield(c,40);c.buff("g1_keheng_step_cd",COOLDOWN_TICKS);KehengAudio.play(p,"shield");
    }
}
