"""Formation and support attribution for the #113 scenario package."""

from collections import defaultdict
from math import prod

from combo_calibration import METRICS, SLOTS, Replay, amount, distributions, effect, named, subtract


METRIC_AXES = dict(zip(METRICS, ("damage", "damage", "control", "support"), strict=True))


def covers_allies(probe):
    return probe and probe.get("beneficiaries") in ("allies", "both")


class FormationReplay(Replay):
    def __init__(self, rules, defaults, config, combination, opponent, delay, script=None, probe=None,
                 affected=1, omit=False, omitted_slot=None):
        pressure = defaults["supportTemplates"]
        # A 3v3 team already includes the caster. The caster plays the low-pressure
        # seat in the three-recipient probe, while general starts keep full actor HP.
        if covers_allies(probe):
            low = pressure["lowPressure"]
            config = {**config, "startState": {**config["startState"], "hpRatio": low["hpRatio"],
                      "casterIncoming": low["incoming"]}}
        defaults = {**defaults, "supportTemplates": {name: pressure[name] for name in ("primary", "secondary")}}
        super().__init__(rules, defaults, config, combination, opponent, delay, script, probe, affected, omit, omitted_slot)
        for ally, position in zip(self.allies, self.initial.get("allyPositions", [0, 0]), strict=True):
            ally.position = position
        self.guards = defaultdict(dict)
        self.prevented = defaultdict(float)
        self.confirmed_points = {}
        self.formation_points = {}
        self.minimum_guard = rules["skillRules"]["incomingGuardResolution"]["value"]["minimumMultiplier"]

    def target_label(self, target):
        if target is self.caster:
            return "self"
        return ("primary", "secondary")[self.allies.index(target)]

    def commit(self, skill, step):
        self.confirmed_points[skill] = self.enemies[0].position
        super().commit(skill, step)

    def release(self, skill, step):
        self.formation_points[(skill, self.tick)] = self.confirmed_points[skill]
        super().release(skill, step)

    def control(self, target, kind, duration, source, rate=0):
        if kind == "movement_slow" and duration > 0 and target.hp > 0:
            # A modifier with the same skill and owner replaces the previous interval.
            target.controls = [
                (start, self.tick if previous == kind and origin == source and start <= self.tick < end else end,
                 previous, value, origin)
                for start, end, previous, value, origin in target.controls
            ]
        super().control(target, kind, duration, source, rate)

    def movement_rate(self, target, tick):
        # Independent movement modifiers multiply in AbilityRuntime.ordinarySlowRate.
        return prod(value for start, end, kind, value, _ in target.controls
                    if kind == "movement_slow" and start <= tick < end)

    def supports(self, row, token=None, elapsed=0):
        mode = row["target"]
        candidates = [] if self.conditions.get("noAllies") else self.allies
        if row.get("radius") is None:
            in_range = lambda target: True
        else:
            origin = self.formation_points[token] if row.get("center") == "point" else self.position
            radius = self.value(token[0], row["radius"])
            in_range = lambda target: abs((self.position if target is self.caster else target.position) - origin) <= radius
        if mode == "self":
            targets = [self.caster]
        elif mode == "lowest_ally":
            targets = [min((ally for ally in candidates if ally.hp > 0 and in_range(ally)),
                           key=lambda ally: ally.hp / ally.stats["health"], default=self.caster)]
        elif mode == "primary":
            targets = candidates[:1]
        elif mode == "allies":
            if covers_allies(self.probe):
                covered = candidates + ([self.caster] if row.get("includeSelf") else [])
                targets = [target for index, target in enumerate(covered[:self.affected])
                           if elapsed <= self.probe["fieldStayTicks"][index]]
            else:
                covered = candidates if self.initial.get("allRecipients") or row.get("allVisibleAllies") else candidates[:1]
                targets = covered + ([self.caster] if row.get("includeSelf") else [])
        else:
            raise ValueError(f"Unsupported formation target {mode}")
        return [target for target in targets if in_range(target)]

    def apply(self, skill, row, token, elapsed, label):
        kind = row["kind"]
        if kind not in ("heal", "shield", "guard"):
            return super().apply(skill, row, token, elapsed, label)
        if not self.meets(row.get("requires")):
            return
        for target in self.supports(row, token, elapsed):
            if kind == "guard":
                duration = self.value(skill, row["duration"])
                multiplier = self.value(skill, row["multiplier"])
                self.guard(target, multiplier, duration, skill, row.get("channel", "team_guard"))
            else:
                nominal = amount(effect(self.skills[skill], row["effect"]), self.ranks[skill], self.stats)
                if kind == "heal":
                    self.heal(target, nominal, skill)
                else:
                    self.shield(target, nominal, self.value(skill, row["duration"]), skill)

    def heal(self, target, nominal, skill, trigger=True):
        if target.hp <= 0:
            return
        effective = min(nominal, target.stats["health"] - target.hp)
        target.hp += effective
        target.healed += effective
        self.record("heal", skill=skill, target=self.target_label(target), nominalHp=nominal,
                    effectiveHp=effective, overhealHp=nominal - effective, hpLeft=target.hp)
        if trigger and effective > 0:
            self.support_passive(target)

    def shield(self, target, nominal, duration, skill):
        if target.hp <= 0:
            return
        old, end = target.shields.get(skill, (0, 0))
        old = old if end > self.tick else 0
        current = max(nominal, old)
        target.shields[skill] = current, self.tick + duration
        self.record("shield", skill=skill, target=self.target_label(target), nominalHp=nominal,
                    previousShieldHp=old, newShieldHp=current - old, activeShieldHp=current,
                    untilTick=self.tick + duration)
        if current > old:
            self.support_passive(target)

    def guard(self, target, multiplier, duration, skill, channel="team_guard"):
        if target.hp <= 0:
            return
        self.guards[target][skill] = channel, multiplier, self.tick + duration
        self.record("guard", skill=skill, target=self.target_label(target), multiplier=multiplier,
                    channel=channel, untilTick=self.tick + duration)

    def incoming(self, target, kind, raw):
        if target.hp <= 0:
            return
        channels = {}
        if kind != "true":
            for skill, (channel, multiplier, until) in self.guards[target].items():
                if self.tick < until and multiplier < channels.get(channel, (1, None))[0]:
                    channels[channel] = multiplier, skill
        multiplier = 1
        for rate, _ in channels.values():
            multiplier *= rate
        multiplier = max(self.minimum_guard, multiplier)
        after_resistance = raw * target.mitigation(kind)
        guarded = after_resistance * multiplier
        active = sorted(((skill, value, end) for skill, (value, end) in target.shields.items()
                         if end > self.tick and value > 0), key=lambda row: row[2])
        damageable = target.hp + sum(value for _, value, _ in active)
        prevented = min(after_resistance, damageable) - min(guarded, damageable)
        left = guarded
        absorbed = {}
        for skill, value, end in active:
            used = min(left, value)
            absorbed[skill] = used
            target.shields[skill] = value - used, end
            left -= used
        health_loss = min(target.hp, left)
        target.hp -= health_loss
        target.absorbed += sum(absorbed.values())
        self.prevented[target] += prevented
        self.record("incoming", target=self.target_label(target), damageKind=kind, rawHp=raw,
                    afterResistanceHp=after_resistance, guardMultiplier=multiplier,
                    guardSources={channel: skill for channel, (_, skill) in channels.items()}, guardEffectiveHp=prevented,
                    absorbedBySourceHp=absorbed, absorbedHp=sum(absorbed.values()), healthLossHp=health_loss, hpLeft=target.hp)

    def net_control(self, target):
        end = target.damage[-1][0] + 1 if target.hp <= 0 and target.damage else self.end
        return sum(self.loss(target, tick) - self.loss(target, tick, include_skill=False)
                   for tick in range(end)) / 20

    def support_reading(self, target):
        return {"effectiveSupportHp": target.healed + target.absorbed + self.prevented[target],
                "actualHealingHp": target.healed, "actualAbsorbedHp": target.absorbed,
                "guardContributionHp": self.prevented[target], "hpLeft": target.hp}

    def result(self):
        result = super().result()
        target = self.allies[0] if self.config.get("supportTarget") == "primary" else self.caster
        return {**result, **self.support_reading(target), "supportReference": self.target_label(target),
                "supportByRecipient": {self.target_label(ally): self.support_reading(ally)
                                       for ally in [*self.allies, self.caster]}}


