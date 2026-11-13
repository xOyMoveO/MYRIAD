package com.myriad.character.skills.tianxing.rework;

import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import static com.myriad.combat.DamageCalc.Kind.MAGIC;
import static com.myriad.network.ThunderSkillViewPayload.*;

/** Stable M-B branch, now a charged travelling sword wave, not an instantaneous lance ray. */
public final class ThunderSlashSkill extends AbilitySkill {
    public static final int WINDUP=10;
    public static final double RANGE=20,SPEED=4,RADIUS=1;
    public ThunderSlashSkill(Definition definition){super(definition);}
    @Override public boolean hasOwnCastSound(){return true;}
    @Override public void present(Cast c){AbilityVisuals.charging(c,WINDUP,RANGE,.45);}
    @Override public void release(Cast c){
        ThunderSkillPresentation.charged(c,WINDUP,SLASH_READY,SLASH_FIRE,()->{
            ThunderAudio.play(c,ThunderAudio.Cue.CAST);
            boolean[] rooted={false};
            c.actionProjectile(RANGE,SPEED,RADIUS,true,false,(target,back)->{
                ThunderAudio.play(c,ThunderAudio.Cue.HIT,target.position());
                c.damage(target,c.value(95,24,.95f),MAGIC);
                if(!rooted[0]&&c.hero(target)){rooted[0]=true;c.root(target,12,false);}
            });
        });
    }
}
