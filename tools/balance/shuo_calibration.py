"""Shuo's zero-preparation replays use a single axis and a frozen frontal contact."""

from combo_calibration import amount, effect

CANGFENG = "shuo_cangfeng"
JIANQIE = "shuo_jianqie"
NADAO = "shuo_nadao"
GUARD = "shuo_qiaofan"
YISHAN = "shuo_yishan"
DUANLIU = "shuo_duanliu"
WUSHENG = "shuo_wusheng"
CANYUE = "shuo_canyue"


class ShuoReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.forms = self.config["skills"]
        self.config = {**self.config, "skills": dict(self.forms)}
        self.drawn = False
        self.guard_until = self.counter_until = self.passive_until = 0
        self.edge = 0
        self.moon_step = self.moon_until = 0
        self.moon_hits = 0
        self.draw_tokens = {}
        self.hit_tokens = set()
        self.origins = {}
        self.directions = {}

    def execute(self, step):
        skill = self.selected.get(step.get("skill"), step.get("skill"))
        if step["action"] == "recast" and skill in (GUARD, CANYUE):
            return self.recast_shuo(skill, step)
        if skill in self.forms:
            form = self.forms[skill]
            self.config["skills"][skill] = form.get("drawn", form) if self.drawn else form
        return super().execute(step)

    def advance(self):
        super().advance()
        if not self.drawn and not self.channel and not self.motion and self.selected_passive() == CANGFENG:
            self.edge = min(self.value(CANGFENG, "gauge_max"), self.edge + self.value(CANGFENG, "gauge_per_tick"))
        if self.moon_step and self.tick >= self.moon_until:
            self.moon_step = self.moon_hits = 0
            self.sheathe()

    def sheathe(self):
        self.drawn = False
        self.edge = 0
        self.record("stance", state="sheathed")

    def draw_blade(self):
        if self.drawn:
            return 0
        self.drawn = True
        self.record("stance", state="drawn")
        passive = self.selected_passive()
        bonus = 0
        if passive == CANGFENG:
            formula = effect(self.skills[passive], "full_edge")
            bonus = amount(formula, self.ranks[passive], self.stats) * self.edge / self.value(passive, "gauge_max")
        elif passive == JIANQIE and self.tick < self.passive_until:
            bonus = amount(effect(self.skills[passive], "damage"), self.ranks[passive], self.stats)
        self.edge = self.passive_until = self.guard_until = 0
        return bonus

    def basic_release(self, label, quick, full):
        bonus = self.draw_blade()
        super().basic_release(label, quick, full)
        if bonus:
            self.draw_tokens[("basic", self.tick)] = bonus

    def on_hit(self, enemy, skill, passive, token):
        super().on_hit(enemy, skill, passive, token)
        if skill == "basic":
            bonus = self.draw_tokens.pop(("basic", self.tick), 0)
            if bonus:
                self.damage(enemy, bonus, "physical", passive, rapid=True, indirect=True)

    def incoming(self, target, kind, raw):
        if (target is self.caster and kind != "true" and self.tick < self.guard_until
                and self.initial.get("frontalGuardContact") and self.tick >= self.opponent["stunTicks"]):
            self.guard_until = 0
            self.counter_until = self.tick + self.value(GUARD, "counter_window")
            if self.selected_passive() == JIANQIE:
                self.passive_until = self.tick + self.value(JIANQIE, "ready_window")
            self.record("parry", skill=GUARD, incomingRawHp=raw, recastUntilTick=self.counter_until)
        else:
            super().incoming(target, kind, raw)

    def release(self, skill, step):
        data = self.config["skills"][skill]
        token = (skill, self.tick)
        self.origins[token] = self.position
        self.directions[token] = 1 if self.enemies[0].position >= self.position else -1
        if skill == GUARD and not self.drawn:
            self.guard_until = self.tick + self.value(GUARD, "guard_window")
        if data.get("drawWeapon") and skill != CANYUE:
            self.draw_tokens[token] = self.draw_blade()
        if skill == CANYUE and not self.drawn:
            self.record("released", skill=skill, label=step.get("label"))
            self.moon_strike(1, step.get("label"))
            return
        super().release(skill, step)
        if "sheatheAfter" in data:
            self.after_action(self.value(skill, data["sheatheAfter"]), self.sheathe)

    def recast_shuo(self, skill, step):
        if self.channel or self.motion or self.pending or not self.meets(step.get("requires")):
            return False
        if self.omitted_slot and self.selected[self.omitted_slot] == skill:
            return False
        if skill == GUARD:
            if self.tick >= self.counter_until:
                return False
            self.counter_until = 0
            token = (GUARD, self.tick)
            self.draw_tokens[token] = self.draw_blade()
            self.record("recast", skill=GUARD, manaCost=0)
            if self.initial.get("counterContact", True) and self.distance() <= self.value(GUARD, "counter_range"):
                enemy = self.enemies[0]
                if self.formula_hit(enemy, GUARD, "riposte", token=token, label=step.get("label")):
                    self.control(enemy, "stun", self.value(GUARD, "counter_stun"), GUARD)
                    nominal = amount(effect(self.skills[GUARD], "shield"), self.ranks[GUARD], self.stats)
                    self.shield(self.caster, nominal, self.value(GUARD, "counter_shield_duration"), GUARD)
            return True
        if not self.moon_step or self.tick >= self.moon_until:
            return False
        follow = self.moon_step + 1
        self.moon_step = 0
        self.record("recast", skill=CANYUE, step=follow, manaCost=0)
        duration = self.value(CANYUE, "windup")
        self.motion = (CANYUE, self.action_progress + duration)
        self.after_action(duration, self.moon_strike, follow, step.get("label"))
        return True

    def moon_strike(self, step, label):
        enemy = self.enemies[0]
        token = (CANYUE, self.tick)
        self.draw_tokens[token] = self.draw_blade()
        name = "moon_third" if step == 3 else "moon_first_two"
        reach = "final_range" if step == 3 else "opening_range"
        hit = self.distance() <= self.value(CANYUE, reach) and self.formula_hit(enemy, CANYUE, name, token=token, label=label)
        if hit:
            self.moon_hits += 1
            if step == 1:
                self.control(enemy, "movement_slow", self.value(CANYUE, "opening_slow_duration"), CANYUE,
                             1 - self.value(CANYUE, "opening_slow_rate"))
            if step == 3 and self.moon_hits == 3:
                self.control(enemy, "root", self.value(CANYUE, "final_root"), CANYUE)
        if step == 3:
            self.moon_step = self.moon_hits = 0
            duration = self.value(CANYUE, "follow_and_sheathe")
            self.motion = (CANYUE, self.action_progress + duration)
            self.after_action(duration, self.sheathe)
        else:
            self.moon_step = step
            self.moon_until = self.tick + self.value(CANYUE, "recast_window")

    def apply(self, skill, row, token, elapsed, label):
        if row.get("needsPriorHit") and token not in self.hit_tokens:
            return
        if row.get("kind") == "position":
            self.position = self.origins[token] + self.directions[token] * self.value(skill, row["distance"])
            self.record("position", skill=skill, distanceBlocks=self.distance())
            return
        if row.get("forward") and (self.enemies[0].position - self.position) * self.directions[token] < 0:
            return
        if not row.get("castOrigin"):
            super().apply(skill, row, token, elapsed, label)
            return
        position = self.position
        self.position = self.origins[token]
        try:
            super().apply(skill, row, token, elapsed, label)
        finally:
            self.position = position

    def impact(self, skill, row, enemy, index, token, label):
        if row.get("controlRequiresPriorHit") and token not in self.hit_tokens:
            row = {key: value for key, value in row.items() if key != "root"}
        super().impact(skill, row, enemy, index, token, label)

    def formula_hit(self, enemy, skill, name, **kwargs):
        token = kwargs.get("token")
        formula = effect(self.skills[skill], name)
        bonus = self.draw_tokens.pop(token, 0)
        raw = amount(formula, self.ranks[skill], self.stats) + bonus
        if skill == WUSHENG and name == "delayed_cut":
            raw *= 1 + self.value(skill, "missing_hp_scale") * (1 - enemy.hp / enemy.stats["health"])
        hit = self.damage(enemy, raw, formula["kind"], skill, **kwargs)
        if hit and token:
            self.hit_tokens.add(token)
        return hit
