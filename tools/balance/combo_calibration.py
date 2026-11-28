"""Snapshot-driven L1 inventory and L3 action/event replay for the 3v3 calibration."""

from collections import Counter, defaultdict
from dataclasses import dataclass, field
from heapq import heappop, heappush
from itertools import count
from math import ceil, floor
from pathlib import Path
import json


SCENARIOS = Path(__file__).with_name("scenarios") / "combos"
SLOTS = ("passive", "q", "w", "e", "r")
METRICS = ("burstHp", "sustainedHpPerSecond", "controlActionSeconds", "effectiveSupportHp")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def named(rows):
    return {row["id"]: row for row in rows}


def effect(skill, name):
    return named(skill["effects"])[f"{skill['id']}.{name}"]["rule"]["value"]


def attributes(role, level, purchases, gain):
    def attribute(name):
        value = role["attributes"][name]["value"]
        return value[level - 1] if isinstance(value, list) else value

    strength = purchases.get("strength", 0) * gain["physicalAttackPerStrength"]
    wisdom = purchases.get("wisdom", 0) * gain["spellPowerPerWisdom"]
    defense = purchases.get("defense", 0) * gain["defensePerPoint"]
    speed = purchases.get("speed", 0) * gain["attackSpeedPerPoint"]
    return {
        "health": attribute("health"), "mana": attribute("mana"), "manaRegen": attribute("manaRegen"),
        "bonus_physical": strength, "total_physical": attribute("physicalAttack") + strength,
        "bonus_spell": wisdom, "total_spell": attribute("spellPower") + wisdom,
        "physicalDefense": attribute("physicalDefense") + defense,
        "magicDefense": attribute("magicResistance") + defense,
        "attackSpeed": min(gain["attackSpeedMax"], attribute("attackSpeed") + speed),
        "innateAttackSpeed": attribute("attackSpeed"), "cooldownPurchases": purchases.get("cooldown", 0),
        "none": 0,
    }


