package com.myriad.character.skills.luoxia;

import com.myriad.character.ability.AbilityRuntime.Cast;
import com.myriad.combat.DamageCalc;
import java.util.HashMap;
import java.util.UUID;

final class FeatherBurst extends LuoxiaSkill {
    FeatherBurst(Definition definition) { super(definition); }
    static float hitMultiplier(int number) { return number == 3 ? .5f : 1; }

    @Override public void release(Cast c) {
        var contacts = new HashMap<UUID, Integer>();
        LuoxiaBow.volley(c, 3, 5, 0, shot -> {
            LuoxiaVfx.shot(c, false, 1.20f + .09f * shot);
            LuoxiaArrow.launch(c, c.player.getLookAngle(), 19, 1.65, LuoxiaArrow.Shape.BURST, false, target -> {
                int count = contacts.merge(target.getUUID(), 1, Integer::sum);
                c.damageRapid(target, damageAtRank(c, 32, 55, .24f) * hitMultiplier(count), DamageCalc.Kind.PHYSICAL);
                return true;
            });
        });
    }
}
