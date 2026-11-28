"""Replay the two #106 balance scenarios and optionally compare runtime evidence."""
import argparse
from collections import defaultdict, deque
import hashlib
import json
from math import ceil, isclose
from pathlib import Path
from xml.etree import ElementTree

SCENARIOS = Path(__file__).with_name("scenarios")
EVENT_MARKER = "BALANCE_VERTICAL "


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def named(rows):
    return {row["id"]: row for row in rows}


def effect(skill, name):
    return named(skill["effects"])[f"{skill['id']}.{name}"]["rule"]


def rank_row(skill, rank):
    return next(row for row in skill["ranks"] if row["rank"] == rank)


def number(field, unit, clock):
    if (field.get("status") in ("defined", "zero") and field.get("unit") == unit
            and field.get("clock") == clock and type(field.get("value")) in (int, float)):
        return field["value"]
    return None


def event(scenario, case, name, tick, **data):
    return {"schemaVersion": 1, "scenarioId": scenario["scenarioId"], "caseId": case, "event": name,
            "tick": tick, "timeUnit": scenario["timeUnit"], **data}


def luoxia_replay(scenario, skills):
    skill = skills[scenario["skill"]]
    arrow_range = effect(skill, "arrow_range")
    speed = effect(skill, "arrow_speed")
    padding = effect(skill, "arrow_entity_padding")
    windup = effect(skill, "commit_windup")
    damage = effect(skill, "damage")
    fields = {"arrow_range": arrow_range, "arrow_speed": speed, "arrow_entity_padding": padding,
              "commit_windup": windup, "damage": damage}
    initial, geometry = scenario["initial"], scenario["conditions"]
    fire_tick = scenario["actions"]["requestTick"] + ceil(windup["value"] / initial["actionMultiplier"])
    steps = speed["value"] * initial["actionMultiplier"]
    width = geometry["targetWidthBlocks"]
    observation = geometry["observationEndTick"]

    events = []
    for center in geometry["centerDistancesBlocks"]:
        def record(name, tick, **data):
            events.append(event(scenario, scenario["caseId"], name, tick, centerDistanceBlocks=center, **data))

        record("initial", 0, level=initial["level"], rank=initial["rank"], mana=initial["mana"],
               rangeBlocks=arrow_range["value"], targetWidthBlocks=width, observationEndTick=observation)
        record("cast_requested", scenario["actions"]["requestTick"], accepted=True)
        record("arrow_released", fire_tick)
        near_face = center - width / 2
        contact = near_face - padding["value"]
        impact_tick = fire_tick + ceil(contact / steps)
        hit = near_face <= arrow_range["value"] and impact_tick <= observation
        formula = damage["value"]
        # The fixture is in a clear straight lane, zero purchases, and a target without armor.
        hp_loss = formula["base"] + (initial["rank"] - 1) * formula["rankGrowth"]
        record("range_probe", impact_tick if hit else observation, hit=hit, damage=hp_loss if hit else 0)

    return {"fields": fields, "events": events, "coverage": {"range_probes": {"scope": "range_and_hit"}}}


