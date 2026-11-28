"""Ink arrays and player-by-player spirit support for the #113 formation replay."""

from collections import defaultdict
from combo_calibration import amount, effect


class MageSupportReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.entered_fields = defaultdict(set)
        self.echo_marks = {}
        self.tether_breaks = set()

    def execute(self, step):
        if self.tick < step.get("notBeforeTick", 0):
            return False
        return super().execute(step)

    def grant_reception(self, skill, row, token, elapsed, label):
        if row["target"] == "aimed_ally" and self.conditions.get("noAllies"):
            row = {**row, "target": "self"}
        elif row["target"] == "aimed_ally":
            row = {**row, "target": "primary"}
        super().apply(skill, row, token, elapsed, label)

    def apply(self, skill, row, token, elapsed, label):
        if not self.meets(row.get("requires")):
            return
        kind = row["kind"]
        if kind in ("heal", "shield", "guard"):
            self.grant_reception(skill, row, token, elapsed, label)
        elif kind == "motion_endpoint":
            self.position = self.formation_points[token]
            self.record("position", skill=skill, endpointBlocks=self.position)
        elif kind == "tether_check":
            self.check_tether(skill, token, elapsed, label)
        elif kind == "portal_ready":
            self.record("portal_ready", skill=skill, usableUntilTick=self.tick + self.value(skill, "portal_duration"))
        elif row.get("center"):
            self.field(skill, row, token, elapsed, label)
        else:
            super().apply(skill, row, token, elapsed, label)

    def field(self, skill, row, token, elapsed, label):
        center = self.formation_points[token] if row["center"] == "point" else self.position
        radius = self.value(skill, row["radius"])
        for index, enemy in enumerate(self.enemies):
            remaining = self.probe["fieldStayTicks"][index] if self.probe else None
            if enemy.hp <= 0 or abs(enemy.position - center) > radius or remaining is not None and elapsed > remaining:
                continue
            if row.get("firstEntry"):
                if index in self.entered_fields[token]:
                    continue
                self.entered_fields[token].add(index)
            self.impact(skill, row, enemy, index, token, label)

    def movement_control(self, enemy, skill, rate, duration):
        self.control(enemy, "movement_slow", self.value(skill, duration), skill, self.value(skill, rate))

    def impact(self, skill, row, enemy, index, token, label):
        if not any(key in row for key in ("derived", "movementOnly", "unconditionalControl")):
            return super().impact(skill, row, enemy, index, token, label)
        if not row.get("movementOnly"):
            derived = row.get("derived", False)
            self.formula_hit(enemy, skill, row["effect"], token=token, label=label, rapid=derived, indirect=derived)
        if "root" in row:
            self.control(enemy, "root", self.value(skill, row["root"]), skill)
        if "movementSlow" in row:
            self.movement_control(enemy, skill, *row["movementSlow"])

    def check_tether(self, skill, token, elapsed, label):
        if token in self.tether_breaks:
            return
        enemy = self.enemies[0]
        if enemy.hp <= 0 or self.distance(enemy) > self.value(skill, "range") or self.tick == self.initial.get("tetherBreakTick"):
            self.tether_breaks.add(token)
            self.record("tether_broken", skill=skill, target=0)
        elif elapsed == self.value(skill, "tether_duration"):
            self.formula_hit(enemy, skill, "segment.tether", token=token, label=label)
            self.movement_control(enemy, skill, "movement_rate", "slow_duration")

    def advance(self):
        if self.channel and self.tick == self.initial.get("interruptAtTick"):
            skill, _, _, paid = self.channel
            self.channel = None
            self.record("cancelled", skill=skill, channel="windup", paid=paid, reason="enemy_stun")
        super().advance()

    def act(self):
        start = self.initial.get("interruptAtTick")
        if start is not None and start <= self.tick < start + self.initial.get("interruptStunTicks", 8):
            return
        super().act()

    def support_reading(self, target):
        reading = super().support_reading(target)
        label = self.target_label(target)
        relevant = [event for event in self.events if event.get("target") == label]
        shields = [event for event in relevant if event["event"] == "shield"]
        return {**reading, "newShieldHp": sum(event["newShieldHp"] for event in shields),
                "healOverflowHp": sum(event["overhealHp"] for event in relevant if event["event"] == "heal"),
                "sameSourceRefreshesWithoutGain": sum(event["newShieldHp"] == 0 for event in shields)}


class XuanchenReplayMixin(MageSupportReplayMixin):
    def commit(self, skill, step):
        previous = self.ready.get(skill)
        super().commit(skill, step)
        passive = self.selected_passive()
        if passive != "g1_xuanchen_pa" or self.ready.get(skill) == previous or self.tick < self.caster.passive_ready:
            return
        row = {"target": "lowest_ally", "radius": "range"}
        self.formation_points[(passive, self.tick)] = self.position
        targets = self.supports(row, (passive, self.tick))
        if not targets:
            targets = [self.caster]
        self.caster.passive_ready = self.tick + self.value(passive, "internal_cooldown")
        nominal = amount(effect(self.skills[passive], "shield"), self.ranks[passive], self.stats)
        for ally in targets:
            self.shield(ally, nominal, self.value(passive, "shield_duration"), passive)

    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if passive != "g1_xuanchen_pb" or skill == "basic" or self.tick < enemy.passive_ready:
            return
        target = self.enemies.index(enemy)
        mark, until = self.echo_marks.get(target, (None, -1))
        if self.tick >= until:
            self.echo_marks[target] = skill, self.tick + self.value(passive, "mark_window")
            self.record("seal_mark", skill=passive, via=skill, target=target)
        elif skill != mark:
            self.echo_marks.pop(target)
            enemy.passive_ready = self.tick + self.value(passive, "internal_cooldown")
            self.formula_hit(enemy, passive, "damage", rapid=True, indirect=True)


class YunshengReplayMixin(MageSupportReplayMixin):
    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if passive != "g2_yunsheng_pb" or self.tick < self.caster.passive_ready:
            return
        self.caster.passive_ready = self.tick + self.value(passive, "internal_cooldown")
        self.formation_points[(passive, self.tick)] = self.position
        row = {"target": "allies", "radius": "radius", "includeSelf": True}
        nominal = amount(effect(self.skills[passive], "heal"), self.ranks[passive], self.stats)
        for ally in self.supports(row, (passive, self.tick)):
            self.heal(ally, nominal, passive, False)

    def support_passive(self, target):
        super().support_passive(target)
        passive = self.selected_passive()
        if passive != "g2_yunsheng_pa" or self.tick < target.passive_ready:
            return
        label = self.target_label(target)
        stacks, successive, until = self.echo_marks.get(label, (0, -100, -1))
        if self.tick - successive < self.value(passive, "support_interval"):
            return
        stacks = (stacks if self.tick < until else 0) + 1
        self.echo_marks[label] = stacks, self.tick, self.tick + self.value(passive, "support_window")
        self.record("effective_support_mark", skill=passive, target=label, stack=stacks)
        if stacks >= self.value(passive, "support_count"):
            self.echo_marks[label] = 0, self.tick, -1
            target.passive_ready = self.tick + self.value(passive, "internal_cooldown")
            nominal = amount(effect(self.skills[passive], "shield"), self.ranks[passive], self.stats)
            self.shield(target, nominal, self.value(passive, "shield_duration"), passive)
