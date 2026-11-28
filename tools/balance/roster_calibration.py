"""Attach role, branch and battlefield evidence to paired Fabric releases."""

import argparse
from collections import Counter
from dataclasses import dataclass
import json
from pathlib import Path
from collections.abc import Callable

from calibration_evidence import engine_run, paired_release, reference
from combo_calibration import METRICS, SCENARIOS, SLOTS, Replay, load, named, run_role


DECISIONS = {"retain": "采用现值", "adopt": "采用新值", "needs_decision": "待选值"}
AXES = dict(zip(METRICS, ("damage", "damage", "control", "support"), strict=True))
UNITS = dict(zip(METRICS, ("1.5 秒伤害 HP", "6 秒伤害 HP/s", "净行动损失秒", "有效盾疗 HP"), strict=True))


@dataclass(frozen=True)
class RosterSpec:
    ticket: int
    title: str
    roles: dict[str, str]
    replays: dict[str, type]
    engine: dict[str, str | None]
    battlefield_notes: list[str]
    augment: Callable | None = None
    render_extra: Callable | None = None

    def battlefield_filters(self):
        prefix = "balance_roster" if self.ticket == 111 else f"balance_roster_{self.ticket}"
        return {scene: f"{prefix}_{scene}" for scene in ("lane", "camps", "towers")}


def skills_diff(previous, current, decisions):
    expected = set(named(current["skills"])) | {"baseAttributes", "basicAttack"}
    if set(decisions) != expected:
        raise ValueError(f"{current['id']} decision keys differ from the recorded roster: {expected ^ set(decisions)}")
    data = {}
    for key, field in (("baseAttributes", "attributes"), ("basicAttack", "basicAttack")):
        data[key] = {**decisions[key], "previous": previous[field], "adopted": current[field]}
    previous_skills = named(previous["skills"])
    for skill in current["skills"]:
        key = skill["id"]
        before = previous_skills[key]
        effects = named(skill["effects"])
        if len(effects) != len(skill["effects"]):
            raise ValueError(f"Duplicate effect ID in {key}")
        data[key] = {**decisions[key], "previous": before, "adopted": skill,
                     "newlyDocumentedEffects": sorted(set(effects) - set(named(before["effects"])))}
    return data


def relevant_gaps(case, metric):
    return {key: gap for key, gap in case["coverageGaps"].items()
            if AXES[metric] in gap["axes"] or "timing" in gap["axes"]}


def branch_differences(rules, defaults, config, role, replay_type, modeled):
    cases = modeled["cases"]
    output = []
    for slot, branch in role["legalBranches"]["exclusive"].items():
        slot_index = SLOTS.index(slot)
        for a in cases:
            if a["combination"][slot_index] != branch[0]:
                continue
            common = a["combination"][:slot_index] + a["combination"][slot_index + 1:]
            b = next(case for case in cases if case["combination"][slot_index] == branch[1]
                     and case["combination"][:slot_index] + case["combination"][slot_index + 1:] == common
                     and (case["opponent"], case["delay"]) == (a["opponent"], a["delay"]))
            baseline = replay_type(rules, defaults, config, a["combination"], a["opponent"], a["delay"],
                                   omitted_slot=slot).run()
            joint = baseline.result()
            row = {"slot": slot, "a": branch[0], "b": branch[1], "commonSkills": common,
                   "opponent": a["opponent"], "delay": a["delay"], "inputs": a["initial"],
                   "withoutEither": {"metrics": joint, "events": baseline.events}, "metrics": {}}
            for metric in METRICS:
                gain_a = a[metric] - joint[metric]
                gain_b = b[metric] - joint[metric]
                average = (gain_a + gain_b) / 2
                difference = abs(gain_a - gain_b) / average if average > 0 else None
                gaps = {**relevant_gaps(a, metric), **relevant_gaps(b, metric)}
                row["metrics"][metric] = {
                    "aContribution": gain_a, "bContribution": gain_b, "absoluteDelta": abs(gain_a - gain_b),
                    "relativeDelta": difference, "reference": .08, "aboveReference": difference > .08 if difference is not None else None,
                    "status": "modeled_subset" if gaps else "modeled_for_this_scenario", "coverageGaps": sorted(gaps),
                }
            output.append(row)
    return output


def rank_values(skill, field):
    return [row[field].get("value") for row in skill["ranks"]]


def runway(engine, spec):
    filters = spec.battlefield_filters()
    return {
        "clearLane": [row for row in engine[filters["lane"]].get("events", [])
                      if row.get("scenario") == "clear_wave"],
        "campFights": [row for row in engine[filters["camps"]].get("events", [])
                       if row.get("event") == "fight"],
        "escortedTowers": [row for row in engine[filters["towers"]].get("events", [])
                           if row.get("scenario") == "whole_wave"],
        "remainingBattlefieldReview": spec.battlefield_notes,
    }