def yike_replay(scenario, skills):
    skill = skills[scenario["skill"]]
    initial, actions = scenario["initial"], scenario["actions"]
    rank = initial["rank"]
    q_windup = effect(skill, "commit_windup")
    q_cost = rank_row(skill, rank)["mana"]
    normal = effect(skill, "form_normal")["value"]
    normal_cooldown = next(row["value"] for row in normal["skillCooldown"] if row["rank"] == rank)
    fields = {"commit_windup": q_windup, "mana": q_cost}
    events, coverage = [], {}
    for case, config in scenario["cases"].items():
        def record(name, tick, **data):
            events.append(event(scenario, case, name, tick, **data))

        opening = actions["openingRequestTick"]
        mana = initial["mana"]
        form = effect(skill, f"form_{case}")["value"]
        cooldown = next(row["value"] for row in form["skillCooldown"] if row["rank"] == rank)
        shots = number(form.get("shots", {}), "shell", "not_timed")
        ammo, cadence, buffer = shots, None, None
        fields[case] = form
        record("initial", opening, ammo=None, stanceRank=config["stanceRank"], **initial)

        if case != "normal":
            deployment = skills[config["stanceSkill"]]
            deploy_windup = effect(deployment, "commit_windup")
            deploy_mana = rank_row(deployment, config["stanceRank"])["mana"]
            fields[case] = {**form, "deploymentWindup": deploy_windup, "deploymentMana": deploy_mana}
            record("stance_requested", opening, accepted=True)
            entered = opening + ceil(deploy_windup["value"] / initial["actionMultiplier"])
            mana -= deploy_mana["value"]
            record("stance_entered", entered, ammo=ammo, mana=mana)
            opening = entered + actions["firstShotTicksAfterDeployment"]
            cadence = number(form.get("nextShot", {}), "ticks", scenario["timeUnit"])
            buffer = number(form.get("shotRequestBuffer", {}), "ticks", scenario["timeUnit"])

        if shots == 0 and case != "normal":
            record("cast_requested", opening, shot=1, accepted=False)
            coverage[case] = {"scope": "no_shots"}
            continue

        record("cast_requested", opening, shot=1, accepted=True)
        windup = ceil(q_windup["value"] / initial["actionMultiplier"])
        first = opening + windup
        mana -= q_cost["value"]
        if ammo is not None:
            ammo -= 1
        record("muzzle", first, shot=1, ammo=ammo, mana=mana, cooldownTicks=cooldown,
               nextShotTicks=cadence if case != "normal" else 0)

        if case != "normal":
            missing = [name for name, value in (("shots", shots), ("nextShot", cadence),
                                                ("shotRequestBuffer", buffer)) if value is None]
            if missing:
                coverage[case] = {"scope": "single_shot_only", "missingInputs": missing}
                continue
            # A known empty/one-shell magazine cannot support this scene's two-shot sequence.
            if shots == 1:
                coverage[case] = {"scope": "single_shot_only", "reason": "one_shell_magazine"}
                continue

        coverage[case] = {"scope": "two_shots_and_exit" if case != "normal" else "two_shots"}
        # The stance allows a new windup to start early. It must still end at or after the real deadline.
        ready = first + (cooldown if case == "normal" else cadence - buffer)
        record("cast_requested", ready - actions["rejectedProbeTicksBeforeNextAllowedRequest"], shot=2, accepted=False)
        record("cast_requested", ready, shot=2, accepted=True)
        second = ready + windup
        record("muzzle", second, shot=2, ammo=None if ammo is None else ammo - 1, mana=mana - q_cost["value"],
               cooldownTicks=cooldown, nextShotTicks=cadence if case != "normal" else 0)
        if case != "normal":
            record("stance_exited", second + actions["manualExitTicksAfterSecondMuzzle"],
                   reason="manual", cooldownTicks=normal_cooldown)

    return {"fields": fields, "events": events, "coverage": coverage}


def engine_events(path):
    events = []
    decoder = json.JSONDecoder()
    for line in path.read_text(encoding="utf-8").splitlines():
        if EVENT_MARKER in line:
            event_text = line.split(EVENT_MARKER, 1)[1]
            events.append(decoder.raw_decode(event_text)[0])
    return events


def compare_trace(predictions, observations):
    def event_key(row):
        return row["caseId"], row["event"], row.get("centerDistanceBlocks"), row.get("shot")

    actual = defaultdict(deque)
    for observation in observations:
        actual[event_key(observation)].append(observation)
    comparisons = []
    for expected in predictions:
        queue = actual[event_key(expected)]
        observed = queue.popleft() if queue else None
        differences = {}
        for key, value in expected.items():
            if observed is None or key not in observed:
                differences[key] = {"expected": value, "observed": "missing"}
            elif type(value) in (int, float) and type(observed[key]) in (int, float):
                if not isclose(value, observed[key], abs_tol=0.0001):
                    differences[key] = {"expected": value, "observed": observed[key]}
            elif value != observed[key]:
                differences[key] = {"expected": value, "observed": observed[key]}
        comparisons.append({"expected": expected, "observed": observed, "differences": differences})
    return {"matched": sum(not row["differences"] for row in comparisons), "expected": len(predictions),
            "events": comparisons, "engineOnly": [row for queue in actual.values() for row in queue]}


def engine_status(path):
    tests = ElementTree.parse(path).findall(".//testcase")
    failed = [test.attrib["name"] for test in tests
              if any(test.find(tag) is not None for tag in ("failure", "error", "skipped"))]
    return {"testCases": [test.attrib["name"] for test in tests], "failedOrSkipped": failed,
            "status": "passed" if tests and not failed else "failed"}


def reference(path):
    return {"file": path.name, "sha256": digest(path)}


