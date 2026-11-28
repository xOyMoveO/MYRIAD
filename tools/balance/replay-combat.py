"""Recompute the fixed #107 combat scenarios from paired snapshots and compare GameTest evidence."""
import argparse
import hashlib
import json
from math import ceil, floor, isclose
from pathlib import Path
from xml.etree import ElementTree


POINTS = (0, 4, 8, 12)
ATTRIBUTES = ("strength", "wisdom", "speed", "defense", "cooldown")
FILTERS = ("balance_haste", "balance_damage_support", "balance_control_supply")


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def named(rows):
    return {row["id"]: row for row in rows}


def get(parent, key):
    return parent[key]["value"]


def skill(roles, role_id, skill_id):
    return named(roles[role_id]["skills"])[skill_id]


def effect(skill_definition, effect_id):
    return named(skill_definition["effects"])[f"{skill_definition['id']}.{effect_id}"]["rule"]["value"]


def first_cooldown(skill_definition):
    return get(skill_definition["ranks"][0], "cooldown")


def stats(roles, role, stat):
    return get(roles[role]["attributes"], stat)


def calculate(rules, purchase_rules, new):
    roles = named(rules["roles"])
    common = rules["skillRules"]
    pricing = get(purchase_rules, "pricing")
    gain = get(purchase_rules, "permanentGain")
    out = {}

    def record(case, variant="", **fields):
        out[(case, str(variant))] = fields

    def ticks(base, points):
        if not new:
            return max(1, floor(base * (1 - min(3 * points, get(common, "cooldownReductionCap")) / 100))) if base else 0
        rule = get(common, "skillHaste")
        normal = rule["denominatorBase"]
        return max(rule["minPositiveTicks"], floor(base * normal / (normal + points * rule["perPurchase"]))) if base else 0

    def price(rank):
        quote = pricing["startingGold"]
        divisor = pricing["stepDenominator"]
        for _ in range(rank):
            quote = (pricing["stepNumerator"] * quote + divisor // 2) // divisor
        return quote

    normal = first_cooldown(skill(roles, "lingyuan", "g2_lingyuan_ma"))
    yike_q = skill(roles, "yike", "yike_rainbow_shell")
    shell_base = first_cooldown(yike_q)
    for points in POINTS:
        haste = points * get(common, "skillHaste")["perPurchase"] if new else None
        record("ordinary_cooldown", points, base=normal, haste=haste, cooldown=ticks(normal, points))
        for form in ("normal", "fixed", "mobile"):
            frame = effect(yike_q, f"form_{form}")
            committed = ticks(frame["skillCooldown"][0]["value"], points)
            cadence = get(frame, "nextShot") if form != "normal" else committed + effect(yike_q, "commit_windup")
            record("yike_cooldown", f"{form}_{points}", cooldown=committed, interval=cadence,
                   exit_cooldown=ticks(shell_base, points) if form != "normal" else None)

    for attribute in ATTRIBUTES:
        record("training_price", attribute, starting_price=price(0), free=pricing["freeTrainingCounts"],
               rank=4, gold=2000 - sum(price(rank) for rank in range(1, 4)))
    speed, move = stats(roles, "lingyuan", "attackSpeed"), stats(roles, "lingyuan", "movementSpeed")
    speed_step, move_step = gain["attackSpeedPerPoint"], gain["movementSpeedPerPoint"]
    partial_rank = ceil((gain["attackSpeedMax"] - speed) / speed_step)
    full_rank = ceil((gain["movementSpeedMax"] - move) / move_step)
    record("speed_purchase", "partial", buy=True, rank=partial_rank + 1, price=price(partial_rank), gold=17,
           attack_delta=0, movement_delta=move_step)
    record("speed_purchase", "full", paid=False, free=not new, rank=full_rank + (0 if new else 1), gold=17)

    def attack_stats(role, build):
        innate = stats(roles, role, "physicalAttack")
        purchased = gain["physicalAttackPerStrength"] if "strength" in build else 0
        bonus = get(common, "azureHornPhysicalAttack") if new else .15
        attack = (innate + purchased) * (1 + bonus if "azure" in build else 1)
        return attack, attack - innate if new else purchased

    def amount(formula, *, attack_bonus=0, spell_power=0):
        if "spellScale" in formula:
            return formula["base"] + formula["spellScale"] * spell_power
        scaling = {"purchased_physical": attack_bonus, "bonus_physical": attack_bonus, "total_spell": spell_power}
        return (formula["base"] + formula["scale"] * scaling[formula["scaling"]]) * formula.get("weight", 1)

    sweep = effect(skill(roles, "fenjue", "g1_fenjue_n1"), "segment.sweep")
    for build in ("zero", "strength", "azure", "strength_azure"):
        attack, bonus = attack_stats("fenjue", build)
        damage = amount(sweep, attack_bonus=bonus)
        record("bonus_physical_damage", build, effectiveAttack=attack, bonusAttack=bonus, first=damage,
               total=damage * sweep["maxHitsPerTarget"])
    shuo = skill(roles, "shuo", "shuo_qiaofan")
    for build in ("zero", "strength", "azure", "defense", "whiff"):
        _, bonus = attack_stats("shuo", build)
        shield = 0 if build == "whiff" else amount(effect(shuo, "shield"), attack_bonus=bonus,
                                                    spell_power=stats(roles, "shuo", "spellPower"))
        riposte = 0 if build == "whiff" else amount(effect(shuo, "riposte"), attack_bonus=bonus)
        record("shuo_qiaofan_shield", build, bonusAttack=bonus, shield=shield, riposte=riposte)
        if build in ("zero", "defense"):
            defense_gain = gain["defensePerPoint"] if build == "defense" else 0
            physical = 4000 / (100 + stats(roles, "shuo", "physicalDefense") + defense_gain)
            magic = 4000 / (100 + stats(roles, "shuo", "magicResistance") + defense_gain)
            true_absorbed = shield - physical - magic
            record("shuo_shield_damage_sequence", build, physicalAbsorbed=physical, magicAbsorbed=magic,
                   trueAbsorbed=true_absorbed, healthLost=30 - true_absorbed)
    dew = effect(skill(roles, "qingluo", "g2_qingluo_n1"), "heal")
    for build in ("zero", "strength", "wisdom", "overflow"):
        spell = stats(roles, "qingluo", "spellPower") + (gain["spellPowerPerWisdom"] if build in ("wisdom", "overflow") else 0)
        nominal = amount(dew, spell_power=spell)
        record("qingluo_verdant_dew", build, spellPower=spell, amount=nominal,
               effective=min(nominal, 12) if build == "overflow" else nominal)

    arena = rules["battlefield"]
    available = arena["controls"]["basicActionAvailability"] if new else {}
    for kind in ("stun", "root", "silence"):
        actions = get(available, kind) if new else {"move": None, "basicAttack": None, "stationaryCast": None}
        record("control_overlap", kind, long_short=30 if new else 10, short_long=30,
               before_expiry=1 if new else None, on_expiry=0 if new else None,
               move=actions["move"], basic_attack=actions["basicAttack"], stationary_cast=actions["stationaryCast"])
    weak, strong = .75, .5
    progress, elapsed = 0, 0
    while progress < 6:
        elapsed += 1
        progress += min([1] + ([weak] if elapsed < 9 else []) + ([strong] if elapsed < 5 else []))
    wind_wall = first_cooldown(skill(roles, "sulan", "g1_sulan_ma"))
    record("action_time", weak=weak, strong=strong, overlap=min(weak, strong), recovered=weak, clear=1,
           required_action_ticks=6, elapsed_real_ticks=elapsed, action_lost=elapsed - 6,
           initial_cooldown=wind_wall, remaining_cooldown=wind_wall - elapsed)

    supplies = arena["supplies"]
    blood, mana = (supplies["offers"][key] for key in ("blood_pill", "mana_pill"))

    def pulse_schedule(pill):
        interval = get(pill, "pulseInterval")
        return range(interval, get(pill, "duration") + 1, interval)

    def pulse_amount(pill, maximum):
        if new:
            return get(pill, "pulseBase") + get(pill, "pulseMaximumRatio") * maximum
        return get(pill["uncappedPulseSamplesByMaximum"], str(maximum))

    for name, combat_times in (("out_of_combat", ()), ("in_combat", (0, 80)), ("mixed", (0,))):
        pulses = [pulse_amount(blood, 800) * (get(blood, "combatMultiplier") if new and any(
            0 <= age - previous <= get(blood, "combatWindowInclusive") for previous in combat_times) else 1)
                  for age in pulse_schedule(blood)]
        record("blood_pill", name, maximum=800, total=sum(pulses), pulses=pulses)
    mana_pulses = [pulse_amount(mana, 700) for _ in pulse_schedule(mana)]
    record("mana_pill", maximum=700, total=sum(mana_pulses), pulses=mana_pulses)
    lifecycle = get(supplies, "regenerationLifecycle") if new else {}
    record("pill_lifecycle", full_stops=lifecycle.get("fullStops"), death_stops=lifecycle.get("deathStops"),
           same_type_rejected=lifecycle.get("sameTypeRejected"), combined_carry_limit=get(supplies, "pillCarryLimit"))
    return out


def parse(item):
    if item in ("true", "false"):
        return item == "true"
    if "," in item:
        return [parse(value) for value in item.split(",")]
    try:
        return float(item) if "." in item else int(item)
    except ValueError:
        return item


def read_engine(directory):
    observed, runs, yike = {}, [], {}
    for name in FILTERS:
        log = directory / f"{name}.log"
        xml = directory / f"{name}.xml"
        text = log.read_text(encoding="utf-8")
        cases = list(ElementTree.parse(xml).getroot().iter("testcase"))
        passed = (f"Will run tests matching myriad:{name} (1 tests)" in text
                  and "1 GAME TESTS COMPLETE" in text and "All 1 required tests passed" in text
                  and len(cases) == 1 and cases[0].get("name") == f"myriad:{name}"
                  and cases[0].find("failure") is None and cases[0].find("error") is None)
        runs.append({"filter": name, "passed": passed, "xmlCases": len(cases), "log": str(log), "xml": str(xml)})
        for line in text.splitlines():
            if "MYRIAD_COMBAT " in line:
                fields = dict(word.split("=", 1) for word in line.split("MYRIAD_COMBAT ", 1)[1].split())
                case = fields.pop("case")
                variant = next((fields.pop(key) for key in ("points", "attribute", "state", "build", "type", "mode") if key in fields), "")
                observed[(case, variant)] = {key: parse(value) for key, value in fields.items()}
            if "BALANCE_VERTICAL " in line:
                entry = json.loads(line.split("BALANCE_VERTICAL ", 1)[1])
                if entry["event"] in ("muzzle", "stance_exited"):
                    yike.setdefault(entry["caseId"], []).append(entry)
    for case, entries in yike.items():
        shots = [row for row in entries if row["event"] == "muzzle"]
        exit_row = next((row for row in entries if row["event"] == "stance_exited"), None)
        if len(shots) == 2 and shots[0]["cooldownTicks"] == shots[1]["cooldownTicks"]:
            observed[("yike_cooldown", case)] = {"cooldown": shots[0]["cooldownTicks"],
                                                "interval": shots[1]["tick"] - shots[0]["tick"],
                                                "exit_cooldown": exit_row["cooldownTicks"] if exit_row else None}
    return observed, runs


def matches(expected, observed):
    if isinstance(expected, list):
        return isinstance(observed, list) and len(expected) == len(observed) and all(
            matches(a, b) for a, b in zip(expected, observed))
    if type(expected) in (float, int):
        return type(observed) in (float, int) and isclose(expected, observed, rel_tol=0, abs_tol=.002)
    return expected == observed


def metadata(path, snapshot):
    return {**{key: snapshot[key] for key in ("formatVersion", "sourceSha", "sourceDirty", "rulesSha256")},
            "file": str(path), "fileSha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("baseline", "snapshot", "logs", "output"):
        parser.add_argument(f"--{option}", type=Path, required=True)
    args = parser.parse_args()
    old, new = load(args.baseline), load(args.snapshot)
    if old["formatVersion"] != 1 or new["formatVersion"] != 2:
        parser.error("expected a #106 version 1 baseline and a #107 version 2 snapshot")
    purchase_rules = new["rules"]["attributePurchases"]
    before, after = calculate(old["rules"], purchase_rules, False), calculate(new["rules"], purchase_rules, True)
    observed, runs = read_engine(args.logs)
    rows = []
    for (case, variant), expected in after.items():
        actual = observed.pop((case, variant), None)
        same = actual is not None and expected.keys() == actual.keys() and all(
            matches(value, actual[key]) for key, value in expected.items())
        rows.append({"case": case, "variant": variant, "before": before[(case, variant)], "offline": expected,
                     "engine": actual, "matched": same})
    matched = sum(row["matched"] for row in rows)
    report = {"scenarioVersion": 1, "baseline": metadata(args.baseline, old), "snapshot": metadata(args.snapshot, new),
              "engineRuns": runs, "matched": matched, "cases": rows,
              "extraEngineCases": [{"case": case, "variant": variant, "engine": result}
                                   for (case, variant), result in observed.items()]}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "combat-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# 公共战斗规则对照", "", f"基线 `{old['sourceSha']}`。新快照 `{new['sourceSha']}`。",
             f"离线与引擎匹配 {matched}/{len(rows)} 条。", "", "| 场景 | 样本 | 改前复算 | 改后复算 | 引擎 | 一致 |",
             "| --- | --- | --- | --- | --- | --- |"]
    for row in rows:
        cells = [row["case"], row["variant"], *(json.dumps(row[key], ensure_ascii=False) for key in ("before", "offline", "engine")),
                 "是" if row["matched"] else "待核对"]
        lines.append("| " + " | ".join(cells) + " |")
    (args.output / "combat-report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Combat replay: {matched}/{len(rows)} matching cases, {sum(run['passed'] for run in runs)}/{len(runs)} GameTests")
    return int(matched != len(rows) or any(not run["passed"] for run in runs) or bool(observed))


if __name__ == "__main__":
    raise SystemExit(main())
