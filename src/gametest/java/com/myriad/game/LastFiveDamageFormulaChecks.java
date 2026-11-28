package com.myriad.game;

import com.myriad.character.CharacterType;
import com.myriad.character.ability.AbilityRuntime;
import com.myriad.character.rework.FirstGroupGrowth;
import com.myriad.character.rework.FirstGroupSkills;
import com.myriad.character.skills.shuo.ShuoCombat;
import com.myriad.combat.DamageCalc.Kind;
import com.myriad.network.ShuoViewPayload;
import com.myriad.player.PlayerDataProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Literal zero-purchase endpoints and one-point deltas for the live registered damage definitions. */
final class LastFiveDamageFormulaChecks {
    private record Segment(double first, double last, double strength, double wisdom, int maxHits) {}
    private record Candidate(String id, int maxRank, List<Segment> segments) {}
    private record DrawFixture(ServerPlayer player, int edge, double expected) {}

    private LastFiveDamageFormulaChecks() {}

    private static Segment physical(double first, double last, double strength) {
        return new Segment(first, last, strength, 0, 1);
    }

    private static Segment magic(double first, double last, double wisdom, int maxHits) {
        return new Segment(first, last, 0, wisdom, maxHits);
    }

    private static Candidate skill(String id, int maxRank, Segment... segments) {
        return new Candidate(id, maxRank, List.of(segments));
    }

    static void run(GameTestHelper helper) {
        verifyRole(helper, CharacterType.SHUO, 65,
                skill("shuo_cangfeng", 5, physical(52, 109.6, 6)),
                skill("shuo_jianqie", 5, physical(70, 142, 7.5)),
                skill("shuo_nadao", 5, physical(40.2, 82.2, 4.5), physical(26.8, 54.8, 3)),
                skill("shuo_qiaofan", 5, physical(78.5, 168.5, 7.5), physical(62, 137, 7.5)),
                skill("shuo_yishan", 7, physical(81.5, 203, 7.5), physical(71.4, 161.4, 4.5), physical(47.6, 107.6, 3)),
                skill("shuo_duanliu", 7, new Segment(55.5, 130.5, 3.75, 0, 2),
                        physical(81.3, 180.3, 4.5), physical(54.2, 120.2, 3)),
                skill("shuo_wusheng", 3, physical(276, 460, 12), physical(368, 618, 12)),
                skill("shuo_canyue", 3, physical(320.5, 545.5, 12),
                        new Segment(118, 210, 3.428571, 0, 2), physical(187, 325, 5.142857)));
        verifyRole(helper, CharacterType.KEHENG, 65,
                skill("keheng_kehen", 5, magic(57.5, 115.1, 5, 1)),
                skill("keheng_hengbu", 5),
                skill("keheng_chizhen", 5, magic(95, 192.2, 10, 1)),
                skill("keheng_cuopaibu", 5),
                skill("keheng_chijie", 7, magic(93.5, 228.5, 9, 1)),
                skill("keheng_suixing", 7, magic(105, 240, 10, 1)),
                skill("keheng_yimiao", 3, magic(27.6, 46, 2.4, 6)),
                skill("keheng_zhongke", 3, magic(370.5, 620.5, 27, 1)));
        verifyRole(helper, CharacterType.YIKE, 65,
                skill("yike_rainbow_mark", 5, magic(46, 94, 4, 1)),
                skill("yike_steady_barrier", 5),
                skill("yike_rainbow_shell", 5, magic(111.5, 201.5, 11, 1)),
                skill("yike_refraction_denial", 5, magic(80.5, 170.5, 7, 1)),
                skill("yike_fixed_artillery", 7),
                skill("yike_mobile_array", 7),
                skill("yike_distant_bombardment", 3, magic(284.5, 497, 23, 1)),
                skill("yike_prismatic_beam", 3, magic(77.5, 123.5, 5, 6)));
        verifyRole(helper, CharacterType.RIVET, 65,
                skill("rivet_station_armor", 5),
                skill("rivet_breach_rhythm", 5, physical(30, 68.4, 3.75)),
                skill("rivet_impact_hammer", 5, physical(90, 190, 5.357143), physical(35, 75, 2.142857)),
                skill("rivet_tactical_anchor", 5),
                skill("rivet_emergency_strut", 7),
                skill("rivet_tow_cable", 7),
                skill("rivet_hold_protocol", 3),
                skill("rivet_breach_charge", 3, physical(170, 296.5, 9), physical(45, 79.5, 3)));
        verifyRole(helper, CharacterType.LUOXIA, 100,
                skill("luoxia_chase_dusk", 5, physical(90, 90, 13.5)),
                skill("luoxia_steady_dusk", 5, physical(121, 145, 18.15)),
                skill("luoxia_dusk_mark", 5, physical(52, 87, 4.166667), physical(25, 50, 3.333333)),
                skill("luoxia_slant_step", 5),
                skill("luoxia_feather_burst", 7, new Segment(50, 56.25, 3, 0, 2), physical(25, 28.125, 1.5)),
                skill("luoxia_last_sun", 7, physical(150, 190.5, 7.5)),
                skill("luoxia_thousand_feathers", 3, new Segment(70, 81.5, 4, 0, 3)),
                skill("luoxia_sunset_line", 3, physical(175, 281.25, 12), physical(121.153846, 194.711539, 8.307692),
                        physical(84.134615, 135.216346, 5.769231), physical(40, 65, 2.285714)));
        System.out.println("[MYRIAD-DAMAGE-LAST-FIVE] PASS: 40 candidates, 45 damage formulas, all ranks, zero and one-point purchases, and ten-point deltas");
    }

