"""Reduces Chixiao's single-target triggers to the frozen L3 event stream."""

from combo_calibration import amount, effect

BATTLE_INTENT = "g2_chixiao_pa"
UNBROKEN_WILL = "g2_chixiao_pb"
CRIMSON_PARRY = "g2_chixiao_ma"
WAR_BLADE = "g2_chixiao_ub"
SOUL_PURSUIT = "g2_chixiao_n2"


class ChixiaoReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.battle_target = self.battle_token = None
        self.battle_stacks = 0
        self.battle_last = self.battle_until = -100
        self.will_ready = 0
        self.brace_until = 0
        self.blade_until = self.blade_hits = 0
        self.blade_last = -100

    def release(self, skill, step):
        if skill == WAR_BLADE:
            self.blade_until = self.tick + self.value(skill, "bonus_window")
            self.blade_hits = self.value(skill, "bonus_hits")
        elif skill == CRIMSON_PARRY:
            self.conditions["brace_hit"] = False
            self.brace_until = self.tick + self.value(skill, "brace_window")
        super().release(skill, step)

    def execute(self, step):
        skill = self.selected.get(step.get("skill"), step.get("skill"))
        if step["action"] != "recast" or skill != SOUL_PURSUIT:
            return super().execute(step)
        saved = self.recasts.get(skill)
        if not saved or self.tick >= saved[0] or self.channel or self.motion or self.pending:
            return False
        if self.omitted_slot and self.selected[self.omitted_slot] == skill:
            return False
        del self.recasts[skill]
        duration = self.value(skill, "dash_duration")
        self.motion = (skill, self.action_progress + duration)
        self.record("recast", skill=skill, channel="return_dash", manaCost=0)
        self.after_action(duration, self.finish_return, saved[1])
        return True

    def finish_return(self, position):
        self.position = position
        self.record("position", skill=SOUL_PURSUIT, distanceBlocks=self.distance())

    def incoming(self, target, kind, raw):
        external_source = self.initial.get("braceExternalSource")
        declared_contact = external_source or self.initial.get("braceContact") and self.tick >= self.opponent["stunTicks"]
        if target is self.caster and kind != "true" and self.tick < self.brace_until and declared_contact:
            self.conditions["brace_hit"] = True
            reduced = raw * self.value(CRIMSON_PARRY, "damage_taken_rate")
            self.record("brace", skill=CRIMSON_PARRY, rawHp=raw, reducedRawHp=reduced)
            raw = reduced
        super().incoming(target, kind, raw)

    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if passive == BATTLE_INTENT:
            self.battle_intent(enemy, skill, token)
        elif (passive == UNBROKEN_WILL and self.tick >= self.will_ready and
              self.caster.hp <= self.stats["health"] * self.value(passive, "health_threshold")):
            self.will_ready = self.tick + self.value(passive, "internal_cooldown")
            nominal = amount(effect(self.skills[passive], "shield"), self.ranks[passive], self.stats)
            self.shield(self.caster, nominal, self.value(passive, "shield_duration"), passive)
        if (skill == "basic" and self.tick < self.blade_until and self.blade_hits
                and self.tick - self.blade_last >= self.value(WAR_BLADE, "bonus_interval")):
            self.blade_hits -= 1
            self.blade_last = self.tick
            self.formula_hit(enemy, WAR_BLADE, "segment.bonus_basic", rapid=True, indirect=True)

    def battle_intent(self, enemy, skill, token):
        if self.battle_target is not enemy or self.tick > self.battle_until:
            self.battle_stacks = 0
        if self.tick - self.battle_last < self.value(BATTLE_INTENT, "hit_interval"):
            return
        if skill != "basic" and token == self.battle_token:
            return
        self.battle_target = enemy
        self.battle_token = token if skill != "basic" else None
        self.battle_last = self.tick
        self.battle_until = self.tick + self.value(BATTLE_INTENT, "stack_window")
        maximum = self.value(BATTLE_INTENT, "stack_count")
        if self.battle_stacks >= maximum and skill != "basic":
            self.battle_stacks = 0
            self.formula_hit(enemy, BATTLE_INTENT, "damage", rapid=True, indirect=True)
        else:
            self.battle_stacks = min(maximum, self.battle_stacks + 1)
        self.record("battle_intent", skill=BATTLE_INTENT, stacks=self.battle_stacks)
