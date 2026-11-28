"""Audit all 15 playable heroes and replay the four roster groups on one release."""

import argparse
from collections import Counter
from importlib import import_module
import json
from pathlib import Path

from calibration_evidence import paired_release, reference
from combo_calibration import METRICS, SCENARIOS, Replay, case_report, load, named, run_role
from roster_calibration import UNITS, branch_differences, relevant_gaps, skills_diff


GROUPS = {
    110: {"luoxia": "落霞", "yingxiao": "夜影", "keheng": "刻衡", "qingluo": "青萝"},
    111: {"yike": "伊珂", "shuo": "朔", "rivet": "铆钉", "chixiao": "赤霄"},
    112: {"tianxing": "天刑", "sulan": "素岚", "fenjue": "焚诀"},
    113: {"lingyuan": "凌渊", "hanze": "寒泽", "xuanchen": "墨尘", "yunsheng": "云笙"},
}
ARCHETYPES = {
    "刺客": ("yingxiao",),
    "灵巧战士": ("shuo", "sulan", "tianxing", "keheng"),
    "重装前排": ("rivet", "chixiao", "fenjue"),
    "阵地／爆发法师": ("yike", "lingyuan", "hanze", "xuanchen"),
    "持续射手": ("luoxia",),
    "守护／增益辅助": ("qingluo", "yunsheng"),
}


def decisions_for(ticket, role):
    if ticket != 110:
        path = SCENARIOS.parent / f"roster-{ticket}-{role}-decisions.json"
        return path, load(path)
    path = SCENARIOS.parent / "calibration-role-decisions.json"
    bank = load(path)["roles"][role]
    scenario = f"{role}.zero_preparation"
    values = {
        "baseAttributes": {"decision": "retain", "condition": "1–20 级零购买或临时增益。",
                           "reason": bank["attributesReason"], "scenario": scenario},
        "basicAttack": {"decision": "retain", "condition": "按配对定义持初始武器并命中合法目标。",
                        "reason": bank["basicReason"], "scenario": scenario},
    }
    for key, row in bank["skills"].items():
        values[key] = {**row, "decision": "retain" if row["decision"] == "retain-current" else row["decision"],
                       "condition": "按正式值的 availability、ranks 和各效果的 condition。",
                       "scenario": scenario}
    return path, values


def mark_model_coverage(replay):
    envelopes = replay["envelopeOfModeledReplay"]
    for mode, delays in envelopes.items():
        for delay, metrics in delays.items():
            cases = [case for case in replay["cases"] if (case["opponent"], case["delay"]) == (mode, delay)]
            for key, series in metrics.items():
                series["coverageGaps"] = sorted({gap for case in cases for gap in relevant_gaps(case, key)})
                series["complete"] = not series["coverageGaps"]


def staged_cases(rules, defaults, config, replay, stage_spec, replay_type):
    combination = replay["combinations"][0]
    focus = max(config["startState"]["purchases"], key=config["startState"]["purchases"].get)
    for stage in stage_spec["stages"]:
        defense = {"defense": stage["defense"]} if stage["defense"] else {}
        script = {
            "execution": config["execution"],
            "startState": {**stage, "purchases": {**defense, focus: stage["attackInvestment"]},
                           "targetRole": stage_spec["targetRole"], "targetPurchases": defense,
                           "casterIncoming": stage_spec["casterIncoming"]},
        }
        for opponent in defaults["opponents"]:
            yield {"stage": stage["id"], **case_report(rules, defaults, config, combination,
                                                      opponent, "human_default", script, replay_type=replay_type)}