def collect_engine(source, filters):
    engine = {}
    for label, marker in filters.items():
        if source is None:
            engine[label] = {"label": label, "status": "not_run_in_report"}
            continue
        directory = source / label
        if (directory / "stdout.txt").is_file() and (directory / "report.xml").is_file():
            evidence = engine_run(label, directory / "stdout.txt", directory / "report.xml", marker)
            if evidence["testCases"] != [f"myriad:{label}"]:
                evidence["status"] = "failed_or_incomplete"
            engine[label] = evidence
        else:
            engine[label] = {"label": label, "status": "missing_engine_evidence"}
    return engine


def build(args, spec):
    before, before_id = paired_release(args.baseline)
    after, after_id = paired_release(args.release)
    rules = after["rules"]
    previous = named(before["rules"]["roles"])
    roster = named(rules["roles"])
    defaults_path = SCENARIOS / "_defaults.json"
    defaults = load(defaults_path)
    battlefield = spec.battlefield_filters()
    engine = collect_engine(args.engine, {
        **spec.engine,
        battlefield["lane"]: "MYRIAD_BATTLEFIELD_LANE ",
        battlefield["camps"]: "MYRIAD_BATTLEFIELD_MONSTER ",
        battlefield["towers"]: "BATTLEFIELD_BUILDING ",
    })
    roles = {}
    configs = {}
    for name in spec.roles:
        path = SCENARIOS / f"{name}.json"
        decision_path = SCENARIOS.parent / f"roster-{spec.ticket}-{name}-decisions.json"
        config, decisions = load(path), load(decision_path)
        configs[name] = config
        replay_type = spec.replays.get(name, Replay)
        modeled = run_role(rules, defaults, config, roster[name], replay_type)
        for mode, delays in modeled["envelopeOfModeledReplay"].items():
            for delay, metrics in delays.items():
                for metric, envelope in metrics.items():
                    gaps = {key for case in modeled["cases"] if (case["opponent"], case["delay"]) == (mode, delay)
                            for key in relevant_gaps(case, metric)}
                    envelope["complete"] = not gaps
                    envelope["coverageGaps"] = sorted(gaps)
        roles[name] = {"scenario": reference(path), "decisionSource": reference(decision_path),
                       "values": skills_diff(previous[name], roster[name], decisions), "replay": modeled,
                       "branchDifferences": branch_differences(rules, defaults, config, roster[name], replay_type, modeled)}
    counts = {
        "candidateSkills": sum(len(role["replay"]["static"]) for role in roles.values()),
        "terminalCombinations": sum(len(role["replay"]["combinations"]) for role in roles.values()),
        "runs": sum(len(role["replay"]["cases"]) for role in roles.values()),
        "sameLayerMatches": sum(len(role["branchDifferences"]) for role in roles.values()),
        "decisions": dict(Counter(value["decision"] for role in roles.values() for key, value in role["values"].items()
                                  if key not in ("baseAttributes", "basicAttack"))),
    }
    for role in roles.values():
        for value in role["values"].values():
            if value["decision"] not in DECISIONS or not value["reason"] or not value["scenario"]:
                raise ValueError("Each role decision needs a status, a reason and a replay reference")
    report = {
        "formatVersion": 1, "baseline": before_id, "release": after_id,
        "sharedScenario": reference(defaults_path), "sharedInputs": defaults, "counts": counts, "roles": roles,
        "battlefield": runway(engine, spec), "fabricGameTests": engine,
        "reviewPolicy": "所有平衡结果仅供策划选值。正式构建与实际引擎专项分别记录。",
    }
    if spec.augment:
        spec.augment(report, rules, defaults, configs, args)
    return report


def display(values):
    return "／".join("—" if v is None else str(v) for v in values)


