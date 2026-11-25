package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.character.ability.AbilityRuntime.Hit;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.combat.DamageCalc;
import com.myriad.game.VisionSystem;
import net.minecraft.world.entity.LivingEntity;

final class DuskMark extends LuoxiaSkill {
    DuskMark(Definition definition) { super(definition); }

    @Override public void release(Cast c) {
        LuoxiaVfx.shot(c, false, 1.18f);
        LuoxiaArrow.launch(c, c.player.getLookAngle(), 20, 1.6, LuoxiaArrow.Shape.MARK, false,
                target -> { c.damage(target, damageAtRank(c, 52, 80, .40f), DamageCalc.Kind.PHYSICAL); return true; });
    }

    @Override public void onHit(Hit hit) {
        var c = AbilityRuntime.context(hit.player(), FirstGroupSkills.node(LuoxiaRework.ID, 2));
        var mark = c.memory("mark");
        LivingEntity target = hit.target();
        if (getId().equals(hit.skillId()) && !hit.basic()) {
            if (!c.hero(target) || !target.isAlive()) return;
            mark.target = target.getUUID();
            mark.until = c.now() + 60;
            int version = ++mark.count;
            c.whileActive(60, age -> {
                if (version != mark.count || c.now() >= mark.until || !target.isAlive() || target.level() != c.player.level()) return false;
                if (age % 4 == 0 && c.player.hasLineOfSight(target) && VisionSystem.canSee(c.player, target))
                    LuoxiaVfx.mark(c, target);
                return true;
            });
            return;
        }
        if (!hit.basic() && !LuoxiaArrow.isSkillArrow(hit.skillId())) return;
        if (c.now() >= mark.until || !target.getUUID().equals(mark.target)) return;
        mark.until = 0;
        LuoxiaVfx.impact(c, target.getBoundingBox().getCenter(), target.getLookAngle(), true);
        c.damage(target, damageAtRank(c, 25, 45, .32f), DamageCalc.Kind.PHYSICAL, false);
    }
}
