"""Replay Tianxing, Sulan and Fenjue on the paired 3v3 balance release."""

from combo_calibration import SCENARIOS, Replay, case_report, load, named
from fenjue_calibration import FenjueReplayMixin
from roster_calibration import RosterSpec, collect_engine, main
from shuo_calibration import ShuoReplayMixin
from sulan_calibration import SulanReplayMixin
from tianxing_calibration import TianxingReplayMixin


class TianxingReplay(TianxingReplayMixin, Replay):
    pass


class SulanReplay(SulanReplayMixin, Replay):
    pass


class FenjueReplay(FenjueReplayMixin, Replay):
    pass


class ShuoReplay(ShuoReplayMixin, Replay):
    pass


NAMES = {"tianxing": "天刑", "sulan": "素岚", "fenjue": "焚诀", "shuo": "朔", "rivet": "铆钉"}
REPLAYS = {"tianxing": TianxingReplay, "sulan": SulanReplay, "fenjue": FenjueReplay, "shuo": ShuoReplay}
PRESSURE = [[4, "physical", 160], [24, "magic", 160], [44, "true", 90], [74, "physical", 180], [104, "magic", 180]]
STAGES = [
    (4, 500, {"strength": 1}, {"passive": 2, "q": 1, "w": 1, "e": 1, "r": 1}),
    (12, 3600, {"strength": 3, "wisdom": 3, "defense": 2}, {"passive": 4, "q": 3, "w": 3, "e": 4, "r": 2}),
    (20, 4885, {"strength": 4, "wisdom": 4, "defense": 2}, {"passive": 5, "q": 5, "w": 5, "e": 7, "r": 3}),
]


def augment(report, rules, defaults, configs, args):
    previous_aggregate = {}
    if args.baseline_engine:
        previous_aggregate = collect_engine(args.baseline_engine, {"first_group": None})
    roster = named(rules["roles"])
    rows = []
    for key in NAMES:
        config = configs[key] if key in configs else load(SCENARIOS / f"{key}.json")
        # The first legal row chooses A on each exclusive layer, Q and W are common.
        combination = roster[key]["legalBranches"]["terminalCombinations"][0]
        for level, budget, purchases, ranks_by_slot in STAGES:
            start = {
                "heroLevel": level, "budgetGold": budget, "distance": 2.4, "skillRanks": ranks_by_slot,
                "purchases": purchases, "targetRole": "luoxia",
                "targetPurchases": {"defense": purchases["defense"]} if "defense" in purchases else {},
                "casterIncoming": PRESSURE,
            }
            script = {"startState": start, "execution": config["execution"]}
            for condition in ("solo", "ally_cc"):
                modeled = case_report(rules, defaults, config, combination, condition, "human_default",
                                      script=script, replay_type=REPLAYS.get(key, Replay))
                rows.append({"role": key, **modeled})
    thunder = report["fabricGameTests"]["balance_tianxing"].get("events", [])
    report["stageComparison"] = {
        "scenario": "roster-112.stage_comparison",
        "basis": "固定同等级、同预算、同购点、同目标初始态与固定来伤。援用各角色优先序，均选各互斥层的 A。"
                 "L3 以 1.5 秒和 6 秒取伤害。L4 在同条件下依次注入各段来伤，独立观察实际生命损失。",
        "offline": rows,
        "engineIncoming": [row for row in thunder if row["event"] == "incoming_pressure"],
    }
    report["specialEvidence"] = {
        "tianxingContacts": [row for row in thunder if row["event"] != "incoming_pressure"],
        "windFire": report["fabricGameTests"]["balance_sulan_fenjue"].get("events", []),
        "windupTimeline": report["fabricGameTests"]["wind_cast_animation"].get("events", []),
        "fireSeaTimeline": report["fabricGameTests"]["fenjue_leap"].get("events", []),
        "priorAggregate": previous_aggregate,
    }


