package com.myriad.character.skills.fenjue.rework;

import com.myriad.network.AbilityVfxPayload;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

/** Visual-only renewal policy. Never changes the field's server damage or duration. */
public final class FlameVisualMerge {
    private FlameVisualMerge(){}
    private static boolean family(AbilityVfxPayload a,AbilityVfxPayload b){return FlameMajorVfx.owns(a)&&FlameMajorVfx.owns(b)
            &&a.owner()==b.owner()&&a.variant()==b.variant()&&a.personal()==b.personal()&&a.anchor()==b.anchor();}
    private static boolean center(AbilityVfxPayload a,AbilityVfxPayload b){return a.from().distanceToSqr(b.from())<.01;}
    private static boolean field(AbilityVfxPayload p){return p.kind()==SEAL&&p.angle()!=359&&!p.personal()&&p.life()>=20;}
    public static boolean obsolete(AbilityVfxPayload incoming,AbilityVfxPayload active){
        return family(incoming,active)&&center(incoming,active)&&incoming.kind()==SEAL&&incoming.angle()==359
                &&field(active)&&active.started()>=incoming.started();
    }
    public static boolean replaces(AbilityVfxPayload incoming,AbilityVfxPayload active){
        if(!family(incoming,active)||incoming.started()<active.started())return false;
        if(incoming.kind()==CHARGE&&active.kind()==CHARGE)return true;
        if(!center(incoming,active)||incoming.kind()!=SEAL||active.kind()!=SEAL)return false;
        if(field(incoming)&&active.angle()==359)return true;
        return Math.abs(incoming.radius()-active.radius())<.02
                &&(field(incoming)&&field(active)||incoming.angle()==359&&active.angle()==359);
    }
}
