"""伊珂的合成对照，使用快照的行动刻起手及真实刻闸门。"""

from math import ceil

from combo_calibration import amount

MARK = "yike_rainbow_mark"
BARRIER = "yike_steady_barrier"
Q = "yike_rainbow_shell"
W = "yike_refraction_denial"
FIXED = "yike_fixed_artillery"
MOBILE = "yike_mobile_array"
HEAVY = "yike_distant_bombardment"
BEAM = "yike_prismatic_beam"


class YikeReplayMixin:
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.stance = None
        self.beam = None
        self.w_until = self.barrier_ready_tick = 0
        self.mark_targets = {}
        self.mark_seen = set()
        self.horizontal_path = self.initial.get("casterHorizontalPath", [])
        self.form_rules = {name: self.value(Q, f"form_{name}") for name in ("normal", "fixed", "mobile")}

    @staticmethod
    def _form_value(form, name):
        return form[name]["value"]

    def _form_name(self):
        return self.stance["form"] if self.stance else "normal"

    def cooldown(self, skill):
        return 0 if skill == Q and self.stance else super().cooldown(skill)

    def _shot_ready(self):
        return max(self.stance["next_muzzle"], self.stance["paused_until"],
                   self.ready.get(Q, 0) if not self.stance["fired"] else 0)

    def _exit_stance(self, reason):
        stance, self.stance = self.stance, None
        if self.channel and self.channel[0] == Q:
            self.channel = None
            self.record("cancelled", skill=Q, channel="unpaid_windup", paid=False)
        if stance["fired"]:
            self.ready[Q] = max(self.ready.get(Q, 0), self.tick + super().cooldown(Q))
        self.record("stance_exited", skill=stance["skill"], form=stance["form"], reason=reason,
                    remainingAmmo=stance["ammo"], normalCooldownTicks=max(0, self.ready.get(Q, 0) - self.tick))

    def _barrier_path(self):
        # Each step records distance travelled, so a return to the starting point still counts.
        window = self.value(BARRIER, "path_window")
        return sum(blocks for tick, blocks in self.horizontal_path if self.tick - window < tick <= self.tick)

    def _barrier_is_ready(self):
        return (self.tick >= self.value(BARRIER, "path_window")
                and self.tick >= self.barrier_ready_tick
                and self._barrier_path() <= self.value(BARRIER, "path_limit") + 1e-6)

    def _test_wait(self, step):
        condition = step.get("condition")
        if step["action"] != "wait_until" or condition not in ("stance_deployed", "barrier_ready"):
            return None
        start = self.waits.setdefault(self.cursor, self.tick)
        met = self.stance is not None if condition == "stance_deployed" else self._barrier_is_ready()
        if met or self.tick - start >= step["maxTicks"]:
            self.record("wait_until", condition=condition, met=met, waitedTicks=self.tick - start)
            return True
        return False

    def execute(self, step):
        wait = self._test_wait(step)
        if wait is not None:
            return wait
        action = step["action"]
        skill = self.selected.get(step.get("skill"), step.get("skill"))
        if not self.meets(step.get("requires")) or (self.omitted_slot is not None
                                                    and skill == self.selected[self.omitted_slot]):
            return False
        if action in ("cast", "recast", "cancel") and self.stance and skill == self.stance["skill"]:
            if self.config["execution"]["type"] == "apl" and not self.script:
                return False
            self._exit_stance("manual")
            return True
        if action in ("recast", "cancel") and skill == BEAM and skill == self.selected["r"]:
            if self.beam or (self.channel and self.channel[0] == BEAM):
                self.beam = None
                self.channel = None
                self.record("cancelled", skill=BEAM, channel="beam", manaCost=0)
                return True
        if action == "cast" and (self.beam or self.tick < self.w_until):
            return False
        if action == "cast" and skill == Q:
            form = self.form_rules[self._form_name()]
            reach = self._form_value(form, "range") + self._form_value(form, "radius") + self.value(Q, "muzzle_offset")
            if self.distance() > reach:
                return False
            if self.stance:
                buffer = self._form_value(form, "shotRequestBuffer") if self.stance["fired"] else 0
                if self.tick < self._shot_ready() - buffer:
                    return False
        if action == "cast" and skill == W and self.channel and self.channel[0] == Q:
            if self.execution["type"] == "apl" and (
                    self.tick < self.ready.get(W, 0) or self.mana_cost(W) > self.mana
                    or self.distance() > self.value(W, "target_range")):
                return False
            # A real W request cancels an unpaid Q before it checks W mana or enters point selection.
            self.channel = None
            self.record("cancelled", skill=Q, channel="unpaid_windup", paid=False)
            previous_input_ready, self.input_ready = self.input_ready, self.tick
            if not super().execute(step):
                self.input_ready = previous_input_ready
                self.record("cast_requested", skill=W, accepted=False, reason="unavailable_after_q_cancel")
            return True
        return super().execute(step)

    def begin(self, skill, step):
        if skill in (W, HEAVY):
            step = {**step, "pointBlocks": self.enemies[0].position}
        super().begin(skill, step)

    def commit(self, skill, step):
        if skill == Q and self.stance and self.tick < self._shot_ready():
            self.record("rejected", skill=Q, reason="muzzle_deadline")
            return
        can_pay = self.mana >= self.mana_cost(skill)
        if can_pay and skill in (HEAVY, BEAM) and self.stance:
            self._exit_stance("ultimate")
        super().commit(skill, step)
        passive = self.selected_passive()
        if passive != BARRIER or not can_pay or not self._barrier_is_ready():
            return
        nominal = amount(self.value(BARRIER, "shield"), self.ranks[BARRIER], self.stats)
        self.barrier_ready_tick = self.tick + self.value(BARRIER, "internal_cooldown")
        self.shield(self.caster, nominal, self.value(BARRIER, "shield_duration"), BARRIER)

    def release(self, skill, step):
        super().release(skill, step)
        label, token = step.get("label"), (skill, self.tick)
        if skill in (FIXED, MOBILE):
            name = self.value(skill, "stance_form")
            form = self.form_rules[name]
            self.stance = {
                "form": name, "skill": skill, "end": self.tick + self._form_value(form, "duration"),
                "next_muzzle": self.tick, "paused_until": 0, "fired": False,
                "ammo": self._form_value(form, "shots")
            }
            self.record("stance_entered", skill=skill, form=name, ammo=self.stance["ammo"], manaLeft=self.mana)
            self._check_stance()
        elif skill == Q:
            self._muzzle(token, label)
        elif skill == W:
            delay = self.value(W, "warning_ticks")
            self.w_until = self.tick + delay
            if self.stance:
                form = self.form_rules[self.stance["form"]]
                self.stance["paused_until"] = max(self.stance["paused_until"], self.tick + self._form_value(form, "pauseForW"))
            self.later(delay, self._explode, W, step["pointBlocks"], token, label)
        elif skill == HEAVY:
            flight = self.value(HEAVY, "flight")
            distance = abs(step["pointBlocks"] - self.position)
            ticks = ceil(max(flight["minimumActionTicks"], distance * 20 / flight["speedBlocksPerSecond"]))
            if self.initial.get("heavyPathBlocked", False):
                self.record("flight_blocked", skill=skill, label=label)
            else:
                self.later(ticks, self._explode, HEAVY, step["pointBlocks"], token, label)
        elif skill == BEAM:
            self.beam = token
            interval, duration = self.value(BEAM, "pulse_interval"), self.value(BEAM, "beam_duration")
            for elapsed in range(interval, duration + 1, interval):
                self.later(elapsed, self._beam_pulse, token, label, elapsed == duration)

    def _muzzle(self, token, label):
        name = self._form_name()
        form = self.form_rules[name]
        multiplier = self._form_value(form, "damageMultiplier")
        stance_rank = self.ranks[self.stance["skill"]] if self.stance else 1
        scale = multiplier["base"] + (stance_rank - 1) * multiplier["growthPerStanceRank"]
        formula = self.value(Q, "damage")
        raw = amount(formula, self.ranks[Q], self.stats) * scale
        if self.stance:
            self.stance["ammo"] -= 1
            self.stance["fired"] = True
            self.stance["next_muzzle"] = self.tick + self._form_value(form, "nextShot")
        self.record("muzzle", skill=Q, label=label, form=name, manaLeft=self.mana, rawDamage=raw,
                    ammo=self.stance["ammo"] if self.stance else None,
                    cooldownTicks=0 if self.stance else self.cooldown(Q),
                    nextShotTicks=self._form_value(form, "nextShot") if self.stance else 0)
        self._check_stance()
        direction = 1 if self.enemies[0].position >= self.position else -1
        origin = self.position + direction * self.value(Q, "muzzle_offset")
        self.later(1, self._shell_tick, origin, direction, 0, form, raw, formula["kind"], token, label)

    def _shell_tick(self, origin, direction, distance, form, raw, kind, token, label):
        radius = self._form_value(form, "radius")
        reach = self._form_value(form, "range")
        next_distance = min(reach, distance + self._form_value(form, "effectiveSpeed") / 20)
        collision = self.initial["targetCollisionDistanceAllowance"]
        contacts = [max(distance, near_edge) for enemy in self.enemies if enemy.hp > 0
                    and (near_edge := (enemy.position - origin) * direction - collision) <= next_distance
                    and (enemy.position - origin) * direction + collision >= distance]
        if not contacts and next_distance < reach:
            self.later(1, self._shell_tick, origin, direction, next_distance, form, raw, kind, token, label)
            return
        center = origin + direction * (min(contacts) if contacts else reach)
        for enemy in self.enemies:
            if abs(enemy.position - center) <= radius + collision:
                self.damage(enemy, raw, kind, Q, label=label, token=token)

    def _explode(self, skill, center, token, label):
        radius = self.value(skill, "blast_radius") + self.initial["targetCollisionDistanceAllowance"]
        for enemy in self.enemies:
            if abs(enemy.position - center) <= radius:
                self.formula_hit(enemy, skill, "damage", token=token, label=label)
                if skill == W:
                    self.control(enemy, "movement_slow", self.value(W, "slow_duration"), W,
                                 1 - self.value(W, "movement_slow"))

    def _beam_pulse(self, token, label, last):
        if self.beam != token:
            return
        for enemy in self.enemies:
            if self.distance(enemy) <= self.value(BEAM, "beam_range"):
                self.formula_hit(enemy, BEAM, "per_pulse", token=token, label=label)
        if last:
            self.beam = None

    def _check_stance(self):
        if not self.stance:
            return
        if self.stance["ammo"] == 0:
            self._exit_stance("empty")
        elif self.tick >= self.stance["end"]:
            self._exit_stance("expired")
        elif self.mana < self.mana_cost(Q):
            self._exit_stance("mana")

    def advance(self):
        self._check_stance()
        super().advance()

    def basic(self, step):
        if self.stance or self.beam or self.tick < self.w_until:
            return False
        return super().basic(step)

    def basic_release(self, label, quick, full):
        row = self.own["basicAttack"]
        enemy = self.enemies[0]
        self.basic_ready = self.action_progress + ceil(20 / self.stats["attackSpeed"])
        self.record("basic_release", label=label, mode="cannon")
        flight = ceil(max(.01, self.distance() - row["muzzleOffset"]["value"]
                          - self.initial["targetCollisionDistanceAllowance"]) / row["flightSpeed"]["value"])
        self.later(flight, self.damage, enemy, self.stats["total_physical"], "physical", "basic", label)

    def on_hit(self, enemy, skill, passive, token):
        if passive != MARK or token is None or skill not in self.ranks or self.mana_cost(skill) <= 0:
            return
        target = self.enemies.index(enemy)
        if (target, token) in self.mark_seen:
            return
        self.mark_seen.add((target, token))
        count, until = self.mark_targets.get(target, (0, 0))
        count = count + 1 if self.tick < until else 1
        until = self.tick + self.value(MARK, "mark_window")
        self.record("passive_mark", skill=MARK, target=target, count=count, untilTick=until)
        if count >= self.value(MARK, "marks_to_trigger"):
            self.formula_hit(enemy, MARK, "damage", indirect=True, rapid=True)
            count = 0
        self.mark_targets[target] = count, until