def team_reading(replay, targets, weights):
    offense = []
    for enemy, weight in zip(replay.enemies, weights[:len(replay.enemies)], strict=True):
        offense.append({"burstHp": weight * sum(value for tick, value in enemy.damage if tick < replay.windows["burst"]),
                        "sustainedHpPerSecond": weight * sum(value for _, value in enemy.damage) / (replay.end / 20),
                        "controlActionSeconds": replay.net_control(enemy)})
    support = {replay.target_label(target): replay.support_reading(target) for target in targets}
    totals = {key: sum(row[key] for row in offense) for key in offense[0]}
    for key in ("actualHealingHp", "actualAbsorbedHp", "guardContributionHp", "effectiveSupportHp"):
        totals[key] = sum(row[key] for row in support.values())
    return totals, offense, support


def multi_target_formation(rules, defaults, configs, scenarios, replays):
    roles = named(rules["roles"])
    rows = []
    for definition in scenarios["probes"]:
        role, skill = definition["role"], definition["skill"]
        combos = [combo for combo in roles[role]["legalBranches"]["terminalCombinations"] if skill in combo]
        for combination in combos if definition.get("compareBranch") else combos[:1]:
            rows.extend(formation_scene(rules, defaults, configs[role], scenarios, replays[role],
                                        definition, combination, name, scene)
                        for name, scene in scenarios["scenes"].items())
    return rows


