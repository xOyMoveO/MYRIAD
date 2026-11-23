package com.myriad.character.skills.sulan.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import static com.myriad.character.ability.AbilityVfxGeometry.*;

final class WindVisuals {
    private WindVisuals() {}

    static void cut(Cast cast, LivingEntity target, double size, double degrees) {
        Vec3 hit=target.position().add(0,target.getBbHeight()*.52,0);
        AbilityVisuals.emit(cast,ARC,hit,hit.add(cast.forward(1)),size,degrees,8,false);
    }

    static void mark(Cast cast, LivingEntity target) {
        AbilityVisuals.following(cast,SEAL,target,.54,5);
    }

    static void consumeMark(Cast cast, LivingEntity target) {
        AbilityVisuals.following(cast,BURST,target,.65,10);
    }

    static void turn(Cast cast, Vec3 end, Vec3 outbound) {
        AbilityVisuals.emit(cast,BURST,end,end.add(outbound),.84,180,11,false);
    }

    static void domainWind(Cast cast, LivingEntity target, boolean adverse) {
        Vec3 motion=target.getDeltaMovement().multiply(1,0,1);
        Vec3 heading=(motion.lengthSqr()>=.001?motion:target.getLookAngle().multiply(1,0,1)).normalize();
        if(heading.lengthSqr()<1E-8)heading=cast.forward(1);
        AbilityVisuals.following(cast,TRAIL,target,heading,.55,adverse?180:90,8);
    }
}