    private static void verifyRole(GameTestHelper h, CharacterType role, int attack, Candidate... expected) {
        near(h, FirstGroupGrowth.baselinePhysicalAttack(role), attack, role.getId() + " baseline physical attack");
        h.assertTrue(FirstGroupSkills.candidates(role.getId()).size() == expected.length,
                role.getId() + " full candidate coverage");
        for (int node = 0; node < expected.length; node++) {
            Candidate candidate = expected[node];
            var skill = FirstGroupSkills.node(role.getId(), node);
            h.assertTrue(skill.getId().equals(candidate.id()) && skill.maxRank() == candidate.maxRank(),
                    candidate.id() + " candidate index and max rank");
            var formulas = skill.damageFormulas();
            h.assertTrue(formulas.size() == candidate.segments().size(), candidate.id() + " registered segment count");
            for (int i = 0; i < formulas.size(); i++) {
                var formula = formulas.get(i);
                Segment segment = candidate.segments().get(i);
                Kind expectedKind = segment.wisdom() > 0 ? Kind.MAGIC : Kind.PHYSICAL;
                h.assertTrue(formula.kind() == expectedKind && formula.maxHits() == segment.maxHits(),
                        candidate.id() + " damage type and per-target hit count");
                for (int rank = 1; rank <= candidate.maxRank(); rank++) {
                    String detail = candidate.id() + " segment " + i + " rank " + rank;
                    double baseline = segment.first() + (segment.last() - segment.first()) * (rank - 1)
                            / (candidate.maxRank() - 1);
                    double strength = segment.strength();
                    if (candidate.id().equals("luoxia_steady_dusk")) strength += .9 * (rank - 1);
                    near(h, formula.value(rank, attack, 30, 0, 0), baseline, detail + " zero purchases");
                    near(h, formula.value(rank, attack + 15, 30, 15, 0), baseline + strength, detail + " first strength");
                    near(h, formula.value(rank, attack, 50, 0, 1), baseline + segment.wisdom(), detail + " first wisdom");
                    double high = formula.value(rank, attack + 150, 230, 150, 10);
                    near(h, high, baseline + 10 * (strength + segment.wisdom()), detail + " ten of each");
                    near(h, high - formula.value(rank, attack + 135, 230, 135, 10), strength, detail + " remove one strength");
                    near(h, high - formula.value(rank, attack + 150, 210, 150, 9), segment.wisdom(), detail + " remove one wisdom");
                    double growthAttack = RoleStatGrowthChecks.attackGrowth(role, 20);
                    double growthSpell = RoleStatGrowthChecks.spellGrowth(role, 20);
                    double grown = baseline + growthAttack / 15 * strength + growthSpell / 20 * segment.wisdom();
                    near(h, formula.value(rank, attack + growthAttack, 30 + growthSpell, growthAttack, 0),
                            grown, detail + " level twenty zero purchases");
                    near(h, formula.value(rank, attack + growthAttack + 15, 50 + growthSpell, growthAttack + 15, 1),
                            grown + strength + segment.wisdom(), detail + " level twenty with one strength and wisdom");
                    near(h, formula.perPoint(rank), strength + segment.wisdom(), detail + " preview increment");
                }
            }
        }
    }

