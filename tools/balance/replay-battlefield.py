"""Pair the fixed battlefield scenarios with the two exported release snapshots."""
import argparse
import json
import re
from pathlib import Path


EVENT = re.compile(r"(?:MYRIAD_BATTLEFIELD_(LANE|MONSTER)|BATTLEFIELD_(BUILDING)) (\{.*\})$")
HERO = re.compile(r"\[(?:CHERRY-HERO-HP|CHERRY-WAVE)\] .*$")
MONSTER_STAT_KEYS = ("baseHealth", "baseAttack", "basePhysicalDefense", "baseMagicDefense")
REFERENCES = {
    "earlyClearSeconds": [6, 9], "middleClearSeconds": [3, 5],
    "smallCampSeconds": [5, 8], "smallCampHeroHpLostRatio": [0.15, 0.25],
    "mediumCampSeconds": [9, 13], "soloCrabSeconds": [18, 25],
    "partyCrabSeconds": [8, 13], "basicTowerCrossRoleRatio": 2,
}
GAPS = [
    "六人真人在樱隙前线的清线、清营、争蟹、末段推进与整局用时",
    "正式 5v5 部署世界实际设施、特殊资源怪和轮换首领的配置点位",
    "正式地图实跑路线、站位、争夺及双方交锋下的贡献",
]


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def value(fields, key):
    return fields[key]["value"]


def read_logs(paths):
    events = {"lane": [], "monster": [], "building": []}
    for path in paths:
        for line in path.read_text(encoding="utf-8").splitlines():
            match = EVENT.search(line)
            if match:
                group = (match[1] or match[2]).lower()
                events[group].append({"source": str(path), **json.loads(match[3])})
    return events


def hero_samples(path):
    return [match[0] for line in path.read_text(encoding="utf-8").splitlines() if (match := HERO.search(line))]


def sum_snapshot(snapshot):
    return {key: snapshot[key] for key in ("sourceSha", "sourceDirty", "rulesSha256")}


def map_camps(field):
    arena = field["maps"]["3v3"]
    return [{"id": key, "type": value(camp, "type"), "position": value(camp, "position")}
            for key, camp in arena["camps"].items()]


def effective_monsters(field):
    result = {}
    for name, definition in field["monsters"]["types"].items():
        row = {key: value(definition, key) for key in MONSTER_STAT_KEYS}
        row["physicalEffectiveHealth"] = row["baseHealth"] * (1 + row["basePhysicalDefense"] / 100)
        row["magicEffectiveHealth"] = row["baseHealth"] * (1 + row["baseMagicDefense"] / 100)
        row["definition"] = definition
        result[name] = row
    return result


def compare_minions(baseline, candidate):
    old = baseline["minions"]["types"]
    new = candidate["minions"]["types"]
    return {name: {"baselineBaseGold": old[name]["baseGold"], "candidateByTempo": rule["byTempo"],
                   "baseHealth": rule["baseHealth"], "baseAttack": rule["baseAttack"]}
            for name, rule in new.items()}


def build_report(args):
    baseline = load(args.baseline)
    candidate = load(args.candidate)
    old_hero = hero_samples(args.baseline_hero)
    new_hero = hero_samples(args.candidate_hero)
    fields = {name: snapshot["rules"]["battlefield"]
              for name, snapshot in (("baseline", baseline), ("candidate", candidate))}
    return {
        "baseline": sum_snapshot(baseline), "candidate": sum_snapshot(candidate),
        "referenceOnly": REFERENCES, "humanAndMapGaps": GAPS,
        "heroControl": {
            "rulesIdentical": baseline["rules"]["roles"] == candidate["rules"]["roles"],
            "actualDamageSamplesIdentical": bool(old_hero) and sorted(old_hero) == sorted(new_hero),
            "baseline": old_hero, "candidate": new_hero,
        },
        "minions": compare_minions(fields["baseline"], fields["candidate"]),
        "configuredCherryCamps": map_camps(fields["candidate"]),
        "monsters": {name: effective_monsters(field) for name, field in fields.items()},
        "objectiveRules": {name: {key: field[key] for key in ("towerTiers", "fountain", "crystal", "rotatingBosses")}
                           for name, field in fields.items()},
        "engine": read_logs(args.logs),
    }


def seconds(ticks):
    return f"{ticks / 20:.2f}" if ticks is not None else "—"


