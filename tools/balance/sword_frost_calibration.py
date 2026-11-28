"""Source-driven geometry, passive triggers and target ledgers for Lingyuan and Hanze."""

from collections import defaultdict
from math import ceil

from combo_calibration import amount, effect


class SwordFrostReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.sword_qi = 0
        self.blades = self.initial.get("preloadedBlades", 0)
        self.last_sword_hit = 0 if self.blades else None
        self.next_sword_volley = 0
        self.resonance = 0
        self.frost = defaultdict(lambda: {"count": 0, "last": -100, "until": -1,
                                         "immunity": 0, "seen": set()})
        self.rime_ready = 0
        self.cast_points = {}
        self.end = (self.script or {}).get("observationTicks", self.end)

    def run(self):
        for tick, index, position in self.initial.get("forcedEnemyPositions", []):
            self.later(tick, self.place_enemy, index, position)
        for tick in self.initial.get("interruptActorAt", []):
            self.later(tick, self.interrupt_actor)
        return super().run()

    def place_enemy(self, index, position):
        self.enemies[index].position = position
        self.record("fixture_enemy_position", target=index, positionBlocks=position)

    def interrupt_actor(self):
        if self.channel is not None:
            skill, _, _, paid = self.channel
            self.channel = None
            self.record("cancelled", skill=skill, channel="windup", paid=paid)

    def value(self, skill, source):
        if source == "third":
            return 2 * super().value(skill, "shot_gap")
        return super().value(skill, source)

    def execute(self, step):
        if self.tick < step.get("earliestTick", 0):
            return False
        return super().execute(step)

    def commit(self, skill, step):
        self.cast_points[skill] = self.position, self.enemies[0].position
        super().commit(skill, step)
        passive = self.selected_passive()
        if passive == "g2_lingyuan_pb":
            limit = self.value(passive, "casts_to_charge")
            self.resonance = min(limit, self.resonance + 1)
            self.record("resonance_charge", skill=passive, source=skill, casts=self.resonance)

    def release(self, skill, step):
        if skill == "g2_lingyuan_ub" and not step.get("chop_phase"):
            self.record("chop_phase", skill=skill, actionTicks=self.value(skill, "chop"))
            self.start_windup(skill, {**step, "chop_phase": True}, "chop", paid=True)
            return
        super().release(skill, step)

    def begin(self, skill, step):
        if skill == "g2_lingyuan_ub":
            end = self.value(skill, "charge") + self.value(skill, "chop")
            self.record("chop_charge", skill=skill, impactAfterActionTicks=end)
        super().begin(skill, step)

    def apply(self, skill, row, token, elapsed, label):
        geometry = row.get("geometry")
        if geometry is None:
            return super().apply(skill, row, token, elapsed, label)
        origin, aim = self.cast_points[skill]
        if geometry == "dash":
            direction = 1 if aim >= origin else -1
            duration = self.value(skill, "dash_duration")
            hit = set()
            for n in range(1, duration + 1):
                delta = direction * self.value(skill, "dash_range") / duration
                self.after_action(n, self.dash_step, skill, row, token, label, delta, hit)
            return
        if geometry == "projectile":
            direction = 1 if self.enemies[0].position >= self.position else -1
            targets = []
            for index, enemy in enumerate(self.enemies):
                depth = (enemy.position - self.position) * direction
                if self.is_present(index, elapsed) and 0 <= depth <= self.value(skill, "projectile_range"):
                    targets.append((depth, index, enemy))
            for depth, index, enemy in sorted(targets)[:None if row.get("pierces") else 1]:
                # Unobstructed, collinear target. The initial muzzle, entity padding and
                # fixed synthetic half-width allow contact before crossing the target center.
                allowance = .4 + self.value(skill, "projectile_padding") + self.initial["targetCollisionDistanceAllowance"]
                flight = max(1, ceil(max(0, depth - allowance) / self.value(skill, "projectile_speed")))
                self.later(flight, self.settle, skill, row, enemy, token, label)
            return
        for index, enemy in enumerate(self.enemies):
            if not self.is_present(index, elapsed):
                continue
            if geometry == "chop":
                depth = (enemy.position - origin) * (1 if aim >= origin else -1)
                legal = 0 <= depth <= self.value(skill, "chop_range")
            elif geometry == "point_area":
                legal = abs(enemy.position - aim) <= self.value(skill, "radius")
            elif geometry == "caster_area":
                legal = abs(enemy.position - self.position) <= self.value(skill, row["radius"])
            else:
                raise ValueError(f"Unknown sword / frost geometry {geometry}")
            if legal:
                self.settle(skill, row, enemy, token, label)

    def is_present(self, index, elapsed):
        return (not self.probe or self.probe.get("beneficiaries") == "allies"
                or elapsed <= self.probe["fieldStayTicks"][index])

    def dash_step(self, skill, row, token, label, delta, hit):
        previous = self.position
        self.position += delta
        for index, enemy in enumerate(self.enemies):
            if index in hit or not self.is_present(index, 0):
                continue
            closest = max(min(previous, self.position), min(max(previous, self.position), enemy.position))
            if abs(closest - enemy.position) <= self.value(skill, "dash_radius"):
                hit.add(index)
                self.settle(skill, row, enemy, token, label)

    def settle(self, skill, row, enemy, token, label):
        if row.get("effect"):
            self.formula_hit(enemy, skill, row["effect"], label=label, token=token,
                             indirect=row.get("indirect", False), rapid=row.get("indirect", False))
        if "root_duration" in row:
            self.control(enemy, "root", self.value(skill, row["root_duration"]), skill)
        if "slow_duration" in row:
            self.control(enemy, "movement_slow", self.value(skill, row["slow_duration"]), skill,
                         self.value(skill, "movement_rate"))

    def act(self):
        passive = self.selected_passive()
        if (passive == "g2_lingyuan_pa" and self.blades and self.last_sword_hit is not None
                and self.tick >= self.next_sword_volley):
            enemy = self.enemies[self.last_sword_hit]
            reach = self.value(passive, "sword_range")
            if enemy.hp > 0 and self.distance(enemy) <= reach:
                self.next_sword_volley = self.tick + self.value(passive, "volley_interval")
                for blade in range(self.blades):
                    delay = blade * self.value(passive, "sword_gap")
                    self.after_action(delay, self.fly_sword, passive, enemy, reach)
        super().act()

    def fly_sword(self, passive, enemy, reach):
        if self.distance(enemy) <= reach:
            self.formula_hit(enemy, passive, "segment.sword", indirect=True, rapid=True)

    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if skill == "basic" and passive == "g2_lingyuan_pa":
            self.last_sword_hit = self.enemies.index(enemy)
            self.sword_qi += self.value(passive, "qi_per_basic")
            threshold = self.value(passive, "qi_per_sword")
            if self.sword_qi >= threshold:
                self.sword_qi -= threshold
                self.blades = min(self.blades + 1, self.value(passive, "max_swords"))
            self.record("sword_qi", skill=passive, qi=self.sword_qi, blades=self.blades)
        if skill == "basic" and passive == "g2_lingyuan_pb" and self.resonance == self.value(passive, "casts_to_charge"):
            self.resonance = 0
            self.formula_hit(enemy, passive, "segment.bonus_basic", indirect=True, rapid=True)
            self.passive_shield(passive)
        if passive == "g2_hanze_pa":
            self.frost_hit(enemy, skill, passive, token)
        if passive == "g2_hanze_pb" and self.tick >= self.rime_ready:
            self.rime_ready = self.tick + self.value(passive, "internal_cooldown")
            self.passive_shield(passive)
            for target in self.enemies:
                if self.distance(target) <= self.value(passive, "radius"):
                    self.settle(passive, {"slow_duration": "slow_duration"}, target, token, None)

    def passive_shield(self, passive):
        nominal = amount(effect(self.skills[passive], "shield"), self.ranks[passive], self.stats)
        self.shield(self.caster, nominal, self.value(passive, "shield_duration"), passive)

    def frost_hit(self, enemy, skill, passive, token):
        target = self.enemies.index(enemy)
        state = self.frost[target]
        if self.tick < state["immunity"]:
            self.record("frost_ineligible", target=target, source=skill, cast=token, reason="target_immunity",
                        untilTick=state["immunity"])
            return
        if self.tick - state["last"] < self.value(passive, "min_hit_gap"):
            self.record("frost_ineligible", target=target, source=skill, cast=token, reason="hit_gap")
            return
        if token is not None and token in state["seen"]:
            self.record("frost_ineligible", target=target, source=skill, cast=token, reason="same_cast")
            return
        if token is not None:
            state["seen"].add(token)
        if self.tick > state["until"]:
            state["count"] = 0
        state["last"] = self.tick
        state["until"] = self.tick + self.value(passive, "stack_window")
        state["count"] += 1
        self.record("frost_stack", target=target, source=skill, cast=token, stacks=state["count"],
                    untilTick=state["until"])
        if state["count"] == self.value(passive, "stacks"):
            state["count"] = 0
            state["immunity"] = self.tick + self.value(passive, "immunity")
            self.record("frost_proc", target=target, immunityUntilTick=state["immunity"])
            self.control(enemy, "root", self.value(passive, "root_duration"), passive)
            self.formula_hit(enemy, passive, "damage", indirect=True, rapid=True)

    def result(self):
        result = super().result()
        window = self.windows["sustained"]
        if self.end != window:
            primary = self.enemies[0]
            result["observationTicks"] = self.end
            result["sustainedHpPerSecond"] = sum(value for tick, value in primary.damage if tick < window) / (window / 20)
            result["controlActionSeconds"] = sum(
                self.loss(primary, tick) - self.loss(primary, tick, include_skill=False)
                for tick in range(window)) / 20
        return result