def markdown(report):
    snapshot = report["snapshot"]
    lines = ["# 平衡纵向场景", "", f"- 报告版本 `{report['reportVersion']}`",
             f"- 源码提交 `{snapshot['sourceSha']}`，含未提交改动 `{snapshot['sourceDirty']}`",
             f"- 规则摘要 `{snapshot['rulesSha256']}`",
             f"- 快照文件 SHA-256 `{snapshot['sha256']}`"]
    for name, source in report["sources"].items():
        lines.append(f"- {name} [{source['file']}]({source['file']})，SHA-256 `{source['sha256']}`")
    if "engineChecks" in report:
        checks = report["engineChecks"]
        lines.append(f"- 服务端 GameTest `{checks['status']}`，用例 `{', '.join(checks['testCases'])}`")
    if "cardChecks" in report:
        lines.append(f"- 客户端技能介绍卡读数见 [{report['sources']['客户端技能介绍卡']['file']}]"
                     f"({report['sources']['客户端技能介绍卡']['file']})")

    for item in report["scenarios"]:
        inputs = item["input"]
        lines += ["", f"## {inputs['scenarioId']} · v{inputs['scenarioVersion']}", "",
                  f"- 场景文件 SHA-256 `{item['source']['sha256']}`",
                  f"- 事件时基 `{inputs['timeUnit']}`",
                  f"- 初态 `{json.dumps(inputs['initial'], ensure_ascii=False)}`",
                  f"- 条件 `{json.dumps(inputs['conditions'], ensure_ascii=False)}`"]
        for case, coverage in item["coverage"].items():
            label = ("仅能比较单发" if coverage["scope"] == "single_shot_only" else coverage["scope"])
            missing = coverage.get("missingInputs", [])
            lines.append(f"- {case} · {label}" + (f"，缺少 `{', '.join(missing)}`" if missing else ""))
        if "comparison" in item:
            compare = item["comparison"]
            lines.append(f"- 离线与引擎事件相符 {compare['matched']}/{compare['expected']}，"
                         f"引擎另外观测 {len(compare['engineOnly'])} 项")
        lines += ["", "| 用例 | 真实刻 | 事件 | 离线结果 | 引擎对照 |",
                  "| --- | ---: | --- | --- | --- |"]
        for index, row in enumerate(item["events"]):
            context_keys = ("schemaVersion", "scenarioId", "caseId", "event", "tick", "timeUnit")
            details = {key: value for key, value in row.items() if key not in context_keys}
            result = "未提供"
            if "comparison" in item:
                difference = item["comparison"]["events"][index]["differences"]
                result = f"`{json.dumps(difference, ensure_ascii=False)}`" if difference else "相符"
            lines.append(f"| {row['caseId']} | {row['tick']} | {row['event']} "
                         f"| `{json.dumps(details, ensure_ascii=False)}` | {result} |")
    if report["reminders"]:
        lines += ["", "## 文案提醒", ""]
        lines += [f"- {row['message']}（{row['source']}）" for row in report["reminders"]]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot", type=Path, default=Path("build/balance/balance-snapshot.json"))
    parser.add_argument("--coverage", type=Path)
    parser.add_argument("--server-log", type=Path)
    parser.add_argument("--server-xml", type=Path)
    parser.add_argument("--cards", type=Path)
    parser.add_argument("--release", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    snapshot = load(args.snapshot)
    if snapshot["formatVersion"] not in (1, 2, 3):
        parser.error(f"unsupported snapshot version {snapshot['formatVersion']}")
    report = {"reportVersion": 1,
              "snapshot": {**{key: snapshot[key] for key in ("formatVersion", "sourceSha", "sourceDirty", "rulesSha256")},
                           **reference(args.snapshot)},
              "sources": {}, "scenarios": [], "reminders": []}
    for label, path in (("覆盖报告", args.coverage), ("服务端日志", args.server_log), ("服务端 XML", args.server_xml),
                        ("客户端技能介绍卡", args.cards), ("配对发行清单", args.release)):
        if path:
            report["sources"][label] = reference(path)
    if args.coverage:
        coverage = load(args.coverage)
        if any(coverage[key] != snapshot[key] for key in ("sourceSha", "sourceDirty", "rulesSha256")):
            parser.error("coverage file and snapshot refer to different rules or source")
        report["reminders"] = coverage["presentationReminders"]
    if args.server_xml:
        report["engineChecks"] = engine_status(args.server_xml)
    if args.release:
        release = load(args.release)
        if (release["source_sha"] != snapshot["sourceSha"]
                or release["balance_snapshot"]["sha256"] != report["snapshot"]["sha256"]):
            parser.error("release manifest and snapshot refer to different builds")
        report["release"] = release
    if args.cards:
        report["cardChecks"] = load(args.cards)
        if args.release and report["cardChecks"]["jarSha256"] != release["sha256"]:
            parser.error("client card evidence was not captured with the paired JAR")

    roles = named(snapshot["rules"]["roles"])
    observed = engine_events(args.server_log) if args.server_log else []
    for filename, replay in (("luoxia-last-sun-range.json", luoxia_replay),
                             ("yike-rainbow-shell-stances.json", yike_replay)):
        path = SCENARIOS / filename
        scenario = load(path)
        analysis = replay(scenario, named(roles[scenario["role"]]["skills"]))
        result = {"input": scenario, "source": reference(path), **analysis}
        if args.server_log:
            result["comparison"] = compare_trace(analysis["events"],
                                                [row for row in observed if row["scenarioId"] == scenario["scenarioId"]])
        report["scenarios"].append(result)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "vertical-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output / "vertical-report.md").write_text(markdown(report), encoding="utf-8")
    for scenario in report["scenarios"]:
        print(scenario["input"]["scenarioId"], scenario["coverage"])
        if "comparison" in scenario:
            print(f"Compared {scenario['comparison']['matched']}/{scenario['comparison']['expected']} events")


if __name__ == "__main__":
    main()
