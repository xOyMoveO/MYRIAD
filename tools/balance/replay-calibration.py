"""Reproduce the #110 3v3 calibration from a paired release and attach independently run evidence."""

import argparse
from hashlib import sha256
import json
from pathlib import Path
from xml.etree import ElementTree
from zipfile import ZipFile

from combo_calibration import METRICS, SCENARIOS, load, replay_rules


EVIDENCE = "BALANCE_CALIBRATION "
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


def reference(path):
    return {"file": path.name, "sha256": sha256(path.read_bytes()).hexdigest()}


def paired_release(directory):
    manifest_path = directory / "release.json"
    manifest = load(manifest_path)
    jar_path = directory / manifest["file"]
    info = manifest["balance_snapshot"]
    snapshot_path = directory / info["file"]
    snapshot = load(snapshot_path)
    for path, expected in ((jar_path, manifest), (snapshot_path, info)):
        if path.stat().st_size != expected["bytes"] or reference(path)["sha256"] != expected["sha256"]:
            raise ValueError(f"Release checksum or size mismatch: {path}")
    if not (
        snapshot["sourceSha"] == manifest["source_sha"]
        and snapshot["sourceDirty"] == info["source_dirty"]
        and snapshot["formatVersion"] == info["format_version"]
        and snapshot["rulesSha256"] == info["rules_sha256"]
    ):
        raise ValueError(f"Snapshot source and rules disagree with release manifest: {directory}")
    with ZipFile(jar_path) as archive:
        if archive.read("META-INF/myriad-balance-snapshot.sha256").decode().strip() != info["sha256"]:
            raise ValueError(f"Packaged jar references a different snapshot: {jar_path}")
    return snapshot, {"sourceSha": snapshot["sourceSha"], "sourceDirty": snapshot["sourceDirty"],
                      "rulesSha256": snapshot["rulesSha256"], "snapshot": reference(snapshot_path),
                      "jar": reference(jar_path), "manifest": reference(manifest_path)}


def public_values(snapshot):
    rules = snapshot["rules"]
    gold = rules["battlefield"]["economy"]
    return {"defensePerPoint": rules["attributePurchases"]["permanentGain"]["value"]["defensePerPoint"],
            "assistGold": gold["assistPool"]["value"]["gold"], "assistXp": gold["assistPool"]["value"]["xp"],
            "wageCatchUpWeight": gold["passiveGoldCatchUpWeight"]["value"]}


def engine_run(label, log, xml):
    contents = log.read_text(encoding="utf-8")
    tests = ElementTree.parse(xml).findall(".//testcase")
    failed = [test.attrib["name"] for test in tests
              if any(test.find(tag) is not None for tag in ("failure", "error", "skipped"))]
    matching = [test.attrib["name"] for test in tests
                if f"Will run tests matching {test.attrib['name']} (1 tests)" in contents]
    success = len(tests) == 1 and bool(matching) and not failed and all(marker in contents for marker in
              ("1 GAME TESTS COMPLETE", "All 1 required tests passed", "BUILD SUCCESSFUL"))
    decoder = json.JSONDecoder()
    rows = [decoder.raw_decode(line.split(EVIDENCE, 1)[1])[0]
            for line in contents.splitlines() if EVIDENCE in line]
    return {"label": label, "status": "passed" if success else "failed_or_incomplete",
            "basis": "Fabric GameTest synthetic fixture", "stdout": reference(log), "xml": reference(xml),
            "testCases": [test.attrib["name"] for test in tests], "failedOrSkipped": failed, "events": rows}


def same_hero_rules(first, second):
    keys = ("roles", "attributePurchases", "skillRules")
    return all(first["rules"][key] == second["rules"][key] for key in keys)


def measured_variant(name, path, patch, log, xml, release):
    snapshot, identity = paired_release(Path(path))
    same = same_hero_rules(snapshot, release)
    return {"name": name, "release": identity, "patch": reference(Path(patch)),
            "snapshotExportsHeroChanges": not same,
            "engine": engine_run(name, Path(log), Path(xml)),
            "offlineReplay": None if same else replay_rules(snapshot)}


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