def markdown(report, spec):
    before, after = report["baseline"], report["release"]
    counts = report["counts"]
    lines = [
        f"# {spec.title}", "",
        f"对照提交 `{before['sourceSha']}`，本轮提交 `{after['sourceSha']}`。",
        f"本轮 JAR SHA-256 `{after['jar']['sha256']}`，快照 SHA-256 `{after['snapshot']['sha256']}`。", "",
        f"共 {counts['candidateSkills']} 个候选、{counts['terminalCombinations']} 套合法终局构筑、"
        f"{counts['runs']} 条零预置轨迹、{counts['sameLayerMatches']} 次同层匹配。", "",
        "每套起点固定英雄等级、预算、配点、敌方血抗和距离。两种对手条件均跑常规操作与零额外延迟。"
        "控制对照扣除队友已施加的动作限制，纯移速减速只作用于移动权重。"
        "同层比较保留另外两层、Q、W 和同一动作表，分别减去临时停用被比较层的共同轨迹。"
        "同层对照完整轨迹与四项差量见 JSON。覆盖缺口旁的数字只表示已建模范围。", "",
    ]
    for name, role in report["roles"].items():
        lines.extend([f"## {spec.roles[name]}", ""])
        for key in ("baseAttributes", "basicAttack"):
            row = role["values"][key]
            title = "基础属性与逐级成长" if key == "baseAttributes" else "普攻"
            lines.append(f"- {title}：{DECISIONS[row['decision']]}。{row['reason']} 复跑 `{row['scenario']}`。")
        lines.extend(["", "| 候选 | 状态 | 各阶蓝耗 改前 → 采用 | 各阶基础冷却刻 改前 → 采用 | 依据与复跑 |",
                      "| --- | --- | --- | --- | --- |"])
        for key, row in role["values"].items():
            if key in ("baseAttributes", "basicAttack"):
                continue
            old, new = row["previous"], row["adopted"]
            mana = f"{display(rank_values(old, 'mana'))} → {display(rank_values(new, 'mana'))}"
            cd = f"{display(rank_values(old, 'cooldown'))} → {display(rank_values(new, 'cooldown'))}"
            lines.append(f"| `{key}` | {DECISIONS[row['decision']]} | {mana} | {cd} | {row['reason']} `{row['scenario']}` |")
        lines.extend(["", "| 起手 | 延迟 | 指标 | 最低构筑／读数 | 最高构筑／读数 | 覆盖 |",
                      "| --- | --- | --- | --- | --- | --- |"])
        for mode, profiles in role["replay"]["envelopeOfModeledReplay"].items():
            for delay, metric_rows in profiles.items():
                for metric, row in metric_rows.items():
                    low = "＋".join(row["minCombination"][i] for i in (0, 3, 4))
                    high = "＋".join(row["maxCombination"][i] for i in (0, 3, 4))
                    coverage = "场景已建模" if row["complete"] else "存在缺口"
                    lines.append(f"| {mode} | {delay} | {UNITS[metric]} | {low}／{row['min']:.3f} | "
                                 f"{high}／{row['max']:.3f} | {coverage} |")
        lines.append("")
    if spec.render_extra:
        lines.extend(spec.render_extra(report))
    lines.extend(["## 樱隙前线固定输入", "",
                  "| 清线英雄 | 比赛时点 | 敌波开场生命 | 清波用时（秒） | 残余生命 |",
                  "| --- | ---: | ---: | ---: | ---: |"])
    for row in report["battlefield"]["clearLane"]:
        duration = f"{row['finishTicks'] / 20:.2f}" if row["finishTicks"] else "未完成"
        lines.append(f"| {spec.roles[row['hero']]} | {row['elapsedTicks'] / 1200:.0f}:00 | "
                     f"{row['startWaveHealth']:.2f} | {duration} | {row['endWaveHealth']:.2f} |")
    lines.extend(["", "| 清营构筑 | 结果 | 观察时长（秒） | 营地生物残血 | 英雄余血 |",
                  "| --- | --- | ---: | ---: | ---: |"])
    for row in report["battlefield"]["campFights"]:
        lines.append(f"| {row['scenario']} | {row['outcome']} | {row['fightTicks'] / 20:.2f} | "
                     f"{row['monsterHealthAtEnd']:.2f} | {row['healthAtEnd'][0]:.2f} |")
    lines.extend(["", "| 带兵英雄 | 塔档 | 英雄塔伤 | 兵线塔伤 | 塔损血比例 |",
                  "| --- | ---: | ---: | ---: | ---: |"])
    for row in report["battlefield"]["escortedTowers"]:
        lines.append(f"| {spec.roles[row['hero']]} | {row['tier']} | {row['heroDamage']:.2f} | "
                     f"{row['laneDamage']:.2f} | {row['percent']:.2f}% |")
    lines.extend(["", *(f"- {item}" for item in report["battlefield"]["remainingBattlefieldReview"]), "",
                  "## Fabric GameTest", "",
                  "| 过滤器 | 实际状态 | 已解析事件 |",
                  "| --- | --- | ---: |"])
    for name, row in report["fabricGameTests"].items():
        lines.append(f"| `myriad:{name}` | {row['status']} | {len(row.get('events', []))} |")
    lines.extend(["", report["reviewPolicy"], ""])
    return "\n".join(lines)


def main(spec):
    parser = argparse.ArgumentParser(description=spec.title)
    parser.add_argument("--baseline", required=True, type=Path, help="Prior paired release")
    parser.add_argument("--release", required=True, type=Path, help="Current paired release")
    parser.add_argument("--engine", type=Path, help="Directory holding the per-filter logs and XML")
    parser.add_argument("--baseline-engine", type=Path, help="Prior run per-filter logs and XML")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    report = build(args, spec)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / f"roster-{spec.ticket}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output / f"roster-{spec.ticket}.md").write_text(markdown(report, spec), encoding="utf-8")
