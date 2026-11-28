"""Reproduce the #110 3v3 calibration from a paired release and attach independently run evidence."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
from zipfile import ZipFile

from calibration_evidence import engine_run, paired_release, reference
from combo_calibration import METRICS, SCENARIOS, load, replay_rules


ROLE_DECISIONS = SCENARIOS.parent / "calibration-role-decisions.json"
HUMAN_CALIBRATION = {
    "luoxia.tower_wave": {
        "expected": "前五分钟一波普通兵与落霞合计约掉 15% 满血外塔",
        "feedback": "旧版本和真人站位的推进体感约 15%",
        "source": "https://github.com/xOyMoveO/MYRIAD/issues/96#issuecomment-7233800802",
    },
    "yingxiao.backstab_21a34_finisher": {
        "expected": "同经济绕背 21A34，首套后第一次补 A 存活，第二次补 A 击杀",
        "feedback": "旧版本真人反馈需要第二轮技能；1,114.742、997.323、907 HP 是旧离线样例的读数",
        "source": "https://github.com/xOyMoveO/MYRIAD/issues/96#issuecomment-7233469433",
    },
    "keheng.escape": {
        "expected": "三阶领域中，未交位移的撤退目标在脱离前能被队友有效打到",
        "feedback": "旧版真人确认有跟进机会",
        "source": "https://github.com/xOyMoveO/MYRIAD/issues/96#issuecomment-7233921931",
    },
    "qingluo.rescue": {
        "expected": "给持续受击的半血队友 R 接小技能，稳住血线并恢复输出",
        "feedback": "旧版真人确认有救援效果，承伤量与恢复输出时点待真人量化",
        "source": "https://github.com/xOyMoveO/MYRIAD/issues/96#issuecomment-7233921931",
    },
}


def public_values(snapshot):
    rules = snapshot["rules"]
    gold = rules["battlefield"]["economy"]
    return {"defensePerPoint": rules["attributePurchases"]["permanentGain"]["value"]["defensePerPoint"],
            "assistGold": gold["assistPool"]["value"]["gold"], "assistXp": gold["assistPool"]["value"]["xp"],
            "wageCatchUpWeight": gold["passiveGoldCatchUpWeight"]["value"]}


def same_hero_rules(first, second):
    keys = ("roles", "attributePurchases", "skillRules")
    return all(first["rules"][key] == second["rules"][key] for key in keys)


def measured_variant(name, directory, patch, log, xml, release):
    path = Path(directory)
    patch_ref = reference(Path(patch))
    manifest_path = path / "release.json"
    manifest = load(manifest_path)
    if "balance_snapshot" in manifest:
        snapshot, identity = paired_release(path)
    else:
        jar_path = path / manifest["jar"]["file"]
        snapshot_path = path / manifest["snapshot"]["file"]
        jar_ref, snapshot_ref = reference(jar_path), reference(snapshot_path)
        snapshot = load(snapshot_path)
        with ZipFile(jar_path) as archive:
            embedded = archive.read("META-INF/myriad-balance-snapshot.sha256").decode().strip()
            changed = manifest["verifiedPatchedClass"]
            class_hash = sha256(archive.read(changed["name"])).hexdigest()
        if (jar_ref["sha256"] != manifest["jar"]["sha256"] or snapshot_ref["sha256"] != embedded
                or snapshot_ref["sha256"] != manifest["snapshot"]["sha256"]
                or snapshot["rulesSha256"] != manifest["snapshot"]["rulesSha256"]
                or class_hash != changed["sha256"] or patch_ref["sha256"] != manifest["sourcePatchSha256"]):
            raise ValueError(f"Experiment archive, changed class or patch does not match: {name}")
        identity = {"sourceSha": manifest["baseSourceSha"], "sourceDirty": manifest["sourceDirty"],
                    "archiveSourceSha": snapshot["sourceSha"], "rulesSha256": snapshot["rulesSha256"],
                    "snapshot": snapshot_ref, "jar": jar_ref, "manifest": reference(manifest_path),
                    "verifiedPatchedClass": changed}
    same = same_hero_rules(snapshot, release)
    return {"name": name, "release": identity, "patch": patch_ref,
            "snapshotExportsHeroChanges": not same,
            "engine": engine_run(name, Path(log), Path(xml)),
            "offlineReplay": None if same else replay_rules(snapshot)}


def compare_night_replay(run, replay):
    scenarios = {row["scenario"]["scenarioId"]: row for row in replay["roles"]["yingxiao"]["scripted"]}
    comparison = []
    for event in run["events"]:
        if event["scenarioId"] != "yingxiao.backstab_21a34_finisher" or event["event"] != "case_result":
            continue
        archived = event["caseId"].endswith("_archived_inputs")
        name = event["scenarioId"] if archived else event["scenarioId"] + "_cherry_v2"
        cases = scenarios[name]["results"]
        for delay, model in cases.items():
            hits = {row["label"]: row["hpLeft"] for row in model["events"] if row["event"] == "hit"}
            observed = {key: event[key] for key in ("afterOpener", "afterFirstBasic", "afterSecondBasic")}
            predicted = dict(zip(observed, (hits.get(label) for label in ("4", "follow_a1", "follow_a2")), strict=True))
            comparison.append({"scenario": name, "caseId": event["caseId"], "delay": delay,
                               "fabricHp": observed, "offlineHp": predicted,
                               "offlineMinusFabricHp": {key: predicted[key] - value
                                   if predicted[key] is not None and value is not None else None
                                   for key, value in observed.items()}})
    return comparison


def build_report(args):
    before, baseline = paired_release(args.baseline)
    after, release = paired_release(args.release)
    baseline["publicValues"] = public_values(before)
    release["publicValues"] = public_values(after)
    economy = load(args.economy) if args.economy else None
    if economy:
        for key, pair in (("baseline", baseline), ("candidate", release)):
            if economy["snapshots"][key]["snapshotSha256"] != pair["snapshot"]["sha256"]:
                raise ValueError(f"The ledger does not use the {key} release snapshot")

    report = {
        "reportVersion": 1, "baseline": baseline, "release": release,
        "scenarioFiles": [reference(path) for path in sorted(SCENARIOS.glob("*.json"))],
        "combatRulesIdentical": same_hero_rules(before, after),
        "humanFeedback": HUMAN_CALIBRATION,
        "newHumanCalibration": None,
        "replay": replay_rules(after),
        "economy": {"report": reference(args.economy), "coverage": len(economy["cases"]),
                    "decisions": economy["decision"], "summary": {rail: run["closingStats"] for rail, run in economy["cases"]["S0"]["runs"].items()}}
                    if economy else None,
        "engine": [engine_run(label, Path(log), Path(xml)) for label, log, xml in args.engine],
        "measuredVariants": [measured_variant(name, pair, patch, log, xml, after)
                             for name, pair, patch, log, xml in args.variant],
        "decisions": load(ROLE_DECISIONS) if ROLE_DECISIONS.exists() else None,
    }
    report["nightComparisons"] = {
        run["label"]: compare_night_replay(run, report["replay"]) for run in report["engine"]
        if any(row["scenarioId"] == "yingxiao.backstab_21a34_finisher" for row in run["events"])
    }
    for trial in report["measuredVariants"]:
        report["nightComparisons"][trial["name"]] = compare_night_replay(
            trial["engine"], trial["offlineReplay"] or report["replay"])
    if not report["combatRulesIdentical"]:
        report["baselineReplay"] = replay_rules(before)
    return report


def summarize_events(rows, label, lines):
    for row in rows:
        case = row["caseId"]
        kind = row["event"]
        if kind not in ("summary", "case_result", "paired_delta"):
            continue
        data = {key: value for key, value in row.items()
                if key not in ("scenarioId", "scenarioVersion", "caseId", "event", "tick", "timeUnit")}
        lines.append(f"- {label} · `{case}` · `{kind}` · " + ", ".join(f"`{k}={v}`" for k, v in data.items()))


def markdown(report):
    release, baseline, replay = (report[key] for key in ("release", "baseline", "replay"))
    result = [
        "# 樱隙前线首轮 3v3 标杆复跑", "",
        f"- 现行对照 `{baseline['sourceSha']}` · 规则 `{baseline['rulesSha256']}`",
        f"- 本次构建 `{release['sourceSha']}` · 规则 `{release['rulesSha256']}`",
        f"- 本次 JAR SHA-256 `{release['jar']['sha256']}`",
        f"- 本次快照 SHA-256 `{release['snapshot']['sha256']}`",
        f"- 角色、购点收益及公共战斗规则与对照一致 `{report['combatRulesIdentical']}`",
        "",
        "## 公共选值与账本", "",
        "| 参数 | 对照 | 本次构建 |", "| --- | ---: | ---: |",
    ]
    names = {"defensePerPoint": "每点物防及法抗", "assistGold": "助攻金币池",
             "assistXp": "助攻经验池", "wageCatchUpWeight": "工资追赶权重"}
    for key, label in names.items():
        result.append(f"| {label} | {baseline['publicValues'][key]} | {release['publicValues'][key]} |")
    if report["economy"]:
        econ = report["economy"]
        result.extend(["", f"同场景账本 {econ['coverage']} 案，JSON SHA-256 `{econ['report']['sha256']}`。"
                       "六时点、席位收入与购物、等级及复活逐笔保存在账本。"])
        for name, row in econ["summary"].items():
            result.append(f"- 均势 {name} 二十分钟平均 Lv{row['avgLevel']:.2f}／"
                          f"{row['avgPoints']:.2f} 购点／{row['activeShare']:.1%} 主动收入。")
    else:
        result.extend(["", "账本未附入本次报告。"])
    result.extend(["", "## 四个引擎标杆", "",
                   "历史真人记录注明目标和当时表现。本轮 Fabric 使用合成环境和固定操作。"])
    for scenario, info in report["humanFeedback"].items():
        result.extend(["", f"### `{scenario}`", "", f"{info['expected']}。{info['feedback']}。[原始反馈]({info['source']})", ""])
        all_runs = report["engine"] + [trial["engine"] for trial in report["measuredVariants"]]
        matching = [run for run in all_runs if any(row["scenarioId"] == scenario for row in run["events"])]
        for run in matching:
            summarize_events((row for row in run["events"] if row["scenarioId"] == scenario), run["label"], result)
        if not matching:
            result.append("- 当前未附可核对的引擎事件。")
    if report["nightComparisons"]:
        result.extend(["", "### 夜影逐击离线与引擎对照", "",
                       "两边固定等级、分支、配点、起点和动作顺序。近战随机值、实际命中所在刻及原生回复由各自口径产生。",
                       "表中用常规延迟，零额外延迟及差值在 JSON。", "",
                       "| 执行／输入 | 来源 | 首套后 HP | 第一下补 A HP | 第二下补 A HP |",
                       "| --- | --- | ---: | ---: | ---: |"])
        for run, records in report["nightComparisons"].items():
            for row in records:
                if row["delay"] != "human_default":
                    continue
                for measure in ("offlineHp", "fabricHp"):
                    cells = " | ".join(f"{hp:.2f}" if hp is not None else "—" for hp in row[measure].values())
                    name = "离线" if measure == "offlineHp" else "Fabric"
                    result.append(f"| {run}／`{row['caseId']}` | {name} | {cells} |")
    result.extend(["", "### Fabric GameTest", "", "| 执行 | 状态 | XML 用例 |", "| --- | --- | --- |"])
    for run in report["engine"]:
        result.append(f"| {run['label']} | {run['status']} | {', '.join(run['testCases'])} |")
    if report["measuredVariants"]:
        result.extend(["", "### 独立候选构建", ""])
        for variant in report["measuredVariants"]:
            identity = variant["release"]
            snapshot_status = "已导出数值改动并重跑离线场景" if variant["snapshotExportsHeroChanges"] else "该候选路径由引擎事件覆盖，角色快照字段一致"
            result.append(f"- {variant['name']} · {variant['engine']['status']} · {snapshot_status}。"
                          f"JAR `{identity['jar']['sha256']}`，快照 `{identity['snapshot']['sha256']}`。")
    result.extend(["", "## L1 字段与 L3 确定性动作", "",
                   "每人八项技能、八套终局分支组合。表中为声明动作在这套合成场景实际模拟到的最高读数，"
                   "两档操作和所有逐刻事件见 JSON。受场景和机制覆盖限制，全面极值待测。", "",
                   "| 角色 | 模式 | 延迟 | 1.5 秒 HP | 6 秒 HP/秒 | 净控制行动秒 | 有效治疗加吸盾 HP |",
                   "| --- | --- | --- | ---: | ---: | ---: | ---: |"])
    for role, row in replay["roles"].items():
        for mode, delays in row["envelopeOfModeledReplay"].items():
            for delay, metrics in delays.items():
                values = " | ".join(f"{metrics[key]['max']:.2f}" for key in METRICS)
                result.append(f"| {role} | {mode} | {delay} | {values} |")
    result.extend(["", "## 3v3 多目标增量", "",
                   "数值用于三级试测提醒。最终联算使用原始 0～3 人分布与相同环境的移除技能基线。"
                   "伤害使用集火权重，控制与有效盾疗按实际影响逐人相加。", "",
                   "| 技能 | 场景 | 主目标／额外目标 1.5 秒 HP | 主目标／额外目标控制秒 | 主目标／额外目标盾疗 HP |",
                   "| --- | --- | ---: | ---: | ---: |"])
    for row in replay["multiTarget"]["observations"]:
        values = " | ".join(f"{row['firstTarget'][key]:.2f}／{row['additional'][key]:.2f}" for key in
                            ("burstHp", "controlActionSeconds", "effectiveSupportHp"))
        result.append(f"| {row['skill']} | {row['scene']} | {values} |")
    if report["decisions"]:
        result.extend(["", "## 四角色选值记录", ""])
        for role, decision in report["decisions"]["roles"].items():
            result.extend([f"### {role}", "", f"- 基础与成长 {decision['attributesReason']}",
                           f"- 普攻 {decision['basicReason']}"])
            for skill, row in decision["skills"].items():
                result.append(f"- `{skill}` · `{row['decision']}` · {row['reason']}")
            result.append("")
    result.extend(["", "## 体验验证", "",
                   "本构建的正式地图和六人真人样本待采。构建、游戏专项、离线推演与历史真人反馈各有来源。"
                   "平衡提醒用于策划复审。"])
    return "\n".join(result) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("baseline", "release", "output"):
        parser.add_argument(f"--{name}", type=Path, required=True)
    parser.add_argument("--economy", type=Path)
    parser.add_argument("--engine", nargs=3, action="append", default=[], metavar=("LABEL", "LOG", "XML"))
    parser.add_argument("--variant", nargs=5, action="append", default=[],
                        metavar=("LABEL", "PAIRED_RELEASE", "PATCH", "LOG", "XML"))
    args = parser.parse_args()
    report = build_report(args)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.with_suffix(".json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.output.with_suffix(".md").write_text(markdown(report), encoding="utf-8")


if __name__ == "__main__":
    main()
