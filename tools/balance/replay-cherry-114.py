"""Re-run the pinned 3v3 ledger and isolate legal route and map supply hypotheses."""

import argparse
from collections import defaultdict
from copy import deepcopy
from importlib import import_module
from math import ceil, dist
from statistics import median
import json
from pathlib import Path

from calibration_evidence import engine_run, paired_release, reference

ledger = import_module("replay-economy")
SCENARIOS = Path(__file__).with_name("scenarios")
TICKS = ledger.TICKS


def travel_ticks(first, second, route):
    return ceil(TICKS * dist(first, second) * route["pathLengthMultiplier"] / route["runBlocksPerSecond"])


def camp_route(rules, scenario, route, fixed):
    route_events, windows = [], defaultdict(list)
    camps = {key: row for key, row in rules.map["camps"].items() if key.startswith(("west_", "east_"))}
    ends = {}
    for key, row in camps.items():
        kind = ledger.value(row, "type")
        values = rules.monsters[kind]["fixedCampTiming"][rules.tempo]
        ends[key] = ledger.value(values, "firstSpawn")
    meeting = route["laneMeetingXZ"]
    reserved = defaultdict(list)
    for event in fixed:
        if "at" not in event:
            continue
        for seat in [event.get("player"), event.get("killer"), event.get("victim"), *event.get("assists", [])]:
            if seat:
                reserved[seat].append((event["at"] - route["reservedEventMarginSeconds"],
                                       event["at"] + route["reservedEventMarginSeconds"]))
    end = scenario["closingSeconds"]
    for team, prefix in (("G", "west_"), ("D", "east_")):
        quadrants = sorted(key for key in camps if key.startswith(prefix))
        for seat_number, first_second in route["raidStarts"].items():
            seat = team + seat_number
            negative_z = seat_number == "2"
            quadrant = []
            for camp in quadrants:
                point = ledger.value(camps[camp], "position")
                if (point["z"] < 0) == (negative_z if team == "G" else not negative_z):
                    quadrant.append((camp, tuple(point[coord] for coord in ("x", "z"))))
            for outset in range(first_second, end, route["raidIntervalSeconds"]):
                if any(abs(outset - shop) <= route["recallMarginSeconds"] for shop in scenario["shops"]):
                    continue
                deadline = min(end, outset + route["raidDurationSeconds"])
                nearby_reservations = sorted(
                    [(second - route["recallMarginSeconds"], second + route["recallMarginSeconds"])
                     for second in scenario["shops"]] + reserved[seat])
                for start, stop in nearby_reservations:
                    if start <= outset <= stop:
                        deadline = outset
                        break
                    if outset < start < deadline:
                        deadline = start
                        break
                trip_events, visit, tick = [], meeting, outset * TICKS
                while tick < deadline * TICKS:
                    options = []
                    for camp, point in quadrant:
                        if any(event["camp"] == camp for event in trip_events):
                            continue
                        arrival = max(ends[camp], tick + travel_ticks(visit, point, route))
                        unit = ledger.value(camps[camp], "type")
                        death = arrival + scenario["campFightTicks"][unit]
                        back = death + travel_ticks(point, meeting, route)
                        if back <= deadline * TICKS:
                            options.append((death, arrival, camp, point, unit))
                    if not options:
                        break
                    tick, arrival, camp, visit, unit = min(options)
                    timing = rules.monsters[unit]["fixedCampTiming"][rules.tempo]
                    wait = ledger.value(timing, "respawnDelay")
                    ends[camp] = ceil((tick + wait) / TICKS) * TICKS
                    trip_events.append({"type": "camp", "id": f"route-{seat}-{outset}-{camp}", "player": seat,
                                        "at": arrival / TICKS, "camp": camp})
                if trip_events:
                    route_events.extend(trip_events)
                    windows[seat].append((outset, (tick + travel_ticks(visit, meeting, route)) / TICKS))
    return route_events, dict(windows)