    static void checkShuoDraws(GameTestHelper h) {
        var fixtures = new ArrayList<DrawFixture>();
        double growthAttack = RoleStatGrowthChecks.attackGrowth(CharacterType.SHUO, 20);
        for (int purchase = 0; purchase < 3; purchase++) {
            double bonusAttack = growthAttack + (purchase == 1 ? 15 : 0);
            for (int ticks : new int[]{25, 50}) {
                Vec3 position = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(50, 177 + purchase * 6, ticks == 25 ? 10 : 15)));
                ServerPlayer p = shuo(h, position, purchase, "shuo_cangfeng");
                double full = 52 + .4 * bonusAttack;
                fixtures.add(new DrawFixture(p, ticks * 2, ticks == 25 ? full / 2 : full));
            }
            Vec3 position = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(50, 177 + purchase * 6, 5)));
            ServerPlayer p = shuo(h, position, purchase, "shuo_jianqie");
            var enemy = ShuoChecks.target(h, position.add(0, 0, 2));
            try {
                ShuoCombat.guard(AbilityRuntime.context(p, FirstGroupSkills.node("shuo", 3)));
                h.assertTrue(ShuoCombat.parry(p, p.damageSources().mobAttack(enemy), Kind.PHYSICAL),
                        "Jianqie's active guard must absorb a frontal hit");
                near(h, ShuoCombat.draw(p, ShuoViewPayload.DRAW, 9), 70 + .5 * bonusAttack, "Level-twenty guarded Shuo draw");
                near(h, ShuoCombat.draw(p, ShuoViewPayload.DRAW, 9), 0, "Guarded draw is consumed once");
            } finally {
                AbilityRuntime.clear(p);
                p.discard();
                enemy.discard();
            }
        }
        // The world clock can already have processed a mock player's native tick when this
        // callback runs. Draw at the observed charge threshold, regardless of callback order.
        for (int tick = 1; tick <= 60; tick++) {
            int age = tick;
            h.runAfterDelay(tick, () -> {
                try {
                    var ready = fixtures.iterator();
                    while (ready.hasNext()) {
                        DrawFixture fixture = ready.next();
                        ServerPlayer p = fixture.player();
                        ShuoCombat.chargeEdge(p);
                        int edge = ShuoCombat.gauge(p);
                        if (edge < fixture.edge()) continue;
                        h.assertTrue(edge == fixture.edge(), "Shuo draw must use the exact edge threshold, got " + edge);
                        near(h, ShuoCombat.draw(p, ShuoViewPayload.DRAW, 9), fixture.expected(), "Charged Shuo draw at " + edge);
                        near(h, ShuoCombat.draw(p, ShuoViewPayload.DRAW, 9), 0, "Charged draw is consumed once");
                        clearShuo(p);
                        ready.remove();
                    }
                    h.assertTrue(age < 60 || fixtures.isEmpty(), "All fixtures must reach their half or full charge within 60 ticks");
                } catch (Throwable failure) {
                    fixtures.forEach(fixture -> clearShuo(fixture.player()));
                    fixtures.clear();
                    throw failure;
                }
            });
        }
    }

    private static void clearShuo(ServerPlayer p) {
        AbilityRuntime.clear(p);
        p.discard();
    }

    private static ServerPlayer shuo(GameTestHelper h, Vec3 position, int purchase, String passive) {
        ServerPlayer player = ShuoChecks.player(h, position, new ArrayList<>());
        var data = PlayerDataProvider.get(player);
        data.setFirstGroupAttribute("strength", purchase == 1 ? 1 : 0);
        data.setFirstGroupAttribute("wisdom", purchase == 2 ? 1 : 0);
        FirstGroupGrowth.initialize(player, data);
        data.setSkillLevel(passive, 1);
        return player;
    }

    private static void near(GameTestHelper h, double actual, double expected, String detail) {
        h.assertTrue(Math.abs(actual - expected) < .005, detail + " expected " + expected + " got " + actual);
    }
}