def time_label(ticks):
    minutes, remainder = divmod(ticks // 20, 60)
    return f"{minutes}:{remainder:02}"


def grouped_battlefield(lines, events, names, formatter):
    lines.extend(["", *names])
    lines.extend(formatter(event) for event in events)


def markdown(report):
    control = report["heroControl"]
    lines = [
        "# 樱隙前线战场对照",
        "",
        f"改前版本 `{report['baseline']['sourceSha']}`，候选版本 `{report['candidate']['sourceSha']}`。",
        f"英雄定义相同 {'是' if control['rulesIdentical'] else '否'}。"
        f"固定英雄专项读数相同 {'是' if control['actualDamageSamplesIdentical'] else '否'}，"
        f"改前与候选分别记录 {len(control['baseline'])} 和 {len(control['candidate'])} 行。",
        "",
        "参考范围用于提醒。场景以 20 游戏刻为一秒。",
        "",
        "## 兵线状态与已结算奖励",
    ]
    grouped_battlefield(lines, (r for r in report["engine"]["lane"] if r["scenario"] == "minion_death"),
        ["| 节奏 | 时点 | 波次 | 兵种 | 最大生命 | 攻击 | 补刀金币 | 经验 |",
         "| --- | --- | ---: | --- | ---: | ---: | ---: | ---: |"],
        lambda r: f"| {r['profile']} | {time_label(r['elapsedTicks'])} | {r['wave']} | {r['kind']} | "
                  f"{r['maxHealth']:.2f} | {r['attack']:.2f} | {r['gold']} | {r['experience']} |")
    grouped_battlefield(lines, (r for r in report["engine"]["lane"] if r["scenario"] == "clear_wave"),
        ["## 单人清线", "",
         "| 节奏 | 时点 | 英雄／等级／智慧 | 波次生命 | 清完秒数 | 金币 | 经验 |",
         "| --- | --- | --- | ---: | ---: | ---: | ---: |"],
        lambda r: f"| {r['profile']} | {time_label(r['elapsedTicks'])} | {r['hero']}／{r['heroLevel']}／{r['wisdom']} | "
                  f"{r['startWaveHealth']:.2f} | {seconds(r['finishTicks'] or None)} | {r['earnedGold']} | {r['earnedExperience']} |")
    grouped_battlefield(lines, report["configuredCherryCamps"],
        ["## 已提交的樱隙前线营地", "", "| 点位 | 实体类型 | 坐标 x / y / z |", "| --- | --- | --- |"],
        lambda r: f"| `{r['id']}` | `{r['type']}` | {r['position']['x']} / {r['position']['y']} / {r['position']['z']} |")
    grouped_battlefield(lines, (r for r in report["engine"]["monster"] if r.get("event") == "fight"),
        ["## 营地与裂隙蟹战斗", "",
         "| 节奏 | 场景 | 角色 | 等级 | 用时秒数 | 结束状态 | 英雄累计损血 |",
         "| --- | --- | --- | ---: | ---: | --- | --- |"],
        lambda r: f"| {r['profile']} | {r['scenario']} | {'、'.join(r['party'])} | {r['levelAtStart']} | "
                  f"{seconds(r['fightTicks'])} | {r['outcome']} | {r['grossHeroHealthLost']} |")
    grouped_battlefield(lines, (r for r in report["engine"]["building"] if r["scenario"] == "pure_basic_spread"),
        ["## 建筑推进", "", "| 节奏 | 对局时点 | 英雄等级 | 纯普攻最高／最低 | 参考线 |",
         "| --- | --- | ---: | ---: | ---: |"],
        lambda r: f"| {r['profile']} | {time_label(r['elapsedTicks'])} | {r['level']} | "
                  f"{r['maxToMin']:.2f}× | {r['referenceMaxToMin']}× |")
    grouped_battlefield(lines, (r for r in report["engine"]["building"] if r["scenario"] == "late_push_wave"),
        ["| 节奏 | 波次 | 建筑 | 该波秒数 | 英雄伤害 | 兵线伤害 | 余血 |",
         "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"],
        lambda r: f"| {r['profile']} | {r['wave']} | {r['tier']} | {seconds(r['waveEndTick'])} | "
                  f"{r['heroDamage']:.2f} | {r['laneDamage']:.2f} | {r['remainingHp']:.2f} |")
    lines.extend(["", "## 独立证据与人工缺口", "",
                  "JSON 保留各次营地、首领、建筑、泉水与胜利结算，附每行原始日志位置、实体定义和塔规则。", ""])
    lines.extend(f"- {gap}" for gap in report["humanAndMapGaps"])
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("baseline", "candidate", "baseline-hero", "candidate-hero", "output"):
        parser.add_argument(f"--{name}", type=Path, required=True)
    parser.add_argument("logs", type=Path, nargs="+")
    args = parser.parse_args()
    report = build_report(args)
    args.output.with_suffix(".json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.output.with_suffix(".md").write_text(markdown(report), encoding="utf-8")


if __name__ == "__main__":
    main()