class RouteReplay(ledger.Replay):
    def __init__(self, scenario, rules, case, events, grade, variant, route):
        self.variant = variant
        scenario = deepcopy(scenario)
        scenario["campRotation"]["opportunitiesSeconds"] = []
        route_events, self.route_windows = camp_route(rules, scenario, route, events)
        super().__init__(scenario, rules, case, [*events, *route_events], grade)
        # Route windows already account for travel and the time spent fighting.
        self.trips = defaultdict(list, {seat: list(periods) for seat, periods in self.route_windows.items()})
        for event in events:
            if event["type"] == "camp":
                at = event["at"]
                player = event["player"]
                fight = scenario["campFightTicks"][ledger.value(self.camps[event["camp"]], "type")] / TICKS
                self.trips.setdefault(player, []).append((at - scenario["campRotation"]["laneMissBeforeSeconds"],
                                                          at + fight + scenario["campRotation"]["laneMissAfterSeconds"]))

    def farm_base(self, gold, xp, kind):
        if self.variant is None:
            return super().farm_base(gold, xp, kind)
        return (ledger.round_java(gold * self.variant[kind + "Gold"]),
                ledger.round_java(xp * self.variant[kind + "Xp"]))


def summarize(run, scenario):
    run["summary"] = {
        second: {**ledger.team_stats(list(states.values())),
                 "medianLevel": median(row["level"] for row in states.values()),
                 "medianPoints": median(row["points"] for row in states.values()),
                 "medianNormalRespawnSeconds": median(row["normalRespawnTicksAtLevel"] for row in states.values()) / TICKS}
        for second in scenario["seconds"] if (states := run["checkpoints"].get(second * TICKS))
    }
    run["campDeaths"] = sum(row["event"] == "camp" and "skip" not in row for row in run["trace"])
    return run


def escorted_timing(directory, scenario):
    evidence = engine_run("balance_roster_114_lane", directory / "stdout.txt", directory / "report.xml",
                          "MYRIAD_BATTLEFIELD_LANE ")
    if evidence["status"] != "passed" or evidence["testCases"] != ["myriad:balance_roster_114_lane"]:
        raise ValueError("No completed full-roster lane GameTest evidence")
    readings = defaultdict(list)
    for row in evidence.pop("events"):
        if row.get("variant") == "legal_rotation" and row.get("escorted"):
            readings[row["elapsedTicks"] // TICKS].append({key: row[key] for key in
                                                          ("hero", "finishTicks", "endWaveHealth", "heroDefeated")})
    offsets = []
    for begins, observation in scenario["laneClearObservationSeconds"]:
        wins = [row["finishTicks"] for row in readings[observation] if row["finishTicks"] > 0]
        if not wins:
            raise ValueError(f"Missing escorted lane completion at {observation} seconds")
        offsets.append([begins, ledger.round_java(median(wins))])
    return {**evidence, "timingByWaveSpawn": offsets, "observationsBySecond": dict(readings)}


