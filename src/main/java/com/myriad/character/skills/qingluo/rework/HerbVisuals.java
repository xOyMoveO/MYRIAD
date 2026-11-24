package com.myriad.character.skills.qingluo.rework;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityVisuals;
import net.minecraft.world.entity.LivingEntity;

import static com.myriad.character.ability.AbilityVfxGeometry.BURST;
import static com.myriad.character.ability.AbilityVfxGeometry.CHARGE;

final class HerbVisuals {
    static final int RESTORATION = 201;

    private HerbVisuals() {}

    static void restoration(Cast cast, LivingEntity target, double radius) {
        AbilityVisuals.following(cast,BURST,target,radius,RESTORATION,10);
    }

    static void essence(Cast cast, int count) {
        AbilityVisuals.following(cast,CHARGE,cast.player,count,0,8);
    }

    static void heal(Cast cast, LivingEntity target, float amount, double radius) {
        float before = target.getHealth();
        cast.heal(target, amount);
        if (target.getHealth() > before) restoration(cast,target,radius);
    }
}
