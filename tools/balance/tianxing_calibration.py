"""Tianxing's effective-basic marks, derived hits and fixed lightning zones."""

from combo_calibration import effect


THIRD = "g1_tianxing_pa"
RESONANCE = "g1_tianxing_pb"
BLINK = "g1_tianxing_n1"
ARC = "g1_tianxing_n2"
GUARD = "g1_tianxing_ma"
NINE = "g1_tianxing_ua"
JUDGMENT = "g1_tianxing_ub"


class TianxingReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.thunder_target = None
        self.thunder_mark_tick = 0
        self.thunder_marks = 0
        self.resonance_expires = self.resonance_recovers = 0
        self.guard_expires = 0
        self.lightning_point = None
        # Independent scripts declare the exact number of co-linear secondary targets.
        if self.initial.get("splashTargets"):
            for offset in self.initial["splashTargets"]:
                self.enemies.append(type(self.enemies[0])(
                    self.target_stats, self.target_stats["health"], self.initial["distance"] + offset))

    def get_thunder(self, skill, field):
        return effect(self.skills[skill], field)

    def thunder_damage(self, enemy, skill):
        self.formula_hit(enemy, skill, "segment.primary", rapid=True, indirect=True)
        radius = self.get_thunder(skill, "splash_radius")
        limit = self.get_thunder(skill, "splash_limit")
        secondary = [target for target in self.enemies if target is not enemy and target.hp > 0
                     and abs(target.position - enemy.position) <= radius]
        for target in secondary[:limit]:
            self.formula_hit(target, skill, "segment.splash", rapid=True, indirect=True)

    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if passive == THIRD and skill == "basic":
            if (enemy is not self.thunder_target
                    or self.tick - self.thunder_mark_tick > self.get_thunder(THIRD, "hit_window")):
                self.thunder_marks = 0
            self.thunder_target, self.thunder_mark_tick = enemy, self.tick
            self.thunder_marks += 1
            triggered = self.thunder_marks == self.get_thunder(THIRD, "hit_count")
            if triggered:
                self.thunder_marks = 0
            self.record("thunder_mark", skill=THIRD, count=self.thunder_marks, triggered=triggered,
                        target=self.enemies.index(enemy))
            if triggered:
                self.thunder_damage(enemy, THIRD)
        if passive == RESONANCE:
            if skill != "basic" and self.tick >= self.resonance_recovers:
                self.resonance_expires = self.tick + self.get_thunder(RESONANCE, "ready_window")
                self.resonance_recovers = self.tick + self.get_thunder(RESONANCE, "internal_cooldown")
                self.record("resonance_ready", skill=RESONANCE, endTick=self.resonance_expires)
            elif skill == "basic" and self.tick < self.resonance_expires:
                self.resonance_expires = 0
                self.thunder_damage(enemy, RESONANCE)

    def release(self, skill, step):
        if skill == BLINK:
            self.position = self.enemies[0].position
            self.record("position", skill=BLINK, distanceBlocks=self.distance())
        elif skill == GUARD:
            self.guard_expires = self.tick + self.get_thunder(GUARD, "guard_duration")
        elif skill == NINE:
            self.lightning_point = self.enemies[0].position
        super().release(skill, step)

    def incoming(self, target, kind, raw):
        if target is self.caster and kind != "true" and self.tick < self.guard_expires:
            adjusted = raw * self.get_thunder(GUARD, "damage_taken_rate")
            self.record("damage_reduced", skill=GUARD, damageKind=kind, rawHp=raw, rawHpAfterGuard=adjusted)
            super().incoming(target, kind, adjusted)
            return
        super().incoming(target, kind, raw)

    def apply(self, skill, row, token, elapsed, label):
        if skill == NINE:
            if row["kind"] == "strike_schedule":
                warning = self.get_thunder(NINE, "warning")
                interval = self.get_thunder(NINE, "strike_interval")
                strikes = self.get_thunder(NINE, "strikes")
                for strike in range(strikes):
                    due = warning + strike * interval
                    self.later(due, self.apply, skill, {"kind": "damage", "effect": "segment.pulse"}, token, 0, label)
                self.later(due, self.apply, skill, {"kind": "control"}, token, 0, label)
                return
            radius = self.get_thunder(NINE, "radius")
            for index, enemy in enumerate(self.enemies):
                if enemy.hp > 0 and abs(enemy.position - self.lightning_point) <= radius:
                    if row["kind"] == "control":
                        self.control(enemy, "root", self.get_thunder(NINE, "root_duration"), NINE)
                    else:
                        self.impact(skill, row, enemy, index, token, label)
            return
        super().apply(skill, row, token, elapsed, label)

    def impact(self, skill, row, enemy, index, token, label):
        if skill == GUARD:
            # c.pulse uses the derived damage path and cannot prime Resonance.
            self.formula_hit(enemy, skill, row["effect"], rapid=True, indirect=True)
            return
        first_contact = skill == ARC and enemy.hp > 0
        super().impact(skill, row, enemy, index, token, label)
        if first_contact:
            for other in self.enemies:
                if (other is not enemy and other.hp > 0
                        and abs(other.position - enemy.position) <= self.get_thunder(ARC, "chain_radius")):
                    self.formula_hit(other, ARC, "segment.chain", rapid=True, indirect=True)
