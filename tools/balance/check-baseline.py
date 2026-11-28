"""Read back the independent #105 baseline samples from the generated snapshot."""
import argparse
import json
from math import isclose
from pathlib import Path


def equal(observed, expected, label):
    if observed != expected:
        raise SystemExit(f"{label}: expected {expected!r}, observed {observed!r}")


def value(fields, key):
    return fields[key]["value"]


def named(rows):
    return {item["id"]: item for item in rows}


def effect(skill, effect_id):
    return named(skill["effects"])[f"{skill['id']}.{effect_id}"]["rule"]


def verify(snapshot, report):
    rules = snapshot["rules"]
    roles = named(rules["roles"])
    skills = {item["id"] for role in roles.values() for item in role["skills"]}
    if snapshot["formatVersion"] not in (1, 2):
        raise SystemExit(f"Unsupported snapshot version {snapshot['formatVersion']}")
    equal(len(roles), 15, "distinct roles")
    equal(len(skills), 120, "distinct skills")
    equal(len(report["roster"]["roleResources"]), 7, "definition resources")
    for role in roles.values():
        equal(len(role["skills"]), 8, f"{role['id']} candidates")
        equal(len({tuple(branch) for branch in role["legalBranches"]["terminalCombinations"]}), 8,
              f"{role['id']} distinct terminal branches")
        for name in ("health", "mana"):
            equal(len(value(role["attributes"], name)), 20, f"{role['id']} {name} levels")
    equal(report["rulesSha256"], snapshot["rulesSha256"], "coverage revision")

    luoxia = roles["luoxia"]
    equal((value(luoxia["attributes"], "health")[0], value(luoxia["attributes"], "health")[-1]), (575, 2000),
          "Luoxia health at level 1/20")
    equal(value(luoxia["basicAttack"], "rawDamage"), 100, "Luoxia normal bow initial damage")
    heavy_arrow = named(luoxia["skills"])["luoxia_last_sun"]
    equal(effect(heavy_arrow, "arrow_range")["value"], 28, "Luoxia heavy arrow range")
    equal(effect(heavy_arrow, "arrow_range")["unit"], "blocks", "Luoxia heavy arrow range unit")
    equal(effect(heavy_arrow, "commit_windup")["value"], 10, "Luoxia heavy arrow action ticks")

    yike_q = named(roles["yike"]["skills"])["yike_rainbow_shell"]
    ordinary = effect(yike_q, "form_normal")["value"]
    equal(ordinary["skillCooldown"][0]["value"], 120, "Yike initial ordinary Q cooldown")
    equal(ordinary["shots"]["status"], "not_applicable", "Yike ordinary shell magazine")
    for form, ticks, shots in (("fixed", 22, 5), ("mobile", 27, 4)):
        artillery = effect(yike_q, f"form_{form}")["value"]
        equal(artillery["skillCooldown"][0]["status"], "zero", f"Yike {form} cooldown status")
        equal(artillery["skillCooldown"][0]["value"], 0, f"Yike {form} cooldown")
        equal(artillery["nextShot"]["clock"], "real_tick", f"Yike {form} timing basis")
        equal(value(artillery, "nextShot"), ticks, f"Yike {form} next shot")
        equal(value(artillery, "shots"), shots, f"Yike {form} starting shells")

    keheng = named(roles["keheng"]["skills"])
    equal(effect(keheng["keheng_kehen"], "damage")["value"]["rankGrowth"], 14.4, "Keheng echo growth")
    equal(effect(keheng["keheng_chijie"], "damage")["value"]["rankGrowth"], 22.5, "Keheng boundary growth")
    control = keheng["keheng_yimiao"]
    equal(effect(control, "field_duration")["value"], 60, "Keheng dome duration")
    if not isclose(effect(control, "field_actor_rate")["value"][2], .25):
        raise SystemExit("Expected Keheng rank-three dome action rate of .25")
    equal({row["skill"]: (row["expected"], row["displayed"]) for row in report["presentationReminders"]},
          {"keheng_kehen": (14.4, 12), "keheng_chijie": (22.5, 18)}, "baseline-only zh_cn reminders")

    anchor = named(roles["rivet"]["skills"])["rivet_tactical_anchor"]["ranks"][0]
    equal(anchor["rank"], 0, "Rivet base anchor availability")
    equal(value(anchor, "cooldown"), 160, "Rivet anchor rank-zero cooldown")

    battlefield = rules["battlefield"]
    arena = battlefield["maps"]["3v3"]
    equal(value(arena, "firstWave"), 700, "3v3 first wave")
    equal(arena["firstWave"]["unit"], effect(heavy_arrow, "commit_windup")["unit"], "shared tick unit")
    equal(arena["firstWave"]["clock"], "real_tick", "3v3 first wave timing basis")
    equal(effect(heavy_arrow, "commit_windup")["clock"], "action_tick", "Luoxia windup timing basis")
    equal(len(arena["camps"]), 13, "3v3 configured camps")
    equal(value(arena, "towerTiers"), [1, 3], "3v3 installed tower tiers")
    equal(len(arena["fountains"]), 2, "3v3 fountains")
    equal(len(arena["crystals"]), 2, "3v3 crystals")
    equal(battlefield["maps"]["5v5"]["camps"]["status"], "missing_input", "5v5 deployment camps")
    equal(len(battlefield["minions"]["types"]), 4, "minion types")
    equal(len(battlefield["monsters"]["types"]), 19, "monster types")
    equal(len(value(battlefield["rotatingBosses"], "candidates")), 4, "rotating bosses")
    equal(len(battlefield["supplies"]["offers"]), 5, "supply types")
    equal(value(battlefield["economy"], "openingPurse"), 500, "opening purse")
    print("PASS: 15 roles, 120 candidates, 120 branch combinations and independent #105 baseline samples")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path, nargs="?", default=Path("build/balance"))
    directory = parser.parse_args().directory
    snapshot = json.loads((directory / "balance-snapshot.json").read_text(encoding="utf-8"))
    report = json.loads((directory / "balance-coverage.json").read_text(encoding="utf-8"))
    verify(snapshot, report)


if __name__ == "__main__":
    main()
