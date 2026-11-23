package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilityVfxGeometry;
import com.myriad.network.AbilityVfxPayload;

/** Pose signals for the caster's first-person hands. */
public final class WindCastAnimation {
    public static final int CAST=320, WINDUP=321, CANCEL=322, DASH_START=323, DASH_HOLD=324, DASH_STOP=325;
    public static final int RELEASE_LIFE=15, REFRESH_LIFE=7;

    private WindCastAnimation(){}

    public static boolean cue(AbilityVfxPayload p){
        return p.theme()==4&&p.variant()>=2&&p.kind()==AbilityVfxGeometry.CHARGE
                &&p.angle()>=CAST&&p.angle()<=DASH_STOP&&p.angle()==(int)p.angle();
    }

    public static boolean inMotion(int cue){
        return cue==DASH_START||cue==DASH_HOLD;
    }
}
