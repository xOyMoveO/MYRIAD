"""Fenjue's direct casts, one refreshable burn per target, and field eligibility."""

from math import floor

from combo_calibration import amount, effect
from sulan_calibration import dash_hits

EMBER = "g1_fenjue_pa"
LAST_STAND = "g1_fenjue_pb"
CAGE = "g1_fenjue_ma"
BURST = "g1_fenjue_mb"
SEA = "g1_fenjue_ua"


class FenjueReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.burn = {}
        self.stand_ready = 0
        self.stand_cast = None
        self.centers = {}

    def release(self, skill, step):
        if skill in (CAGE, SEA):
            self.centers[skill, self.tick] = step.get("pointBlocks", self.enemies[0].position)
        super().release(skill, step)

    def damage(self, enemy, raw, kind, skill, label=None, rapid=False, indirect=False, token=None):
        if not indirect and kind != "true" and skill != "basic":
            if skill == BURST:
                lost = 1 - self.caster.hp / self.stats["health"]
                raw *= 1 + min(self.value(skill, "max_damage_bonus"),
                               floor(lost * self.value(skill, "health_steps"))
                               * self.value(skill, "damage_bonus_per_step"))
            if self.selected_passive() == LAST_STAND:
                if (self.tick >= self.stand_ready
                        and self.caster.hp <= self.stats["health"] * self.value(LAST_STAND, "hp_threshold")):
                    self.stand_cast = token
                    self.stand_ready = self.tick + self.value(LAST_STAND, "internal_cooldown")
                    self.record("last_stand", skill=LAST_STAND, source=skill)
                if token is not None and token == self.stand_cast:
                    raw *= self.value(LAST_STAND, "damage_multiplier")
        return super().damage(enemy, raw, kind, skill, label, rapid, indirect, token)

    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if passive != EMBER or skill == "basic":
            return
        deadline = self.tick + self.value(EMBER, "burn_duration")
        existing = self.burn.get(enemy)
        if existing is not None and existing[0] > self.tick:
            existing[0] = deadline
            self.record("burn_refreshed", skill=EMBER, target=self.enemies.index(enemy), untilTick=deadline)
            return
        formula = effect(self.skills[EMBER], "segment.burn")
        state = [deadline, amount(formula, self.ranks[EMBER], self.stats)]
        self.burn[enemy] = state
        self.record("burn_started", skill=EMBER, target=self.enemies.index(enemy), untilTick=deadline)
        self.later(self.value(EMBER, "pulse_interval"), self.burn_pulse, enemy, state)

    def burn_pulse(self, enemy, state):
        if self.burn.get(enemy) is not state or enemy.hp <= 0 or self.tick > state[0]:
            return
        self.damage(enemy, state[1], "true", EMBER, "burn", rapid=True, indirect=True)
        self.later(self.value(EMBER, "pulse_interval"), self.burn_pulse, enemy, state)

    def apply(self, skill, row, token, elapsed, label):
        kind = row["kind"]
        if kind == "dash_hit":
            dash_hits(self, skill, row, token, label)
        elif kind == "fire_sea":
            center = self.centers.pop(token)
            self.position = center
            self.record("position", skill=skill, distanceBlocks=self.distance())
            radius = self.value(skill, "impact_radius")
            for enemy in self.enemies:
                if abs(enemy.position - center) <= radius:
                    self.formula_hit(enemy, skill, "segment.landing", token=token, label=label or "landing")
            self.start_field(skill, center, radius)
        elif kind == "lava_field":
            self.start_field(skill, self.centers.pop(token), self.value(skill, "radius"))
        elif kind == "armor_pulse":
            value, expires = self.caster.shields.get(skill, (0, 0))
            if value > 0 and self.tick < expires:
                self.pulse(skill, "segment.shield_pulse", self.position, self.value(skill, "radius"))
        else:
            super().apply(skill, row, token, elapsed, label)

    def start_field(self, skill, center, radius):
        interval = self.value(skill, "pulse_interval")
        duration = self.value(skill, "field_duration")
        for tick in range(interval, duration + 1, interval):
            self.later(tick, self.pulse, skill, "segment.pulse", center, radius)
        if skill == CAGE:
            for tick in range(0, duration + 1, self.value(skill, "refresh_interval")):
                self.later(tick, self.cage_slow, skill, center, radius)

    def pulse(self, skill, effect_id, center, radius):
        for enemy in self.enemies:
            if abs(enemy.position - center) <= radius:
                self.formula_hit(enemy, skill, effect_id, rapid=True, indirect=True, label="pulse")

    def cage_slow(self, skill, center, radius):
        for enemy in self.enemies:
            if abs(enemy.position - center) <= radius:
                self.control(enemy, "movement_slow", self.value(skill, "slow_duration"), skill,
                             1 - self.value(skill, "slow_rate"))