def formation_scene(rules, defaults, config, scenarios, replay_type, definition, combination, name, scene):
    probabilities = distributions(scene["p"][definition["size"]])
    probe = {**definition, "fieldStayTicks": scene["fieldStayTicks"]}
    outcomes = []
    for affected in range(1, 4):
        args = (rules, defaults, config, combination, scene["opponent"], "human_default")
        with_skill = replay_type(*args, probe=probe, affected=affected).run()
        without = replay_type(*args, probe=probe, affected=affected, omit=True).run()

        def selected(replay):
            if covers_allies(definition):
                return (replay.allies + [replay.caster])[:affected]
            return [replay.caster]

        raw, damage_by_enemy, support_by_ally = team_reading(with_skill, selected(with_skill), [1, 1, 1])
        baseline, _, support_baseline = team_reading(without, selected(without), [1, 1, 1])
        adjusted, _, _ = team_reading(with_skill, selected(with_skill), scenarios["focusWeights"])
        reference_adjusted, _, _ = team_reading(without, selected(without), scenarios["focusWeights"])
        outcomes.append({"affected": affected, "raw": subtract(raw, baseline),
                         "adjusted": subtract(adjusted, reference_adjusted), "perEnemy": damage_by_enemy,
                         "perRecipient": {key: subtract(row, support_baseline[key]) for key, row in support_by_ally.items()},
                         "withSkill": with_skill.events, "withoutSkill": without.events})
    primary = {key: (1 - probabilities[0]) * value for key, value in outcomes[0]["adjusted"].items()}
    expected = {key: sum(probabilities[number] * row["adjusted"][key] for number, row in enumerate(outcomes, 1))
                for key in primary}
    return {"role": definition["role"], "skill": definition["skill"], "scene": name, "sceneWeight": scene["weight"],
            "distribution": probabilities, "conditions": probe, "initial": with_skill.initial,
            "referenceHpPerBudgetPoint": with_skill.target_stats["health"] / 100,
            "focusWeights": scenarios["focusWeights"], "combination": combination,
            "coverageGaps": {key: value for key, value in config["coverageGaps"].items() if key in combination},
            "firstTarget": primary, "additional": subtract(expected, primary), "expected": expected, "outcomes": outcomes}


def formation_branch_differences(rules, rows):
    roles = named(rules["roles"])
    seen = defaultdict(list)
    for row in rows:
        probe = row["conditions"]
        if not probe.get("compareBranch"):
            continue
        role, skill = row["role"], row["skill"]
        index = SLOTS.index(probe["slot"])
        other_slots = tuple(row["combination"][:index] + row["combination"][index + 1:])
        seen[(role, probe["slot"], other_slots, skill)].append(row)
    result = []
    for (role, slot, common, skill), a in seen.items():
        first, second = roles[role]["legalBranches"]["exclusive"][slot]
        if skill != first:
            continue
        b = seen[(role, slot, common, second)]
        metrics = {}
        for metric in METRICS:
            def contribution(group, attribution):
                return sum(row["sceneWeight"] * row[attribution][metric] for row in group)

            a_total, b_total = contribution(a, "expected"), contribution(b, "expected")
            mean = (a_total + b_total) / 2
            difference = abs(a_total - b_total) / mean if mean > 0 else None
            gaps = {name for row in [*a, *b] for name, gap in row["coverageGaps"].items()
                    if METRIC_AXES[metric] in gap["axes"] or "timing" in gap["axes"]}
            metrics[metric] = {"aPrimary": contribution(a, "firstTarget"), "bPrimary": contribution(b, "firstTarget"),
                               "aAdditional": contribution(a, "additional"), "bAdditional": contribution(b, "additional"),
                               "aExpected": a_total, "bExpected": b_total, "relativeDelta": difference,
                               "aboveReference": difference > .08 if difference is not None else None, "reference": .08,
                               "coverageGaps": sorted(gaps)}
        result.append({"role": role, "slot": slot, "commonSkills": common, "a": first, "b": second, "metrics": metrics})
    return result