def render_extra(report):
    rows = report["stageComparison"]
    out = ["## 前期至同经济终局", "", rows["basis"], "",
           "| 角色 | 等级／预算 | 起手 | 1.5 秒伤害 HP | 6 秒伤害 HP/s | 6 秒来伤扣血 HP |",
           "| --- | --- | --- | ---: | ---: | ---: |"]
    for row in rows["offline"]:
        initial = row["initial"]
        out.append(f"| {NAMES[row['role']]} | Lv{initial['heroLevel']}／{initial['budgetGold']} 金 | {row['opponent']} | "
                   f"{row['burstHp']:.2f} | {row['sustainedHpPerSecond']:.2f} | {row['incomingHealthLostHp']:.2f} |")
    out.extend(["", "| 实际目标 | 等级 | 来伤条件 | 初始生命 | 实际累计掉血 | 剩余生命 |",
                "| --- | ---: | --- | ---: | ---: | ---: |"])
    for row in rows["engineIncoming"]:
        taken = sum(hit["healthLossHp"] for hit in row["hits"])
        out.append(f"| {NAMES[row['hero']]} | {row['level']} | {row['scenario']} | {row['startingHealthHp']:.2f} | "
                   f"{taken:.2f} | {row['remainingHealthHp']:.2f} |")
    out.extend(["", "## 天刑有效普攻", "",
                "| 事件 | 测试刻 | 剩余计数 | 主目标掉血 HP | 周围目标逐个掉血 HP |",
                "| --- | ---: | ---: | ---: | --- |"])
    for row in report["specialEvidence"]["tianxingContacts"]:
        splashes = "／".join(f"{hp:.2f}" for hp in row["otherTargetsHealthLossHp"])
        out.append(f"| {row['event']} | {row['tick']} | {row['markCount']} | {row['mainHealthLossHp']:.2f} | {splashes} |")
    out.extend(["", "## 素岚与焚诀引擎专项", "",
                "| 场景 | 有效分段 | 刻数 | 单段掉血 HP |",
                "| --- | --- | --- | --- |"])
    for event in report["specialEvidence"]["windFire"]:
        if "hits" not in event or "startWorldTick" not in event:
            continue
        grouped = {}
        for hit in event["hits"]:
            key = (hit["skill"] or "普攻", hit["direct"])
            grouped.setdefault(key, []).append(hit)
        for (skill, direct), hits in grouped.items():
            ticks = "／".join(str(hit["worldTick"] - event["startWorldTick"]) for hit in hits)
            losses = "／".join(f"{hit['hp']:.2f}" for hit in hits)
            out.append(f"| {event['scenario']} | {skill}（{'直接' if direct else '派生'}） | {ticks} | {losses} |")
    for event in report["specialEvidence"]["fireSeaTimeline"]:
        if event["scenario"] != "fenjue.sea_stay_leave":
            continue
        for target, label in (("primary", "主目标"), ("stay", "驻留目标"), ("leave", "脱离目标")):
            hits = event[target]
            first = hits[0]["tick"]
            ticks = "／".join(str(hit["tick"] - first) for hit in hits)
            losses = "／".join(f"{hit['hp']:.2f}" for hit in hits)
            out.append(f"| 焚天火海 | {label}，相对首个命中 | {ticks} | {losses} |")
    out.append("")
    return out


SPEC = RosterSpec(
    ticket=112,
    title="天刑、素岚与焚诀首轮平衡复跑",
    roles={key: NAMES[key] for key in ("tianxing", "sulan", "fenjue")},
    replays=REPLAYS,
    engine={
        "first_group": None,
        "wind_cast_animation": "MYRIAD_ROSTER_112 ",
        "fenjue_leap": "MYRIAD_ROSTER_112 ",
        "balance_tianxing": "MYRIAD_ROSTER_112_THUNDER ",
        "balance_sulan_fenjue": "MYRIAD_ROSTER_112 ",
        "balance_haste": "BALANCE_VERTICAL ",
    },
    battlefield_notes=[
        "清线按相同合法 Q 阶、等预算一或四点力量、普通攻击、固定四兵聚团与回位复跑。慢于策划参考时保留读数，己方兵协助、实际地图走位与常用连招列入全员复查。",
        "清营只采侦察机和重装步行机单怪。连续营地的走位、触发和搜敌用实际地图另验。",
        "带兵样本含一波4名真实兵与一名持初始武器英雄。中塔来自三档合成校准，正式樱隙前线采外塔及高地塔。",
    ],
    augment=augment,
    render_extra=render_extra,
)


if __name__ == "__main__":
    main(SPEC)
