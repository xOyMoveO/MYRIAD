package com.myriad.game;

import com.myriad.character.SkillSystem;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.skills.luoxia.LuoxiaBow;
import com.myriad.combat.BasicAttackSystem;
import com.myriad.player.PlayerDataProvider;
import com.myriad.character.rework.FirstGroupSkills;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Time one opening E and repeated normal arrows against two full melee and two full ranged minions. */
final class CherryWaveDamageChecks {
    private final ServerPlayer farmer;
    private final int node;
    private final List<Mob> wave = new ArrayList<>();
    private int arrows, finish;
    private float healthAfterOpening;

    @SuppressWarnings({"rawtypes","unchecked"})
    CherryWaveDamageChecks(GameTestHelper h, ServerPlayer farmer, int node) throws ReflectiveOperationException {
        this.farmer = farmer;
        this.node = node;
        var level = h.getLevel();
        Class kind = Class.forName("com.myriad.game.LaneSystem$Kind");
        var spawn = LaneSystem.class.getDeclaredMethod("spawnMinion", ServerLevel.class, Faction.class,
                LaneSystem.Lane.class, List.class, kind, double.class);
        spawn.setAccessible(true);
        var states = LaneSystem.class.getDeclaredField("UNITS");
        states.setAccessible(true);
        var units = (Map<UUID, ?>)states.get(null);
        for (int i = 0; i < 4; i++) {
            var existing = Set.copyOf(units.keySet());
            var pos = BlockPos.containing(farmer.position().add(i*2, 0, 7));
            spawn.invoke(null, level, Faction.DESTRUCTION, LaneSystem.Lane.MID,
                    List.of(pos, pos.south(20)), Enum.valueOf(kind,i<2?"MELEE":"RANGED"), 0d);
            var state = units.entrySet().stream().filter(e -> !existing.contains(e.getKey()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Managed minion not created at " + pos)).getValue();
            var entity = state.getClass().getDeclaredField("entity");
            entity.setAccessible(true);
            var mob = (Mob) entity.get(state);
            mob.setPos(Vec3.atBottomCenterOf(pos));
            mob.setNoGravity(true);
            wave.add(mob);
            h.assertTrue(LaneSystem.health(mob) == (i<2 ? 665 : 455), "First-wave type and initial health");
        }
    }

    void advance(int tick) {
        if (finish != 0) return;
        var target = wave.stream().filter(m -> m.isAlive() && LaneSystem.health(m)>0).findFirst().orElse(null);
        if (target == null) {
            finish = tick;
            return;
        }
        Vec3 delta = target.getBoundingBox().getCenter().subtract(farmer.getEyePosition());
        farmer.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
        farmer.setXRot((float)Math.toDegrees(Math.atan2(-delta.y,delta.horizontalDistance())));
        if (tick == 1) SkillSystem.cast(farmer, FirstGroupSkills.node("luoxia", node).slot());
        int resume = node == 6 ? 55 : 22;
        if (tick == resume-2) healthAfterOpening = LaneSystem.health(wave.getFirst());
        if (tick >= resume && (tick-resume)%17==0) BasicAttackSystem.tryAttack(farmer, LuoxiaBow.PRESS);
        if (tick >= resume+13 && (tick-resume-13)%17==0) {
            BasicAttackSystem.tryAttack(farmer, LuoxiaBow.RELEASE);
            arrows++;
        }
        AbilityRuntime.tick(farmer);
    }

    void verify(GameTestHelper h) {
        System.out.printf(Locale.ROOT,"[CHERRY-WAVE] E%d openingLoss=%.3f arrows=%d endTick=%d%n",
                node, 665-healthAfterOpening, arrows, finish);
        h.assertTrue(PlayerDataProvider.get(farmer).getCooldown(FirstGroupSkills.node("luoxia", node).getId()) > 0
                        || PlayerDataProvider.get(farmer).getMana() < 700,
                "Opening E charged its cost");
        h.assertTrue(healthAfterOpening > 0 && healthAfterOpening < 665 && finish > 0,
                "A registered opening E damaged a full melee minion and ordinary arrows cleared four real soldiers");
    }

    void close() {
        for (Mob m : wave) {
            if (LaneSystem.isManaged(m)) LaneSystem.onDeath(m, null, true);
            m.discard();
        }
    }
}
