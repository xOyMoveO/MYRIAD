package com.myriad.character.skills.chixiao.rework;
import com.myriad.character.ability.*;
import com.myriad.character.ability.AbilityRuntime.Cast;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Independent reusable branch. */
public final class CrimsonParrySkill extends AbilitySkill {
    public static final double RANGE = 5, DAMAGE_TAKEN_RATE = .65;
    public static final int BRACE_TICKS = 20, STUN_TICKS = 12;
    private static final AbilityDamageFormula DAMAGE =
            AbilityDamageFormula.physicalFromSpell(110, 31.25f, .75f, .5f);

    @Override public List<AbilityDamageFormula> damageFormulas() {
        return List.of(DAMAGE);
    }

 public CrimsonParrySkill(Definition definition){super(definition);}

@Override public void release(Cast c){var m=c.memory();m.until=c.now()+BRACE_TICKS;m.count=0;WarSpiritHandCues.brace(c);c.later(BRACE_TICKS,false,()->{m.until=0;if(m.count==0)return;WarSpiritHandCues.follow(c);c.cone(RANGE,100,DAMAGE.value(c),DAMAGE.kind());for(var t:c.enemies(c.player.position(),RANGE)){Vec3 to=t.position().subtract(c.player.position()).multiply(1,0,1);if(to.lengthSqr()<.01||to.normalize().dot(c.forward(1))>=Math.cos(Math.toRadians(50)))c.root(t,STUN_TICKS,true);}});}
@Override public double incomingMultiplier(Cast c){if(c.memory().until>c.now()){c.memory().count=1;return DAMAGE_TAKEN_RATE;}return 1;}
@Override public void present(Cast c){AbilityVisuals.following(c,AbilityVfxGeometry.SHIELD,c.player,.8,BRACE_TICKS);}
}

