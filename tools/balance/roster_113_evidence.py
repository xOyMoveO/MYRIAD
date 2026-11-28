"""The #113 multi-target report and prior engine execution evidence."""

from calibration_evidence import engine_run, reference
from combo_calibration import SCENARIOS, load
from formation_calibration import FormationReplay, formation_branch_differences, multi_target_formation
from mage_support_calibration import XuanchenReplayMixin, YunshengReplayMixin
from sword_frost_calibration import SwordFrostReplayMixin


class SwordFrostReplay(SwordFrostReplayMixin, FormationReplay):
    pass


class XuanchenReplay(XuanchenReplayMixin, FormationReplay):
    pass


class YunshengReplay(YunshengReplayMixin, FormationReplay):
    pass


REPLAYS_113 = {"lingyuan": SwordFrostReplay, "hanze": SwordFrostReplay,
               "xuanchen": XuanchenReplay, "yunsheng": YunshengReplay}
ENGINE_113 = {
    "roster_a": None,
    "roster_b": None,
    "balance_haste": "BALANCE_VERTICAL ",
    "balance_roster_sword_frost": "MYRIAD_SWORD_FROST ",
    "balance_mage_support_113": "MYRIAD_ROSTER_113 ",
    "balance_roster_113_lane": "MYRIAD_BATTLEFIELD_LANE ",
    "balance_roster_113_camps": "MYRIAD_BATTLEFIELD_MONSTER ",
    "balance_roster_113_towers": "BATTLEFIELD_BUILDING ",
}


def first_engine(directory):
    expected_file = directory / "source.json"
    runs = {}
    for label, marker in ENGINE_113.items():
        log, xml = directory / label / "stdout.txt", directory / label / "report.xml"
        if log.is_file() and xml.is_file():
            row = engine_run(label, log, xml, marker)
            if row["testCases"] != [f"myriad:{label}"]:
                row["status"] = "failed_or_incomplete"
            runs[label] = row
        else:
            runs[label] = {"label": label, "status": "not_available"}
    return {"sources": load(expected_file) if expected_file.is_file() else None, "runs": runs}


def formation_evidence(rules, defaults, configs, baseline_engine=None):
    common = SCENARIOS / "multi-target.json"
    local = SCENARIOS / "multi-target-113.json"
    scenarios = {**load(common), **load(local)}
    rows = multi_target_formation(rules, defaults, configs, scenarios, REPLAYS_113)
    return {"scenario": reference(local), "assumptions": reference(common), "inputs": scenarios,
            "rows": rows, "sameLayerAdditional": formation_branch_differences(rules, rows),
            "firstRound": first_engine(baseline_engine) if baseline_engine else None}


def formation_markdown(report):
    formation = report["formation"]
    lines = ["## 3v3 范围、净控制与受援", "",
             "使用分散拉扯、集中交战和队友先控三组原型条件。每条结果保留 1／2／3 人的生效与移除技能轨迹。"
             "混合技能分开记录敌人与队友的读数。法师自身作为第三档低承压受援人。", "",
             f"{len(formation['rows'])} 组场景与构筑配对，{len(formation['sameLayerAdditional'])} 组同层范围分支比较。"
             "JSON 保留额外目标前后的各项贡献、8% 参考差及分项读数。", "",
             "| 技能 | 场景 | 1／2／3 人伤害（HP/s） | 1／2／3 人净控制（人·秒） | 1／2／3 人有效盾疗与减伤（HP） |",
             "| --- | --- | --- | --- | --- |"]
    rendered = set()
    for row in formation["rows"]:
        key = (row["skill"], row["scene"])
        if key in rendered:
            continue
        rendered.add(key)
        columns = ["／".join(f"{outcome['raw'][metric]:.2f}" for outcome in row["outcomes"])
                   for metric in ("sustainedHpPerSecond", "controlActionSeconds", "effectiveSupportHp")]
        lines.append(f"| `{row['skill']}` | {row['scene']} | {' | '.join(columns)} |")
    lines.extend(["", "表中每项显示对应场景的首套合法组合。其它组合及逐人治疗、溢出、实际吸盾、减伤见 JSON。"
                  "传送、范围加速的后续命中变化依赖交战路径，列在各自覆盖缺口。", ""])
    if formation["firstRound"]:
        lines.extend(["## 首轮服务端记录", "", "| 过滤器 | 首轮执行 | 事件数 |", "| --- | --- | ---: |"])
        for label, run in formation["firstRound"]["runs"].items():
            lines.append(f"| `myriad:{label}` | {run['status']} | {len(run.get('events', []))} |")
        lines.append("")
    return lines
