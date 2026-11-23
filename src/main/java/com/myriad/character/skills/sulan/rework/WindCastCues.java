package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilitySkill;
import com.myriad.character.ability.AbilityVisuals;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.CHARGE;
import static com.myriad.character.skills.sulan.rework.WindCastAnimation.*;

final class WindCastCues {
    private WindCastCues(){}

    static void release(Cast cast){release(cast,cast.forward(1));}
    static void release(Cast cast,Vec3 direction){emit(cast,CAST,0,RELEASE_LIFE,direction);}

    static void windup(Cast cast,boolean active){
        if(!active||cast.windupProgress()==0||cast.now()%2==0)
            emit(cast,active?WINDUP:CANCEL,cast.windupProgress()*16,REFRESH_LIFE,cast.forward(1));
    }

    static void dash(Cast cast,AbilitySkill.DashPose phase,Vec3 direction){
        int signal=switch(phase){
            case START->DASH_START;case MOVING->DASH_HOLD;case FINISH->DASH_STOP;case CANCEL->CANCEL;
        };
        if(phase!=AbilitySkill.DashPose.MOVING||cast.now()%2==0)
            emit(cast,signal,0,phase==AbilitySkill.DashPose.FINISH?RELEASE_LIFE:REFRESH_LIFE,direction);
    }

    private static void emit(Cast cast,int signal,double progress,int life,Vec3 direction){
        Vec3 from=cast.player.position();
        AbilityVisuals.emit(cast,CHARGE,from,from.add(direction.normalize()),progress,signal,life,true);
    }
}
