package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.network.AbilityVfxPayload;

/** Pose signals for the caster's first-person hands. */
public final class InkCastAnimation {
    public static final int AIM=310, RELEASE=311, SELF=312, WINDUP=313, STOP=314;
    public static final int BASIC=315, ACTIVATE=316;
    public static final int CAST_LIFE=15, REFRESH_LIFE=8;

    private InkCastAnimation(){}

    public static boolean cue(AbilityVfxPayload p){
        return p.theme()==3&&p.variant()>=2&&p.kind()==AbilityVfxGeometry.CHARGE
                &&p.angle()>=AIM&&p.angle()<=STOP&&p.angle()==(int)p.angle();
    }

    public static boolean firstPersonCue(AbilityVfxPayload p){
        return p.personal()&&p.theme()==3&&p.kind()==AbilityVfxGeometry.CHARGE
                &&(p.angle()==BASIC&&p.variant()==0||p.angle()==ACTIVATE&&(p.variant()==5||p.variant()==7));
    }
}