def build(args):
    prior, prior_manifest = ledger.paired_release(args.frozen_battlefield_release)
    before, before_id = paired_release(args.previous_release)
    snapshot, identity = paired_release(args.release)
    scenario_path = SCENARIOS / "economy-cherry-v2.json"
    experiment_path = SCENARIOS / "economy-cherry-114.json"
    scenario, experiment = ledger.load(scenario_path), ledger.load(experiment_path)
    prior_rules = ledger.Rules(prior, "baseline", "CHERRY_V2", current=True)
    previous = ledger.Rules(before, "baseline", "CHERRY_V2", current=True)
    tempo = ledger.value(snapshot["rules"]["battlefield"]["maps"]["3v3"], "tempoProfile")
    rules = ledger.Rules(snapshot, "candidate", tempo, current=True)
    if tempo != "CHERRY_V3":
        raise ValueError("Final 3v3 report requires the CHERRY_V3 paired release")
    evidence = ledger.read_battlefield_evidence(args.frozen_battlefield, scenario, prior_rules, prior_manifest)
    scenario["seconds"] = experiment["checkpointsSeconds"]
    route_scenario = deepcopy(scenario)
    lane_timing = escorted_timing(args.lane_engine, scenario) if args.lane_engine else None
    if lane_timing:
        route_scenario["laneClearTicksByWaveSpawn"] = lane_timing["timingByWaveSpawn"]
    full, trials = {}, {}
    for id_, case, label, events, grade in ledger.scenario_matrix(scenario):
        control = summarize(ledger.Replay(scenario, previous, case, events, grade).run(), scenario)
        replay = ledger.Replay(scenario, rules, case, events, grade)
        run = summarize(replay.run(), scenario)
        full[id_] = {"label": label, "reminders": ledger.reminders(case, run), "before": control, "result": run}
        if id_ in scenario["fullCases"]:
            installed = RouteReplay(route_scenario, rules, case, events, grade, None, experiment["route"])
            official = summarize(installed.run(), scenario)
            official["routeWindowsBySeat"] = installed.route_windows
            trials[id_] = {"production": official}
            for variant in experiment["supplyTrials"]:
                model = RouteReplay(route_scenario, rules, case, events, grade, variant, experiment["route"])
                result = summarize(model.run(), scenario)
                result["routeWindowsBySeat"] = model.route_windows
                trials[id_][variant["id"]] = result
            if official != trials[id_]["target-xp-only"]:
                raise ValueError("The declared 3v3 experience multipliers differ from the selected trial")

    level18_xp = sum(rules.xp_thresholds[:17])
    end_tick = scenario["closingSeconds"] * TICKS
    nonactive_per_seat = rules.opening + rules.gold(rules.wage) * (end_tick // rules.wage_ticks)
    min_active = ceil(nonactive_per_seat * .6 / .4)
    min_income = nonactive_per_seat + min_active
    frontier = ledger.frontier(rules, min_income)["maximalVectorsAndRemainingGold"]
    balanced = max(sum(row[:len(ledger.ATTRIBUTES)]) for row in frontier)
    focused_ranks = [0] * len(ledger.ATTRIBUTES)
    spendable, specialization = min_income, (0, 0, 2, 4, 4, 0, 0, 1, 2, 4, 2, 4, 3, 1, 3, 1, 2)
    for channel in specialization:
        price = rules.price(focused_ranks[channel])
        if price > spendable:
            break
        spendable -= price
        focused_ranks[channel] += 1
    return {"formatVersion": 1, "previousRelease": before_id, "release": identity, "scenario": scenario,
            "scenarioSources": [reference(scenario_path), reference(experiment_path)],
            "experiment": experiment, "basis": "fixed-event offline replay",
            "frozenEarlierSyntheticTiming": evidence, "escortedLaneGameTest": lane_timing,
            "frozenRouteOfficialCases": full,
            "expandedRouteAndSupplyTrials": trials, "fullGameHumanSample": None,
            "defenseTrials": ledger.defense_trials(scenario, rules, full["S0"]["result"]),
            "progressionArithmetic": {"passiveAndOpeningGold": nonactive_per_seat, "activeFor60Percent": min_active,
                                      "grossAt60Percent": min_income, "maximumBeforeSupplies": balanced,
                                      "spendingExampleBeforeSupplies": dict(zip(ledger.ATTRIBUTES, focused_ranks)),
                                      "spendingExampleRemainder": spendable, "totalExperienceAtLevel18": level18_xp},
            "officialRules": {"wage": rules.gold(rules.wage), "wageTicks": rules.wage_ticks,
                              "wageCatchUpWeight": rules.wage_weight, "assistPool": rules.assist_pool,
                              "profile": tempo, "experiencePercents": rules.farm_xp_percent,
                              "defensePerPoint": rules.permanent["defensePerPoint"]}}


def markdown(report):
    lane_timing = report["escortedLaneGameTest"]
    if lane_timing:
        early, mid = (ticks / TICKS for _, ticks in lane_timing["timingByWaveSpawn"])
        route_line = f"扩展路线的清线延迟用本轮 GameTest 主清线位带兵成功样本中位数 {early:g}／{mid:g} 秒。"
    else:
        route_line = "扩展路线沿用历史合成清线延迟。"
    lines = ["# 樱隙前线 3v3 · 经济同输入复算", "",
             f"V2 对照配对提交 `{report['previousRelease']['sourceSha']}`，V3 正式配对提交 `{report['release']['sourceSha']}`。"
             "十四案使用两版共用的历史合成清线、清营与塔事件输入，原始版本和读数见 JSON。"
             "四条整局脚本另按营地坐标、刷怪截止、合成清营用时及步行时间扩展路线。"
             "按 4.2 格／秒及直线距离 ×1.15 换算通勤，离线轨迹缺实际通路、封锁与仇恨变化。"
             "参战与回城时间保留独立窗口。" + route_line, "",
             "| 剧本／路线与供给 | 20:00 营地尾刀（两队） | 18:00 → 20:00 中位等级 | "
             "18:00 → 20:00 中位购点（低价优先） | 18:00 → 20:00 主动收入 | 18:00 → 20:00 对应中位复活 |",
             "| --- | ---: | ---: | ---: | ---: | ---: |"]
    series = [("S0 冻结路线·V2", report["frozenRouteOfficialCases"]["S0"]["before"]),
              ("S0 冻结路线·V3", report["frozenRouteOfficialCases"]["S0"]["result"])]
    labels = {"production": "扩展路线·V3 正式", "official": "扩展路线·V2", "experience-only": "扩展路线·经验中间档",
              "lane-led": "扩展路线·兵线收益主调", "balanced-increase": "扩展路线·兵野共同加成",
              "target-xp-only": "扩展路线·已选经验试算", "target-pressure": "扩展路线·金币和经验共同提高"}
    for case, runs in report["expandedRouteAndSupplyTrials"].items():
        for id_, run in runs.items():
            if id_ != "target-xp-only":
                series.append((f"{case} {labels[id_]}", run))
    for label, run in series:
        mid, end = run["summary"][1080], run["summary"][1200]
        lines.append(f"| {label} | {run['campDeaths']} | {mid['medianLevel']:g} → {end['medianLevel']:g} | "
                     f"{mid['medianPoints']:g} → {end['medianPoints']:g} | {mid['activeShare']:.1%} → {end['activeShare']:.1%} | "
                     f"{mid['medianNormalRespawnSeconds']:g}s → {end['medianNormalRespawnSeconds']:g}s |")
    number = report["progressionArithmetic"]
    rank_sum = sum(number["spendingExampleBeforeSupplies"].values())
    lines += ["", f"在线 20 分钟的 500 初始金加工资合计 {number['passiveAndOpeningGold']:,} 金。"
              f"按相同分母，主动收入达到 60% 至少需要额外 {number['activeFor60Percent']:,} 金。"
              f"达到此收入时，补给前按最低价可买 {number['maximumBeforeSupplies']} 点；"
              f"按 JSON 中的专精购买顺序可买 {rank_sum} 点。"
              "购点观察须保留玩家购买策略及级差。", "",
              "| 试算 | 兵线金币／经验倍率 | 野区金币／经验倍率 |",
              "| --- | ---: | ---: |"]
    for row in report["experiment"]["supplyTrials"]:
        lines.append(f"| {labels[row['id']]} | {row['laneGold']:g}／{row['laneXp']:g} | "
                     f"{row['campGold']:g}／{row['campXp']:g} |")
    lines += ["", "已选经验试算与 V3 正式快照逐笔相符。兵线按原等级、时间和击杀归属算出经验整数后乘 3.6，"
              "野区基础经验乘 3.2，均四舍五入后继续按锁定参赛席位追赶。金币、工资、助攻、购点和英雄值沿用。"
              "其他倍率用于同路线比较。主动收入在正式配对脚本中仍低于 60%–70% 参考区间。", "",
              "**正式数值已接入，3v3 整局体验待验。**", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--previous-release", type=Path, required=True, help="paired CHERRY_V2 release before the experience change")
    parser.add_argument("--frozen-battlefield-release", type=Path, required=True)
    parser.add_argument("--frozen-battlefield", type=Path, required=True)
    parser.add_argument("--lane-engine", type=Path, help="full-roster lane GameTest stdout and XML directory")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = build(args)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "cherry-economy.md").write_text(markdown(report), encoding="utf-8")
    (args.output / "cherry-economy.json").write_text(
        json.dumps(report, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