def purchase_cost(pricing, purchases):
    total = 0
    for points in purchases.values():
        price = pricing["startingGold"]
        for _ in range(points):
            total += price
            price = (price * pricing["stepNumerator"] + pricing["stepDenominator"] // 2) // pricing["stepDenominator"]
    return total


def amount(formula, rank, stats):
    extra_rank = rank - 1
    return (formula["base"] + extra_rank * formula["rankGrowth"]
            + (formula["scale"] + extra_rank * formula.get("scaleGrowth", 0)) * stats[formula["scaling"]]
            ) * formula.get("weight", 1)


@dataclass(eq=False)
class Combatant:
    stats: dict
    hp: float
    position: float = 0
    damage: list = field(default_factory=list)
    controls: list = field(default_factory=list)
    shields: dict = field(default_factory=dict)
    last_hit: int = -100
    healed: float = 0
    absorbed: float = 0
    passive_ready: int = 0

    def mitigation(self, kind):
        defense = {"physical": "physicalDefense", "magic": "magicDefense"}.get(kind)
        return 100 / (100 + max(0, self.stats[defense])) if defense else 1


class Replay:
    def __init__(self, rules, defaults, config, combination, opponent, delay, script=None, probe=None, affected=1,
                 omit=False, omitted_slot=None):
        roles = named(rules["roles"])
        self.config = config
        self.script = script
        self.probe = probe
        self.omit = omit
        self.affected = affected
        self.defaults = defaults
        self.initial = {**defaults["startState"], **config["startState"], **(script or {}).get("startState", {})}
        self.opponent = {**defaults["opponents"][opponent], **(script or {}).get("opponentOverrides", {})}
        self.delay = defaults["delayProfiles"][delay]
        self.windows = defaults["windows"]
        self.end = self.windows["sustained"]
        self.own = roles[config["role"]]
        self.skills = named(self.own["skills"])
        self.selected = dict(zip(SLOTS, combination, strict=True))
        self.omitted_slot = omitted_slot
        self.level = self.initial["heroLevel"]
        self.ranks = {key: self.initial.get("skillRanks", {}).get(slot, self.skills[key]["rank"]["value"])
                      for slot, key in self.selected.items()}
        if sum(self.ranks[key] for key in combination[1:]) > self.level:
            raise ValueError("Learned ranks exceed the skill point budget")
        for key, rank in self.ranks.items():
            ranks = self.skills[key]["ranks"]
            if (not any(row["rank"] == rank and row["earliestHeroLevel"]["value"] <= self.level for row in ranks)
                    or self.skills[key]["category"] == "passive" and rank != sum(self.level >= n for n in (1, 4, 7, 10, 13))):
                raise ValueError(f"Illegal learned rank {key}/{rank} at level {self.level}")
        bonuses = rules["attributePurchases"]["permanentGain"]["value"]
        self.pricing = rules["attributePurchases"]["pricing"]["value"]
        for key in ("purchases", "targetPurchases"):
            if purchase_cost(self.pricing, self.initial[key]) > self.initial["budgetGold"]:
                raise ValueError(f"Over-budget configuration {config['scenarioId']}/{key}")
        self.stats = attributes(self.own, self.level, self.initial["purchases"], bonuses)
        reference = roles[self.initial["targetRole"]]
        self.target_stats = attributes(reference, self.level, self.initial["targetPurchases"], bonuses)
        self.haste = rules["skillRules"]["skillHaste"]["value"]
        self.availability = rules["battlefield"]["controls"]["basicActionAvailability"]
        self.mana = self.stats["mana"] * self.initial["manaRatio"]
        self.enemies = [Combatant(self.target_stats, self.target_stats["health"], self.initial["distance"])
                        for _ in range(affected if probe and probe.get("beneficiaries") != "allies" else 1)]
        self.allies = []
        pressure = defaults["supportTemplates"]
        for name in pressure:
            level_stats = self.target_stats
            self.allies.append(Combatant(level_stats, level_stats["health"] * pressure[name]["hpRatio"]))
        self.caster = Combatant(self.stats, self.stats["health"] * self.initial["hpRatio"])
        self.execution = script["execution"] if script else config["execution"]
        self.actions = self.execution["steps"]
        self.tick = self.cursor = self.action_progress = self.input_ready = self.basic_ready = 0
        self.position = 0
        self.prepositioned_behind = self.initial["facing"] == "behind"
        self.ready = {}
        self.events, self.queue, self.waits = [], [], {}
        self.action_queue = []
        self.conditions = {**self.initial, **self.initial.get("conditions", {})}
        self.tiebreak = count()
        self.pending = self.channel = self.motion = self.draw = None
        self.recasts = {}
        self.field_token = {}
        self.marks = {}
        self.engraving = {}
        self.engraving_seen = set()
        self.herbal_stacks = 0
        self.herbal_last = -100
        self.herbal_token = None
        self.constant_step_ready = 0
        self.hit_labels = {}
        self.quick_until = 0
        self.last_action_tick = 0

    def value(self, skill, source):
        if not isinstance(source, str):
            return source
        field = effect(self.skills[skill], source)
        return field[self.ranks[skill] - 1] if isinstance(field, list) else field

    def record(self, event, **data):
        self.events.append({"tick": self.tick, "event": event, **data})

    def later(self, delay, fn, *args):
        heappush(self.queue, (self.tick + delay, next(self.tiebreak), fn, args))

    def after_action(self, delay, fn, *args):
        heappush(self.action_queue, (self.action_progress + delay, next(self.tiebreak), fn, args))

    def selected_passive(self):
        return None if self.omitted_slot == "passive" else self.selected["passive"]

    def distance(self, enemy=None):
        return abs((enemy or self.enemies[0]).position - self.position)

    def cooldown(self, skill):
        rank = self.ranks[skill]
        row = next(row for row in self.skills[skill]["ranks"] if row["rank"] == rank)
        base = row["cooldown"]["value"]
        if base <= 0:
            return base
        scale = 1 + self.haste["perPurchase"] * self.stats["cooldownPurchases"] / self.haste["denominatorBase"]
        return max(self.haste["minPositiveTicks"], floor(base / scale))

    def mana_cost(self, skill):
        return next(row["mana"]["value"] for row in self.skills[skill]["ranks"] if row["rank"] == self.ranks[skill])

    def loss(self, target, tick, include_skill=True):
        blocked = set()
        rate = 1
        movement_rate = 1
        if target is self.enemies[0] and tick < self.opponent["stunTicks"]:
            blocked.update(self.defaults["controlActionWeights"])
        for start, end, kind, value, _ in (target.controls if include_skill else ()):
            if not start <= tick < end:
                continue
            if kind == "slow":
                rate = min(rate, value)
            elif kind == "movement_slow":
                movement_rate = min(movement_rate, value)
            else:
                blocked.update(key for key, available in self.availability[kind]["value"].items() if not available)
        weights = self.defaults["controlActionWeights"]
        return 1 - sum(weight * (min(rate, movement_rate) if key == "move" else rate)
                       for key, weight in weights.items() if key not in blocked)

    def control(self, target, kind, duration, source, rate=0):
        if duration <= 0 or target.hp <= 0:
            return
        target.controls.append((self.tick, self.tick + duration, kind, rate, source))
        self.record("control", skill=source, target=self.enemies.index(target), control=kind, rate=rate,
                    untilTick=self.tick + duration)

    def heal(self, target, nominal, skill, trigger=True):
        if target.hp <= 0:
            return
        effective = min(nominal, target.stats["health"] - target.hp)
        target.hp += effective
        target.healed += effective
        self.record("heal", skill=skill, nominalHp=nominal, effectiveHp=effective, hpLeft=target.hp)
        if trigger and effective > 0:
            self.support_passive(target)

    def shield(self, target, nominal, duration, skill):
        if target.hp <= 0:
            return
        old, end = target.shields.get(skill, (0, 0))
        old = old if end > self.tick else 0
        total = max(nominal, old)
        target.shields[skill] = total, self.tick + duration
        self.record("shield", skill=skill, nominalHp=nominal, activeShieldHp=total, untilTick=self.tick + duration)
        if total > old:
            self.support_passive(target)

    def support_passive(self, target):
        skill = self.selected_passive()
        if skill == "g2_qingluo_pa" and target.hp < 0.6 * target.stats["health"] and self.tick >= target.passive_ready:
            target.passive_ready = self.tick + 80
            self.heal(target, amount(effect(self.skills[skill], "heal"), self.ranks[skill], self.stats), skill, False)
        if skill == "g2_qingluo_pb" and self.herbal_stacks:
            stacks, self.herbal_stacks = self.herbal_stacks, 0
            self.heal(target, stacks * amount(effect(self.skills[skill], "heal"), self.ranks[skill], self.stats),
                      skill, False)

    def incoming(self, target, kind, raw):
        if target.hp <= 0:
            return
        left = raw * target.mitigation(kind)
        absorbed = 0
        for source, (value, end) in sorted(target.shields.items(), key=lambda item: item[1][1]):
            if self.tick >= end:
                continue
            used = min(left, value)
            target.shields[source] = value - used, end
            absorbed += used
            left -= used
        target.absorbed += absorbed
        health_loss = min(target.hp, left)
        target.hp -= health_loss
        label = f"ally_{self.allies.index(target) + 1}" if any(target is ally for ally in self.allies) else "self"
        self.record("incoming", target=label, damageKind=kind, rawHp=raw, absorbedHp=absorbed,
                    healthLossHp=health_loss, hpLeft=target.hp)

    def damage(self, enemy, raw, kind, skill, label=None, rapid=False, indirect=False, token=None):
        if enemy.hp <= 0:
            return False
        dealt = min(enemy.hp, raw * enemy.mitigation(kind))
        if not rapid and kind != "true" and self.tick - enemy.last_hit < 3:
            self.record("hurt_recovery", skill=skill, label=label, target=self.enemies.index(enemy))
            return False
        passive = self.selected_passive()
        if passive == "g1_yingxiao_pa" and not indirect and (self.prepositioned_behind or skill == "g1_yingxiao_ua"):
            dealt = min(enemy.hp, dealt * (1 + (10 + 5 * (self.ranks[passive] - 1)) / 100))
        enemy.hp -= dealt
        enemy.damage.append((self.tick, dealt))
        if label:
            self.hit_labels[label] = self.tick
        if not rapid and kind != "true":
            enemy.last_hit = self.tick
        self.record("hit", skill=skill, label=label, target=self.enemies.index(enemy), damageKind=kind,
                    effectiveHp=dealt, hpLeft=enemy.hp)
        if not indirect:
            self.on_hit(enemy, skill, passive, token)
        return True

    def on_hit(self, enemy, skill, passive, token):
        target = self.enemies.index(enemy)
        if skill == "luoxia_dusk_mark":
            self.marks[target] = self.tick + 60
        elif self.marks.get(target, -1) > self.tick and (skill == "basic" or skill.startswith("luoxia_")):
            del self.marks[target]
            mark = "luoxia_dusk_mark"
            self.formula_hit(enemy, mark, "mark_pop", indirect=True)
        if skill == "basic" and passive == "luoxia_chase_dusk":
            self.quick_until = self.tick + 24
        if passive == "g1_yingxiao_pb" and len(self.enemies) == 1 and self.tick >= enemy.passive_ready:
            enemy.passive_ready = self.tick + 160
            self.formula_hit(enemy, passive, "damage", indirect=True)
        if passive == "keheng_kehen" and token is not None:
            seen, expires = self.engraving.get(target, (set(), -1))
            if (target, token) not in self.engraving_seen:
                self.engraving_seen.add((target, token))
                if self.tick >= expires:
                    seen = set()
                    expires = self.tick + effect(self.skills[passive], "mark_window")
                seen.add(token)
                self.engraving[target] = seen, expires
                if len(seen) == effect(self.skills[passive], "marks_to_trigger"):
                    del self.engraving[target]
                    self.formula_hit(enemy, passive, "damage", indirect=True)
        if passive == "g2_qingluo_pb" and self.tick - self.herbal_last >= 4:
            if token is None or token != self.herbal_token:
                self.herbal_stacks = min(3, self.herbal_stacks + 1)
                self.herbal_last = self.tick
                self.herbal_token = token

    def formula_hit(self, enemy, skill, name, **kwargs):
        formula = effect(self.skills[skill], name)
        return self.damage(enemy, amount(formula, self.ranks[skill], self.stats), formula["kind"], skill, **kwargs)

    def begin(self, skill, step):
        data = self.config["skills"][skill]
        self.pending = None
        self.input_ready = self.tick + self.value(skill, data.get("inputRecovery", 3))
        self.record("input_accepted", skill=skill, label=step.get("label"))
        if data.get("prepayWindup"):
            self.start_windup(skill, step, data["prepayWindup"], paid=False)
        else:
            self.commit(skill, step)

    def start_windup(self, skill, step, work, paid):
        self.channel = (skill, step, self.action_progress + self.value(skill, work), paid)
        self.record("windup", skill=skill, paid=paid, actionTicks=self.value(skill, work))
        if self.config["skills"][skill].get("preWindupSlow"):
            self.control(self.enemies[0], "slow", self.value(skill, work), skill,
                         self.config["skills"][skill]["preWindupSlow"])

    def commit(self, skill, step):
        cost = self.mana_cost(skill)
        if cost > self.mana:
            self.record("rejected", skill=skill, reason="mana_at_commit")
            return
        self.mana -= cost
        self.ready[skill] = self.tick + self.cooldown(skill)
        self.record("committed", skill=skill, label=step.get("label"), manaCost=cost, manaLeft=self.mana,
                    cooldownEndTick=self.ready[skill])
        work = self.config["skills"][skill].get("paidWindup")
        if work:
            self.start_windup(skill, step, work, paid=True)
        else:
            self.release(skill, step)

    def release(self, skill, step):
        data = self.config["skills"][skill]
        self.record("released", skill=skill, label=step.get("label"))
        token = (skill, self.tick)
        if data.get("motion"):
            self.motion = (skill, self.action_progress + self.value(skill, data["motion"]))
        passive = self.selected_passive()
        if (passive == "keheng_hengbu" and skill in ("keheng_cuopaibu", "keheng_suixing")
                and self.tick >= self.constant_step_ready):
            self.constant_step_ready = self.tick + effect(self.skills[passive], "internal_cooldown")
            value = amount(effect(self.skills[passive], "shield"), self.ranks[passive], self.stats)
            self.shield(self.caster, value, effect(self.skills[passive], "shield_duration"), passive)
        if data.get("recast"):
            self.recasts[skill] = (self.tick + self.value(skill, data["recastWindow"]), self.position)
            self.field_token[skill] = token
        for row in data["events"]:
            at = self.value(skill, row.get("at", 0))
            schedule = self.after_action if row.get("clock") == "action_tick" else self.later
            if "repeat" in row:
                period, duration = (self.value(skill, x) for x in row["repeat"])
                for elapsed in range(period, duration + 1, period):
                    schedule(at + elapsed, self.apply, skill, row, token, elapsed, step.get("label"))
            else:
                schedule(at, self.apply, skill, row, token, 0, step.get("label"))

    def impact(self, skill, row, enemy, index, token, label):
        if row.get("perTargetEffect"):
            name = row["perTargetEffect"][index]
        else:
            name = row["effect"]
        hit = self.formula_hit(enemy, skill, name, token=token, label=label, rapid=row.get("rapid", False))
        for kind in ("root", "stun", "silence"):
            if hit and kind in row:
                self.control(enemy, kind, self.value(skill, row[kind]), skill)
        if hit and "movementSlow" in row:
            rate, duration = (self.value(skill, x) for x in row["movementSlow"])
            self.control(enemy, "movement_slow", duration, skill, rate)
        if hit and "slow" in row:
            rate, duration = (self.value(skill, x) for x in row["slow"])
            self.control(enemy, "slow", duration, skill, rate)

    def apply(self, skill, row, token, elapsed, label):
        if self.config["skills"][skill].get("recast") == "cancel_follow" and self.field_token.get(skill) != token:
            return
        if not self.meets(row.get("requires")):
            return
        kind = row["kind"]
        if kind == "position":
            enemy = self.enemies[0]
            if row.get("behind"):
                self.position = enemy.position - row["distance"] if self.prepositioned_behind else enemy.position + row["distance"]
                self.prepositioned_behind = True
            elif skill in ("g1_yingxiao_ma", "g1_yingxiao_mb"):
                self.position += self.config["skills"][skill]["range"]
                self.prepositioned_behind = not self.prepositioned_behind
            elif "distance" in row:
                self.position += self.value(skill, row["distance"])
            else:
                self.position += effect(self.skills[skill], "dash_range")
            self.record("position", skill=skill, distanceBlocks=self.distance())
            return
        if kind in ("heal", "shield"):
            target = row["target"]
            targets = [self.caster] if target == "self" else self.allies[:self.affected if target == "allies" else 1]
            nominal = amount(effect(self.skills[skill], row["effect"]), self.ranks[skill], self.stats)
            for ally in targets:
                if kind == "heal":
                    self.heal(ally, nominal, skill)
                else:
                    self.shield(ally, nominal, self.value(skill, row["duration"]), skill)
            return
        enemies = self.enemies if row.get("area") or row.get("perTargetEffect") else self.enemies[:1]
        for index, enemy in enumerate(enemies):
            radius = self.value(skill, row.get("radius"))
            if radius is not None and self.distance(enemy) > radius:
                continue
            stay = self.probe["fieldStayTicks"][index] if self.probe else None
            if stay is not None and elapsed > stay:
                continue
            if kind == "control":
                self.control(enemy, row["control"], self.value(skill, row["duration"]), skill)
            elif kind == "slow":
                duration = self.value(skill, row["duration"])
                if stay is not None and row.get("area"):
                    duration = min(duration, stay)
                self.control(enemy, "slow", duration, skill, self.value(skill, row["rate"]))
            else:
                speed = self.value(skill, row.get("speed"))
                due = ceil(max(0.01, self.distance(enemy) - self.initial["targetCollisionDistanceAllowance"]) / speed) if speed else 0
                self.later(due, self.impact, skill, row, enemy, index, token, label)

    def basic(self, step):
        row = self.own["basicAttack"]
        data = self.config["basicAttack"]
        mode = row["id"]
        reach = data.get("range") or row.get("range", row.get("drawRange", {}))["value"]
        allowed = not self.channel and not self.draw and (not self.motion
                  or self.config["skills"][self.motion[0]].get("basicDuringMotion"))
        if not allowed or self.action_progress < self.basic_ready or self.distance() > reach:
            return False
        label = step.get("label")
        if mode == "bow":
            passive = self.selected_passive()
            quick = passive == "luoxia_chase_dusk" and self.tick < self.quick_until
            full = passive == "luoxia_steady_dusk" and not quick
            work = 20 if full else 11 - 0.4 * self.ranks[passive] if quick else row["drawWork"]["value"]
            self.draw = self.action_progress + work * self.stats["innateAttackSpeed"] / self.stats["attackSpeed"], label, quick, full
            self.record("bow_draw", label=label, actionTicks=work)
            return True
        self.basic_release(label, False, False)
        return True

    def basic_release(self, label, quick, full):
        row = self.own["basicAttack"]
        mode = row["id"]
        data = self.config["basicAttack"]
        speed = data.get("flightSpeed")
        enemy = self.enemies[0]
        self.record("basic_release", label=label, mode="full" if full else "quick" if quick else mode)
        if mode == "bow":
            passive = self.selected_passive()
            raw = amount(effect(self.skills[passive], "damage"), self.ranks[passive], self.stats) if quick or full else self.stats["total_physical"]
            reach = data["overdrawRange"] if full else row["drawRange"]["value"]
            speed = data["overdrawSpeed"] if full else speed
            self.quick_until = 0
        elif mode == "magic_bullet":
            raw = row["rawDamage"]["value"] + row["spellScaling"]["value"] * self.stats["bonus_spell"]
            reach = row["range"]["value"]
            self.basic_ready = self.action_progress + ceil(20 / self.stats["attackSpeed"])
        else:
            raw = self.stats["total_physical"]
            reach = data.get("range", row["range"].get("value"))
            self.basic_ready = self.action_progress + ceil(20 / self.stats["attackSpeed"])
        if self.distance() <= reach:
            flight = ceil(max(0.01, self.distance() - self.initial["targetCollisionDistanceAllowance"]) / speed) if speed else 0
            kind = "magic" if mode == "magic_bullet" else "physical"
            self.later(flight, self.damage, enemy, raw, kind, "basic", label)

    def meets(self, conditions):
        return all(self.conditions.get(key, False) == expected for key, expected in (conditions or {}).items())

    def execute(self, step):
        action = step["action"]
        skill = self.selected.get(step.get("skill"), step.get("skill"))
        if not self.meets(step.get("requires")):
            return False
        if action == "wait_until":
            limit = step.get("maxTicks")
            if limit is None:
                return True
            start = self.waits.setdefault(self.cursor, self.tick)
            condition = step["condition"]
            if condition.startswith("hurt_ready:"):
                label = condition.split(":", 1)[1]
                met = label in self.hit_labels and self.tick - self.hit_labels[label] >= 3
            else:
                met = {"channel_finished": self.channel is None, "motion_finished": self.motion is None}[condition]
            if met or self.tick - start >= limit:
                self.record("wait_until", condition=condition, met=met,
                            waitedTicks=self.tick - start)
                return True
            return False
        if action == "basic_attack":
            return self.basic(step)
        if (skill not in self.selected.values() or skill not in self.config["skills"]
                or self.omitted_slot is not None and self.selected[self.omitted_slot] == skill):
            return False
        if action in ("recast", "cancel"):
            if action == "cancel" and self.pending and self.pending[0] == skill:
                self.pending = None
                self.record("cancelled", skill=skill, channel="placement", paid=False)
                return True
            saved = self.recasts.get(skill)
            if not saved or self.tick >= saved[0] or self.channel or self.motion:
                return False
            recast = self.config["skills"][skill]["recast"]
            if recast == "cancel_follow":
                self.field_token.pop(skill, None)
                for enemy in self.enemies:
                    enemy.controls = [(start, min(end, self.tick) if source == skill else end, kind, rate, source)
                                      for start, end, kind, rate, source in enemy.controls]
            elif action == "recast":
                self.position = saved[1]
            else:
                return False
            del self.recasts[skill]
            self.record("cancelled" if action == "cancel" else "recast", skill=skill, channel=recast, manaCost=0)
            return True
        if action != "cast":
            raise ValueError(f"Unknown action {action}")
        if self.channel or self.motion or self.pending or self.tick < self.input_ready or self.tick < self.ready.get(skill, 0):
            return False
        data = self.config["skills"][skill]
        if data.get("omitted") or not self.meets(data.get("requires")) or self.mana_cost(skill) > self.mana:
            return False
        reach = self.value(skill, data.get("range"))
        if reach is not None and self.distance() > reach:
            return False
        if self.draw is not None:
            if self.config["role"] != "luoxia" or self.skills[skill]["category"] == "w":
                return False
            self.draw = None
            self.record("cancelled", skill="basic", channel="bow_draw", paid=False)
        if data.get("point"):
            self.record("point_requested", skill=skill)
            if self.delay["point"]:
                self.pending = (skill, step, self.tick + self.delay["point"])
                return True
            self.record("point_confirmed", skill=skill)
        self.begin(skill, step)
        return True

    def advance(self):
        rates = self.initial.get("casterActionRate", 1)
        self.action_progress += (next((rate for start, end, rate in rates if start <= self.tick < end), 1)
                                 if isinstance(rates, list) else rates)
        if self.motion and self.action_progress >= self.motion[1]:
            self.motion = None
        if self.pending and self.tick >= self.pending[2]:
            skill, step, _ = self.pending
            reach = self.value(skill, self.config["skills"][skill].get("range"))
            if reach is not None and self.distance() > reach:
                self.record("rejected", skill=skill, reason="outside_range_at_confirmation")
                self.pending = None
            else:
                self.record("point_confirmed", skill=skill)
                self.begin(skill, step)
        if self.channel and self.action_progress >= self.channel[2]:
            skill, step, _, paid = self.channel
            self.channel = None
            self.release(skill, step) if paid else self.commit(skill, step)
        if self.draw and self.action_progress >= self.draw[0]:
            _, label, quick, full = self.draw
            self.draw = None
            self.basic_release(label, quick, full)

    def act(self):
        if self.caster.hp <= 0 or self.enemies[0].hp <= 0:
            return
        if self.probe:
            if self.tick == 0 and not self.omit:
                self.execute({"action": "cast", "skill": self.probe["skill"]})
            return
        if self.execution["type"] == "apl":
            if (self.config.get("checkActionsDuringChannel")
                    or not self.channel and not self.draw and not self.pending):
                chosen = next((step for step in self.actions
                               if (not step.get("judgment") or self.tick >= self.last_action_tick + self.delay["judgment"])
                               and self.execute(step)), None)
                if chosen and chosen["action"] != "wait_until":
                    self.last_action_tick = self.tick
        elif self.cursor < len(self.actions) and not self.pending:
            step = self.actions[self.cursor]
            delay = self.delay["judgment"] if step.get("judgment") or self.cursor > 0 else 0
            if self.tick >= self.last_action_tick + delay and self.execute(step):
                self.cursor += 1
                self.last_action_tick = self.tick

    def process_events(self):
        while True:
            if self.action_queue and self.action_queue[0][0] <= self.action_progress:
                _, _, fn, args = heappop(self.action_queue)
            elif self.queue and self.queue[0][0] <= self.tick:
                _, _, fn, args = heappop(self.queue)
            else:
                break
            fn(*args)

    def run(self):
        for name, ally in zip(self.defaults["supportTemplates"], self.allies, strict=True):
            for tick, kind, raw in self.defaults["supportTemplates"][name]["incoming"]:
                self.later(tick, self.incoming, ally, kind, raw)
        for tick, kind, raw in self.initial.get("casterIncoming", self.defaults["supportTemplates"]["secondary"]["incoming"]):
            self.later(tick, self.incoming, self.caster, kind, raw)
        for self.tick in range(self.end):
            if self.tick:
                self.mana = min(self.stats["mana"], self.mana + self.stats["manaRegen"] / 20)
                self.advance()
            self.process_events()
            self.act()
            self.process_events()
            for enemy in self.enemies:
                if any(start <= self.tick < end and kind in ("stun", "root") for start, end, kind, _, _ in enemy.controls):
                    continue
                if enemy is self.enemies[0] and self.tick < self.opponent["stunTicks"]:
                    continue
                rate = min([value for start, end, kind, value, _ in enemy.controls
                            if kind in ("slow", "movement_slow") and start <= self.tick < end], default=1)
                enemy.position += rate * self.opponent["retreatPerActionTick"]
        return self

    def net_control(self, target):
        return sum(self.loss(target, tick) - self.loss(target, tick, include_skill=False)
                   for tick in range(self.end)) / 20

    def result(self):
        primary = self.enemies[0]
        support = self.allies[0] if self.config["role"] == "qingluo" else self.caster
        return {
            "burstHp": sum(value for tick, value in primary.damage if tick < self.windows["burst"]),
            "sustainedHpPerSecond": sum(value for _, value in primary.damage) / (self.end / 20),
            "controlActionSeconds": self.net_control(primary),
            "effectiveSupportHp": support.healed + support.absorbed,
            "actualHealingHp": support.healed, "actualAbsorbedHp": support.absorbed,
            "completedActions": self.cursor if self.execution["type"] == "scripted" else None,
            "actionCount": len(self.actions) if self.execution["type"] == "scripted" else None,
        }


def distributions(conditional):
    first, second, third = conditional
    return [1 - first, first * (1 - second), first * second * (1 - third), first * second * third]


def multi_metrics(replay, weights):
    enemies = zip(replay.enemies, weights)
    burst = sustained = control = 0
    for enemy, weight in enemies:
        burst += weight * sum(value for tick, value in enemy.damage if tick < replay.windows["burst"])
        sustained += weight * sum(value for _, value in enemy.damage) / (replay.end / 20)
        control += replay.net_control(enemy)
    targets = replay.allies[:replay.affected]
    return {"burstHp": burst, "sustainedHpPerSecond": sustained, "controlActionSeconds": control,
            "effectiveSupportHp": sum(ally.healed + ally.absorbed for ally in targets),
            "actualHealingHp": sum(ally.healed for ally in targets),
            "actualAbsorbedHp": sum(ally.absorbed for ally in targets)}


def subtract(current, baseline):
    return {key: current[key] - baseline[key] for key in current}


def multi_target(rules, defaults, configs, scenarios):
    rows = []
    roles = named(rules["roles"])
    for definition in scenarios["probes"]:
        role = definition["role"]
        slot = SLOTS.index(definition["slot"])
        combination = next(row for row in roles[role]["legalBranches"]["terminalCombinations"]
                           if row[slot] == definition["skill"])
        for name, case in scenarios["scenes"].items():
            probabilities = distributions(case["p"][definition["size"]])
            probe = {**definition, "fieldStayTicks": case["fieldStayTicks"]}
            outcomes = []
            for affected in range(1, 4):
                inputs = (rules, defaults, configs[role], combination, case["opponent"], "human_default")
                with_skill = Replay(*inputs, probe=probe, affected=affected).run()
                without = Replay(*inputs, probe=probe, affected=affected, omit=True).run()
                raw_base = multi_metrics(without, [1, 1, 1])
                raw = subtract(multi_metrics(with_skill, [1, 1, 1]), raw_base)
                weighted = subtract(multi_metrics(with_skill, scenarios["focusWeights"]), raw_base)
                outcomes.append({"affected": affected, "raw": raw, "adjusted": weighted,
                                 "withSkill": with_skill.events, "withoutSkill": without.events})
            expected = {key: sum(probabilities[pos] * row["adjusted"][key] for pos, row in enumerate(outcomes, 1))
                        for key in outcomes[0]["adjusted"]}
            primary = {key: (1 - probabilities[0]) * value for key, value in outcomes[0]["adjusted"].items()}
            href = with_skill.target_stats["health"] / 100
            rows.append({"role": role, "skill": definition["skill"], "scene": name, "sceneWeight": case["weight"],
                         "combination": combination, "distribution": probabilities, "referenceHpPerBudgetPoint": href,
                         "firstTarget": primary, "additional": subtract(expected, primary),
                         "expected": expected, "outcomes": outcomes})
    return rows


def aggregate_multi_target(rows):
    values = defaultdict(lambda: {key: dict.fromkeys(METRICS, 0.0)
                                 for key in ("firstTarget", "additional", "expected")})
    for row in rows:
        for attribution, metrics in values[row["skill"]].items():
            for metric in METRICS:
                metrics[metric] += row["sceneWeight"] * row[attribution][metric]
    return dict(values)


def static_skill(skill, stats):
    covered = Counter(row["rule"]["status"] for row in skill["effects"])
    fields = []
    for effect_row in skill["effects"]:
        rule = effect_row["rule"]
        row = {"id": effect_row["id"], "type": effect_row["type"], **rule}
        if row["type"] in ("damage", "shield", "heal") and rule["status"] in ("defined", "zero"):
            formula = rule["value"]
            if isinstance(formula, dict) and "scaling" in formula:
                row["nominalRankSamples"] = [
                    amount(formula, rank["rank"], stats) for rank in skill["ranks"] if rank["rank"] > 0
                ]
        fields.append(row)
    return {"id": skill["id"], "category": skill["category"], "definition": skill["implementation"],
            "availability": skill["availability"], "rankInputs": skill["ranks"],
            "coverage": dict(covered), "effects": fields}


def read_scenarios():
    configs = {role: load(SCENARIOS / f"{role}.json") for role in ("keheng", "luoxia", "qingluo", "yingxiao")}
    return load(SCENARIOS / "_defaults.json"), configs, load(SCENARIOS / "multi-target.json")


def verify_combinations(role):
    legal = role["legalBranches"]
    combinations = legal["terminalCombinations"]
    expected = {(p, *legal["common"], e, r) for p in legal["exclusive"]["passive"]
                for e in legal["exclusive"]["e"] for r in legal["exclusive"]["r"]}
    if len(combinations) != 8 or expected != {tuple(row) for row in combinations}:
        raise ValueError(f"Unexpected terminal branch definitions in {role['id']}")
    return combinations


def case_report(rules, defaults, config, combination, mode, delay, script=None, replay_type=Replay):
    replay = replay_type(rules, defaults, config, combination, mode, delay, script=script).run()
    result = replay.result()
    abilities = set(combination)
    gaps = {**config["coverageGaps"], **(script or {}).get("coverageGaps", {})}
    if any(row["event"] == "wait_until" and not row["met"] for row in replay.events):
        gaps["wait"] = {"axes": ["damage", "control", "support"], "reason": "至少一个等待条件到时仍未满足。"}
    if not config.get("zeroContactIsObservable") and not any(row["event"] == "hit" for row in replay.events):
        gaps["contact"] = {"axes": ["damage"], "reason": "该场景在观察窗内没有模型内合法命中。"}
    result.update({"events": replay.events, "combination": combination, "ranks": replay.ranks, "opponent": mode,
                   "delay": delay, "initial": replay.initial, "actorStats": replay.stats,
                   "targetStats": replay.target_stats, "initialGoldSpent": purchase_cost(replay.pricing, replay.initial["purchases"]),
                   "incomingHealthLostHp": sum(row.get("healthLossHp", 0) for row in replay.events
                                               if row["event"] == "incoming" and row["target"] == "self"),
                   "coverageGaps": {key: gap for key, gap in gaps.items() if key in abilities or key in ("contact", "wait")}})
    return result


def run_role(rules, defaults, config, role, replay_type=Replay):
    combinations = verify_combinations(role)
    delays = defaults["delayProfiles"]
    opponents = defaults["opponents"]
    cases = [case_report(rules, defaults, config, combination, mode, delay, replay_type=replay_type)
             for combination in combinations for mode in opponents for delay in delays]
    result = {mode: {delay: {} for delay in delays} for mode in opponents}
    for mode, profiles in result.items():
        for delay, metrics in profiles.items():
            selected = [row for row in cases if (row["opponent"], row["delay"]) == (mode, delay)]
            for metric in METRICS:
                low, high = min(selected, key=lambda row: row[metric]), max(selected, key=lambda row: row[metric])
                metrics[metric] = {"min": low[metric], "minCombination": low["combination"],
                                   "max": high[metric], "maxCombination": high["combination"],
                                   "complete": False}
    scripts = []
    for script in config["scripted"]:
        combination = next(row for row in combinations if set(script["applicableBuilds"]).issubset(row))
        scripts.append({"scenario": script, "results": {
            delay: case_report(rules, defaults, config, combination, script["opponentCondition"], delay, script, replay_type)
            for delay in delays}})
    return {
        "scenario": config["scenarioId"], "scenarioVersion": config["scenarioVersion"],
        "static": [static_skill(skill, cases[0]["actorStats"]) for skill in role["skills"]],
        "attributes": role["attributes"], "basicAttack": role["basicAttack"], "combinations": combinations,
        "cases": cases, "envelopeOfModeledReplay": result, "scripted": scripts,
    }


def replay_rules(snapshot):
    rules = snapshot["rules"]
    defaults, configs, multi = read_scenarios()
    roles = named(rules["roles"])
    multi_rows = multi_target(rules, defaults, configs, multi)
    return {"defaults": defaults, "roles": {key: run_role(rules, defaults, config, roles[key])
                                             for key, config in configs.items()},
            "multiTarget": {"config": multi, "observations": multi_rows,
                            "weightedAcrossScenes": aggregate_multi_target(multi_rows)}}
