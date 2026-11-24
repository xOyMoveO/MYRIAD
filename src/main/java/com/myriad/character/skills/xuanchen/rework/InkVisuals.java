package com.myriad.character.skills.xuanchen.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

final class InkVisuals {
    private InkVisuals() {}

    static void deliver(Cast cast, LivingEntity target, double size) {
        Vec3 end=target.position().add(0,.85,0);
        Vec3 start=cast.player.position().add(0,.85,0).add(cast.forward(.35));
        AbilityVisuals.emit(cast,BEAM,start,end,size,0,7,false);
    }

    static void halfSeal(Cast cast, LivingEntity target) {
        AbilityVisuals.following(cast,SEAL,target,.43,5);
    }

    static void binding(Cast cast, LivingEntity target, int lifespan) {
        AbilityVisuals.following(cast,PILLAR,target,.64,lifespan);
    }

    static void shieldTorn(Cast cast, LivingEntity target, DamageSource hit) {
        if(!cast.player.isAlive()||cast.player.hasDisconnected()||cast.player.level()!=target.level()
                ||!cast.skill.role().equals(cast.data.getCharacterId()))return;
        Vec3 source=hit==null?null:hit.getSourcePosition();
        Vec3 toward=source==null?Vec3.ZERO:source.subtract(target.position()).multiply(1,0,1);
        if(toward.lengthSqr()<.04&&hit!=null&&hit.getEntity()!=null)
            toward=hit.getEntity().position().subtract(target.position()).multiply(1,0,1);
        if(toward.lengthSqr()<.04)toward=cast.forward(1);
        AbilityVisuals.following(cast,BURST,target,toward.normalize(),.625,270,8);
    }

    static void portalTravel(Cast cast, Vec3 from, Vec3 to) {
        Vec3 heading=to.subtract(from).multiply(1,0,1).normalize();
        if(heading.lengthSqr()<1E-8)heading=cast.forward(1);
        Vec3 entry=from.add(0,.055,0),exit=to.add(0,.055,0);
        AbilityVisuals.emit(cast,BURST,entry,entry.add(heading),.9,90,10,false);
        AbilityVisuals.emit(cast,BURST,exit,exit.subtract(heading),.9,180,10,false);
    }

    static void arrayPulse(Cast cast, Vec3 center, int degrees) {
        AbilityVisuals.emit(cast,BURST,center,center.add(cast.forward(1)),6,degrees,12,false);
    }

    static void warn(Cast cast, Vec3 center, double radius, int ticks) {
        Vec3 floor=center.add(0,.055,0);
        AbilityVisuals.emit(cast,SEAL,floor,floor.add(cast.forward(1)),radius,359,ticks,false);
    }
    static void closeBoundary(Cast cast, Vec3 center) {
        Vec3 floor=center.add(0,.07,0);
        AbilityVisuals.emit(cast,BURST,floor,floor.add(cast.forward(1)),4,0,12,false);
    }
}
