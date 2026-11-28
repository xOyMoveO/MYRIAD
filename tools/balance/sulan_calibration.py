"""Single-axis motion and per-target passive timing for Sulan's frozen scenarios."""

from math import ceil

WALK = "g1_sulan_pa"
COMBO = "g1_sulan_pb"
MARK = "g1_sulan_n2"


def dash_hits(replay, skill, row, token, label):
    start = replay.position
    direction = 1 if replay.enemies[0].position >= start else -1
    distance = replay.value(skill, "dash_range")
    duration = replay.value(skill, "dash_duration")
    speed = distance / duration
    padding = replay.value(skill, "path_radius")
    for target in replay.enemies:
        ahead = (target.position - start) * direction
        if -padding <= ahead <= distance + padding:
            due = max(1, ceil((ahead - padding) / speed))
            def hit(enemy=target):
                replay.formula_hit(enemy, skill, row["effect"], token=token, label=label)
            replay.after_action(due, hit)

    def finish():
        replay.position = start + direction * distance
        replay.record("position", skill=skill, distanceBlocks=replay.distance())

    replay.after_action(duration, finish)


class SulanReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.walk_ready = (self.selected_passive() == WALK
                           and self.initial.get("naturalTravel", 0) >= self.value(WALK, "charge_distance"))
        self.combo_until = 0
        self.combo_ready = 0

    def damage(self, enemy, raw, kind, skill, label=None, rapid=False, indirect=False, token=None):
        marked = (not indirect and kind != "true" and self.marks.get(enemy, 0) > self.tick
                  and self.selected["w"] == MARK and self.omitted_slot != "w")
        if marked:
            raw *= self.value(MARK, "mark_multiplier")
        hit = super().damage(enemy, raw, kind, skill, label, rapid, indirect, token)
        if hit and marked and skill != MARK:
            self.marks.pop(enemy, None)
            self.record("mark_consumed", skill=MARK, target=self.enemies.index(enemy), source=skill)
        return hit

    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if skill == "basic":
            if passive == WALK and self.walk_ready:
                self.walk_ready = False
                self.formula_hit(enemy, passive, "segment.bonus_basic", rapid=True, indirect=True)
            if passive == COMBO and self.tick < self.combo_until:
                self.combo_until = 0
                self.formula_hit(enemy, passive, "segment.bonus_basic", rapid=True, indirect=True)
                self.control(enemy, "movement_slow", self.value(passive, "slow_duration"), passive,
                             1 - self.value(passive, "slow_rate"))
        elif passive == COMBO and self.tick >= self.combo_ready:
            self.combo_until = self.tick + self.value(passive, "ready_window")
            self.combo_ready = self.tick + self.value(passive, "internal_cooldown")
            self.record("combo_ready", skill=passive, untilTick=self.combo_until)

    def impact(self, skill, row, enemy, index, token, label):
        if skill == MARK:
            before = enemy.hp
            super().impact(skill, row, enemy, index, token, label)
            if enemy.hp < before:
                self.marks[enemy] = self.tick + self.value(skill, "mark_window")
                self.record("marked", skill=skill, target=index, untilTick=self.marks[enemy])
            return
        super().impact(skill, row, enemy, index, token, label)

    def apply(self, skill, row, token, elapsed, label):
        kind = row["kind"]
        if kind == "dash_hit":
            dash_hits(self, skill, row, token, label)
        elif kind == "returning_blade":
            self.later(1, self.returning_blade, skill, token, label, self.position,
                                 1 if self.enemies[0].position >= self.position else -1, 0, False, set())
        elif kind == "stop_dash":
            duration = self.value(skill, "dash_duration")
            direction = 1 if self.enemies[0].position >= self.position else -1
            speed = self.value(skill, "dash_range") / duration
            self.after_action(1, self.stop_dash_step, skill, token, label, direction, speed, duration)
        elif kind == "follow_field":
            for enemy in self.enemies:
                if self.distance(enemy) <= self.value(skill, "radius"):
                    self.control(enemy, "movement_slow", self.value(skill, "status_duration"), skill,
                                 1 - self.value(skill, "slow_rate"))
        else:
            super().apply(skill, row, token, elapsed, label)

    def returning_blade(self, skill, token, label, position, direction, travelled, returning, hit):
        speed = self.value(skill, "projectile_speed")
        reach = self.value(skill, "range")
        padding = self.value(skill, "projectile_radius")
        if returning:
            delta = self.position - position
            next_position = position + max(-speed, min(speed, delta))
        else:
            delta = min(speed, reach - travelled)
            next_position = position + direction * delta
            travelled += delta
        low, high = sorted((position, next_position))
        for enemy in self.enemies:
            if enemy not in hit and low - padding <= enemy.position <= high + padding:
                hit.add(enemy)
                leg = "return" if returning else "outbound"
                self.formula_hit(enemy, skill, "segment.out_and_back", token=token, label=label or leg)
        if returning and next_position == self.position:
            return
        if not returning and travelled >= reach:
            returning, hit = True, set()
        # This synthetic flight is independent from the actor and has no applied projectile slow.
        self.later(1, self.returning_blade, skill, token, label, next_position, direction, travelled, returning, hit)

    def stop_dash_step(self, skill, token, label, direction, speed, remaining):
        self.position += speed * direction
        collided = any(enemy.hp > 0 and self.distance(enemy) <= self.value(skill, "path_radius")
                       for enemy in self.enemies)
        if not collided and remaining > 1:
            self.after_action(1, self.stop_dash_step, skill, token, label, direction, speed, remaining - 1)
            return
        self.motion = None
        self.record("position", skill=skill, distanceBlocks=self.distance())
        for index, enemy in enumerate(self.enemies):
            if self.distance(enemy) <= self.value(skill, "impact_radius"):
                self.impact(skill, {"effect": "damage", "movementSlow": ["movement_rate", "slow_duration"]},
                            enemy, index, token, label)