def build(baseline_dir, release_dir):
    baseline, baseline_id = paired_release(baseline_dir)
    release, release_id = paired_release(release_dir)
    rules = release["rules"]
    before, current = named(baseline["rules"]["roles"]), named(rules["roles"])
    expected_roles = set().union(*(set(group) for group in GROUPS.values()))
    if before.keys() != expected_roles or current.keys() != expected_roles:
        raise ValueError("Playable roster differs from the 15 reviewed roles")
    skill_ids = [skill["id"] for role in current.values() for skill in role["skills"]]
    if len(skill_ids) != 120 or len(set(skill_ids)) != 120:
        raise ValueError("Playable candidate skills require 120 unique IDs")
    defaults_path = SCENARIOS / "_defaults.json"
    stage_path = SCENARIOS / "roster-114-stages.json"
    defaults, stages = load(defaults_path), load(stage_path)
    archetypes = {role: label for label, members in ARCHETYPES.items() for role in members}
    roles, counts = {}, {}
    for ticket, group in GROUPS.items():
        replay_types = import_module(f"replay-roster-{ticket}").SPEC.replays if ticket != 110 else {}
        for role, label in group.items():
            current_role = current[role]
            if len(current_role["skills"]) != 8 or len(current_role["attributes"]["health"]["value"]) != 20 \
                    or len(current_role["attributes"]["mana"]["value"]) != 20:
                raise ValueError(f"Incomplete baseline attributes or candidate count for {role}")
            path = SCENARIOS / f"{role}.json"
            config = load(path)
            replay_type = replay_types.get(role, Replay)
            replay = run_role(rules, defaults, config, current_role, replay_type)
            mark_model_coverage(replay)
            decision_path, decisions = decisions_for(ticket, role)
            values = skills_diff(before[role], current_role, decisions)
            roles[role] = {
                "name": label, "archetype": archetypes[role], "group": ticket,
                "scenario": {**reference(path), "id": config["scenarioId"], "version": config["scenarioVersion"]},
                "decisionSource": reference(decision_path), "values": values, "replay": replay,
                "stageCases": list(staged_cases(rules, defaults, config, replay, stages, replay_type)),
                "branchDifferences": branch_differences(rules, defaults, config, current_role, replay_type, replay),
            }
        counts[ticket] = {
            "roles": len(group),
            "uniqueCandidateSkills": sum(len(current[key]["skills"]) for key in group),
            "terminalCombinations": sum(len(roles[key]["replay"]["combinations"]) for key in group),
            "terminalTraces": sum(len(roles[key]["replay"]["cases"]) for key in group),
            "stageTraces": sum(len(roles[key]["stageCases"]) for key in group),
        }
    return {
        "formatVersion": 1, "basis": "paired Fabric snapshot, static inventory and fixed-input offline combat replay",
        "baseline": baseline_id, "release": release_id, "groups": counts, "roles": roles,
        "defaultScenario": {"file": reference(defaults_path), "definition": defaults},
        "stageScenario": {"file": reference(stage_path), "definition": stages},
        "decisionCounts": dict(Counter(data["values"][skill]["decision"] for data in roles.values()
                                       for skill in data["values"] if skill not in ("baseAttributes", "basicAttack"))),
        "missingEvidence": ["六人真人实图", "场景中未接入的几何与时序", "全部构筑的同输入引擎对照"],
    }


def extreme_metric(series):
    suffix = " ※" if series["coverageGaps"] else ""
    return f"{series['min']:.2f}–{series['max']:.2f}{suffix}"


def markdown(report):
    identity = report["release"]
    lines = ["# 樱隙前线全员正式数值与终局构筑", "",
             f"基线提交 `{report['baseline']['sourceSha']}`。最终提交 `{identity['sourceSha']}`。",
             f"规则摘要 `{identity['rulesSha256']}`。JAR 与快照摘要见同行 JSON。", "",
             "| 批次 | 角色 | 唯一候选 | 合法终局构筑 | 终局轨迹 | 分阶段轨迹 |",
             "| --- | ---: | ---: | ---: | ---: | ---: |"]
    for ticket, group in report["groups"].items():
        lines.append(f"| #{ticket} | " + " | ".join(map(str, group.values())) + " |")
    lines += ["", "逐角色 JSON 含基础属性、1–20 级数值、普攻、逐阶耗蓝／冷却、效果条件、改前值、正式值、选值理由及复跑入口。"
              "四项读数来自冻结初态和动作优先级。角色分支的原始动作、各极值组合、同层增量与覆盖缺口见 JSON。", ""]
    for mode, mode_title in (("solo", "独自出手"), ("ally_cc", "队友先控")):
        lines += [f"## Lv20 · {mode_title} · 常规延迟", "",
                  "| 角色／定位 | " + " | ".join(UNITS.values()) + " |",
                  "| --- | ---: | ---: | ---: | ---: |"]
        for role in report["roles"].values():
            extrema = role["replay"]["envelopeOfModeledReplay"][mode]["human_default"]
            lines.append(f"| {role['name']}／{role['archetype']} | "
                         + " | ".join(extreme_metric(extrema[metric]) for metric in METRICS) + " |")
        lines += ["", "※ 有受影响的覆盖缺口，范围是固定脚本找到的可达读数。", ""]
    lines += ["## 分阶段正面交锋", "",
              "首套合法分支。各阶段冻结同一目标、来伤和预算，按角色既有场景选主要购点。"
              "表中每格为独自出手的 1.5 秒伤害、6 秒均伤、净行动损失与有效盾疗。队友先控同样重放。", "",
              "| 角色 | Lv4／500 金 | Lv12／3,600 金 | Lv20／4,885 金 |",
              "| --- | --- | --- | --- |"]
    for role in report["roles"].values():
        samples = ["／".join(f"{row[metric]:.2f}" for metric in METRICS)
                   for row in role["stageCases"] if row["opponent"] == "solo"]
        lines.append(f"| {role['name']} | " + " | ".join(samples) + " |")
    lines += ["", "一级确定差异、二级参考偏离和三级试测缺口仅供判断，人工值可照常保存、构建、试玩、合并和发布。", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--release", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = build(args.baseline, args.release)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "roster.md").write_text(markdown(report), encoding="utf-8")
    (args.output / "roster.json").write_text(json.dumps(report, ensure_ascii=False, separators=(",", ":")) + "\n",
                                            encoding="utf-8")


if __name__ == "__main__":
    main()
