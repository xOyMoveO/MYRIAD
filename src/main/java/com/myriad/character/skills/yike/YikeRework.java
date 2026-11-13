package com.myriad.character.skills.yike;

import com.myriad.character.ability.*;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.player.PlayerDataProvider;
import net.minecraft.server.level.ServerPlayer;

/** Yike's eight independent candidates and ordinary lifecycle dispatch. */
public final class YikeRework {
    public static final int INITIAL_HEALTH=550,HEALTH_PER_LEVEL=75;
    private YikeRework(){}
    public static AbilitySkill bind(AbilitySkill.Definition d){return switch(d.index()){
        case 0->new RainbowCalibration(d);case 1->new SteadyBarrier(d);
        case 2->new RainbowShell(d);case 3->new RefractionDenial(d);
        case 4->new FixedArtillery(d);case 5->new MobileArray(d);
        case 6->new DistantBombardment(d);case 7->new PrismaticBeam(d);
        default->throw new IllegalArgumentException("Invalid Yike branch");};}
    public static AbilitySkill q(ServerPlayer p){return FirstGroupSkills.node("yike",2);}
    public static void tick(ServerPlayer p){
        if(!"yike".equals(PlayerDataProvider.get(p).getCharacterId()))return;
        var winding=AbilityRuntime.winding(p);
        if(winding!=null&&winding.skill.role().equals("yike")){
            String stage=winding.skill.index()==2?"muzzle":winding.skill.index()<6?"deploy":"beam_charge";
            YikeVfx.event(winding,stage,p.getEyePosition(),p.getEyePosition().add(p.getLookAngle()),.3f,2);
        }
        YikeStance.tick(p);YikeBasicAttack.tick(p);PrismaticBeam.tick(p);DistantBombardment.tick(p);YikeState.sync(p);
    }
    public static void clear(ServerPlayer p){
        DistantBombardment.clear(p);YikeStance.clear(p);YikeBasicAttack.clear(p);PrismaticBeam.clear(p);
        RainbowCalibration.clear(p);SteadyBarrier.clear(p);YikeState.clear(p);
    }
}
