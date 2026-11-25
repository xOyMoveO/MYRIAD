package com.myriad.client;

import static com.myriad.client.CombatHudOverlay.label;
import static com.myriad.client.CombatHudOverlay.secondsWithUnit;

import com.myriad.character.rework.FirstGroupSkills;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class CombatHudRoleStatus {
    private static final int CYAN = 0xFF9FDFE2;
    private static final int AMBER = 0xFFF2C488;

    record Row(Component text, int color, float progress) {
        Row(Component text, int color) { this(text, color, -1F); }
    }

    static List<Row> rows(CombatHudState.Snapshot state) {
        var data = ClientDataHolder.get();
        var passive = state.passive();
        String id = state.character().getId();
        return switch (id) {
            case "lingyuan" -> data.skillLevels().getOrDefault("g2_lingyuan_pa", 0) > 0
                    ? List.of(new Row(label("lingyuan", data.flyingSwords(), data.swordQi()),
                            CYAN, data.swordQi() / 100F)) : List.of();
            case "rivet" -> anchor(state.preparing());
            case "keheng" -> {
                var buffs = ClientDataHolder.buffs();
                int marks = ClientDataHolder.syncedCounter("g1_keheng_marks");
                int nextStep = buffs.getOrDefault("g1_keheng_step_cd", 0);
                var display = buffs.getOrDefault("g1_keheng_follow", 0) > 0
                        ? label("keheng_follow", secondsWithUnit(buffs.getOrDefault("g1_keheng_follow", 0)))
                        : data.skillLevels().getOrDefault("keheng_hengbu", 0) > 0
                          ? label("keheng_step", nextStep > 0 ? secondsWithUnit(nextStep) : label("ready"))
                          : label("keheng_marks", marks,
                                  secondsWithUnit(buffs.getOrDefault("g1_keheng_mark_window", 0)));
                yield List.of(new Row(display, CYAN));
            }
            case "shuo" -> shuo(passive);
            case "yike" -> yike(state);
            case "chixiao" -> {
                int stacks = Math.clamp(ClientDataHolder.syncedCounter("chixiao_passive"), 0, 3);
                int recovery = ClientDataHolder.buffTicks("chixiao_passive_cd");
                var text = label("chixiao_stacks", stacks);
                if (recovery > 0) text.append(" · ").append(secondsWithUnit(recovery));
                yield List.of(new Row(text, AMBER, stacks / 3F));
            }
            case "sulan" -> {
                boolean ready = ClientDataHolder.syncedCounter("sulan_passive_ready") > 0;
                yield List.of(new Row(Component.translatable(ready
                        ? "hud.myriad.wind_ready" : "hud.myriad.wind_charging"), ready ? CYAN : 0xFFC0C9CD));
            }
            default -> passive != null && FirstGroupSkills.SECOND_GROUP.contains(id)
                    && ClientDataHolder.buffTicks(passive.skill().getId() + "_ready") > 0
                    ? List.of(new Row(Component.translatable("skill.myriad." + passive.skill().getId())
                              .append(" · ").append(label("ready")), AMBER)) : List.of();
        };
    }

    private static List<Row> anchor(boolean preparing) {
        if (ClientDataHolder.syncedCounter("g1_rivet_anchor_active") <= 0) {
            return List.of(new Row(Component.translatable("hud.myriad.rivet.no_anchor"), CYAN));
        }
        var buffs = ClientDataHolder.buffs();
        int recovery = buffs.getOrDefault("g1_rivet_anchor_recovery", 0);
        var text = label("anchor_lifetime", secondsWithUnit(buffs.getOrDefault("g1_rivet_anchor_remaining", 0)));
        var strike = preparing ? label("anchor_disabled")
                : recovery > 0 ? label("anchor_recovery", secondsWithUnit(recovery)) : label("anchor_ready");
        return List.of(new Row(text.append(" · ").append(strike), preparing || recovery > 0 ? AMBER : CYAN));
    }

    private static List<Row> shuo(CombatHudState.Slot passive) {
        var buffs = ClientDataHolder.buffs();
        boolean drawn = ClientDataHolder.syncedCounter("g1_shuo_drawn") > 0;
        var stance = buffs.getOrDefault("g1_shuo_sheathing", 0) > 0 ? label("shuo_sheathing")
                : ClientDataHolder.isChanneling() ? label("shuo_charging")
                : drawn ? label("shuo_drawn", Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage())
                : label("shuo_sheathed");
        int gauge = Math.clamp(ClientDataHolder.syncedCounter("g1_shuo_gauge"), 0, 100);
        if (passive != null && passive.rank() > 0 && passive.skill() instanceof
                com.myriad.character.ability.AbilitySkill ability) {
            if (ability.index() == 0) {
                return List.of(new Row(stance, CYAN), new Row(label("shuo_edge", gauge), CYAN, gauge / 100F));
            }
            if (ClientDataHolder.syncedCounter("g1_shuo_ready") > 0) {
                return List.of(new Row(stance, CYAN), new Row(label("shuo_counter"), AMBER));
            }
        }
        return List.of(new Row(stance, CYAN));
    }

    private static List<Row> yike(CombatHudState.Snapshot state) {
        var player = Minecraft.getInstance().player;
        var yike = player == null ? null : YikeClientState.get(player.getId());
        if (yike == null) return List.of();
        var rows = new ArrayList<Row>(3);
        var mode = label(switch (yike.mode()) {
            case 1 -> "yike_fixed";
            case 2 -> "yike_mobile";
            case 3 -> "yike_beam";
            default -> "yike_travel";
        });
        if (yike.mode() > 0) mode.append(" · ").append(secondsWithUnit(yike.remaining()));
        if (yike.windup() >= 0) mode.append(" · ").append(label("yike_windup"));
        rows.add(new Row(mode, CYAN));
        if (yike.mode() == 3) {
            rows.add(new Row(label("yike_beam_jumps", yike.jumps()), CYAN, yike.jumps() / 6F));
        } else if (yike.mode() > 0) {
            int total = yike.mode() == 1 ? 5 : 4;
            var shells = label("yike_shells", yike.shells(), total);
            if (yike.nextShot() > 0) shells.append(" · ").append(secondsWithUnit(yike.nextShot()));
            else if (state.activeSlots().getFirst().status() == CombatHudState.SkillStatus.NO_MANA) {
                shells.append(" · ").append(label("no_mana"));
            }
            rows.add(new Row(shells, CYAN, yike.shells() / (float)total));
        }
        if (state.passive() != null && state.passive().rank() > 0
                && state.passive().skill().getId().equals("yike_steady_barrier")) {
            var barrier = yike.barrierCooldown() > 0
                    ? label("yike_barrier", secondsWithUnit(yike.barrierCooldown()))
                    : yike.steady() ? label("yike_barrier", label("ready")) : label("yike_brace");
            rows.add(new Row(barrier, AMBER));
        }
        return rows;
    }

    private CombatHudRoleStatus() {}
}
