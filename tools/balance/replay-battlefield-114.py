"""Report the actual full-roster GameTests against paired release rules."""

import argparse
from collections import Counter
import json
from pathlib import Path

from calibration_evidence import engine_run, paired_release
from roster_calibration import collect_engine


FILTERS = {
    "lane": "MYRIAD_BATTLEFIELD_LANE ",
    "camps": "MYRIAD_BATTLEFIELD_MONSTER ",
    "towers": "BATTLEFIELD_BUILDING ",
}
MINION_NAMES = {"melee": "近战兵", "ranged": "远程兵", "magic": "法兵", "siege": "攻城兵"}
MONSTER_NAMES = {
    "azure_horn_guardian": "苍角冰卫", "cherry_dusk_fox": "暮狐", "cherry_frost_hare": "霜兔",
    "cherry_heavy_walker": "重装步行机", "cherry_jade_beast": "翡翠兽", "cherry_rift_crab": "裂隙蟹",
    "cherry_scout_bot": "侦察机", "gale_roc_matriarch": "青岚鹏王", "glod_pig": "金钱猪",
    "frost_web_matriarch": "霜缚蛛后", "obsidian_tortoise_warden": "玄甲地龟",
    "spring_reef_guardian": "灵泉鲛卫", "vermilion_flame_tyrant": "朱炎兽王",
}
HISTORY_GUIDE = {
    109: "兵种边界与奖励、各层塔和泉水、水晶、普通营地、单／双／三人蟹、鹏王攻速",
    111: "伊珂、朔、铆钉、赤霄前轮",
    112: "天刑、素岚、焚诀前轮",
    113: "凌渊、寒泽、墨尘、云笙前轮",
}
DECISIONS = {
    "minions": "CHERRY_V3 沿用 V2 兵种生命、普攻、编制、成长和金币，兵线经验 ×3.6。完整轮转带己方兵线时，多名主清线角色在中期已进入 3–5 秒区间，"
               "前期亦出现低于 6 秒者。职业、配点、技能命中和兵线贡献会显著改变用时，降整波生命会一并加快已经偏快的样本。",
    "monsterCamps": "3v3 保留 V2 的营地与裂隙蟹战斗参数、节奏和金币，野怪经验 ×3.2。全阵容首刷单怪均测到反击、击杀、奖励和刷新。"
                    "耗时及净失血跨角色分散。继续以固定资源投入比较路线、复合营地和承伤，现有单点差异支持保留职业成本。",
    "experience": "3v3 经济复算显示中位等级仍偏低，选择只加兵线与野怪经验。实际追赶、共享、尾刀和取整沿用结算规则。",
    "rotatingBosses": "保留轮换首领及特殊资源怪定值。樱隙前线未配置这些点位，青岚鹏王的攻速已由 #109 独立抽检。"
                      "5v5 部署世界的点位与清场实测仍待输入。",
    "buildings": "保留建筑耐久、攻击、入伤保护和樱隙团队赏金。所有可造成塔伤的角色有兵时同窗约为空推的五倍；"
                 "伊珂普通炮弹两种条件的合法读数均为零。固定保护无法收窄不同英雄的相对贡献。",
    "luoxia": "按 #110 决议保留落霞普攻塔伤。合成读数和实图约 15% 的参考分别保留，等待实际落点、站位和防守节奏复测。",
    "yingxiao": "按 #110 决议保留夜影正式现值。限定绕背／完整连招的候选仍是隔离试验。",
}
GAPS = [
    "正式数值已接入，3v3 整局体验待验。",
    "合成站位、投点和输入优先级固定。正式地图站位、绕行、争夺、承伤与真人推进体感仍待验。",
    "清营是独立首刷单怪。联合营地拉怪、行进时间、实际后续周转与轮换首领清场需实图样本。",
    "建筑按合成三层推进。发布的樱隙点位采用外塔和高地塔，跨波固定等波含在合成时钟中。",
    "5v5 地图供给规则及模式验证另有配对证据。部署世界具体营地、首领与设施位置需读取该世界。",
]


def value(item):
    return item.get("value")


def same_or_change(before, formal):
    return {"before": before, "formal": formal, "same": before == formal}


def profile_minion(row, profile):
    common = ("baseHealth", "baseAttack", "attackRange", "attackRecovery")
    return {**{key: row[key] for key in common}, **row["byTempo"][profile]}


def profile_monster(row, profile):
    return {**{key: field for key, field in row.items() if key not in ("combatStatsByTempo", "fixedCampTiming")},
            "combatStats": row["combatStatsByTempo"][profile],
            "fixedCampTiming": row["fixedCampTiming"].get(profile, row["fixedCampTiming"])}


def read_history(root):
    if root is None:
        return {}
    history = {}
    folder = root / "109-artifacts" / "combined"
    _, identity = paired_release(folder / "paired")
    routes = {
        "battlefield_lane": FILTERS["lane"],
        "battlefield_buildings": FILTERS["towers"],
        **{f"battlefield_{name}": FILTERS["camps"] for name in (
            "small_medium_camps", "rift_crab", "rift_crab_duo", "rift_crab_trio", "peng_king")},
    }
    history[109] = {"release": identity, "scope": HISTORY_GUIDE[109], "directory": str(folder / "engine"), "filters": {
        name: engine_run(name, folder / "engine" / f"{name}.log", folder / "engine" / f"{name}.xml", marker)
        for name, marker in routes.items()}}
    for issue in (111, 112, 113):
        folder = root / f"{issue}-artifacts" / ("joint" if issue == 113 else "final")
        _, identity = paired_release(folder / "paired")
        prefix = "balance_roster" if issue == 111 else f"balance_roster_{issue}"
        history[issue] = {"release": identity, "scope": HISTORY_GUIDE[issue],
                          "directory": str(folder / "engine"), "filters": collect_engine(
            folder / "engine", {f"{prefix}_{name}": marker for name, marker in FILTERS.items()})}
    return history


def event_rows(engine, name, scenario, field="scenario"):
    return [row for row in engine[f"balance_roster_114_{name}"].get("events", []) if row.get(field) == scenario]


def index(rows, *fields):
    return {tuple(row[field] for field in fields): row for row in rows}


def current_events(engine):
    lane = event_rows(engine, "lane", "clear_wave")
    fixed = index((row for row in lane if row["variant"] == "clustered_q_basic_fixed_reengage"),
                  "hero", "elapsedTicks")
    full = index((row for row in lane if row["variant"] == "legal_rotation"), "hero", "elapsedTicks", "escorted")
    camp = event_rows(engine, "camps", "fight", "event")
    tower = index(event_rows(engine, "towers", "paired_tower_basics"), "hero", "elapsedTicks", "tier", "escorted")
    whole = index(event_rows(engine, "towers", "whole_wave"), "hero", "elapsedTicks", "tier")
    lane_pairs = [
        {"fixed": fixed.get((hero, tick)), "rotation": row, "rotationWithWave": full.get((hero, tick, True))}
        for (hero, tick, escorted), row in full.items() if not escorted
    ]
    tower_pairs = [
        {"empty": row, "escorted": tower.get((hero, tick, tier, True)), "wholeWave": whole.get((hero, tick, tier))}
        for (hero, tick, tier, escorted), row in tower.items() if not escorted
    ]
    return {
        "fixedLane": list(fixed.values()),
        "historicalYikeInputReplay": [row for row in lane if row["variant"] == "clustered_hero_only"],
        "rotationPairs": lane_pairs,
        "campFights": camp,
        "towerPairs": tower_pairs,
        "push": event_rows(engine, "towers", "late_push_wave"),
        "pushResult": event_rows(engine, "towers", "late_push_result"),
        "forcedPush": event_rows(engine, "towers", "forced_objective_path"),
    }



def paired_measurements(previous, formal):
    def pair(before_rows, after_rows, key, fields):
        before = {key(row): row for row in before_rows}
        return [{"case": key(row), "measured": {name: same_or_change(before[key(row)].get(name), row.get(name))
                                                for name in fields}}
                for row in after_rows if key(row) in before]

    def clear_rows(rows):
        return [*rows["fixedLane"], *rows["historicalYikeInputReplay"],
                *(entry for row in rows["rotationPairs"] for entry in (row["rotation"], row["rotationWithWave"]) if entry)]

    def tower_rows(rows):
        return [entry for row in rows["towerPairs"] for entry in (row["empty"], row["escorted"], row["wholeWave"]) if entry]

    return {
        "lane": pair(clear_rows(previous), clear_rows(formal),
                     lambda row: (row["hero"], row["elapsedTicks"], row["variant"], row.get("escorted", False)),
                     ("startWaveHealth", "finishTicks", "endWaveHealth", "earnedGold", "earnedExperience", "heroLevelAtEnd")),
        "camps": pair(previous["campFights"], formal["campFights"], lambda row: row["scenario"],
                      ("outcome", "fightTicks", "healthAtEnd", "respawnDelayTicks", "rewards")),
        "towers": pair(tower_rows(previous), tower_rows(formal),
                       lambda row: (row["scenario"], row["hero"], row["elapsedTicks"], row["tier"], row.get("escorted", False)),
                       ("heroDamage", "laneDamage", "percent")),
        "pushResult": pair(previous["pushResult"], formal["pushResult"], lambda row: row["scenario"],
                           ("elapsedSinceWaveEntry", "finished", "winner")),
    }

def build(args):
    previous, prev_id = paired_release(args.baseline)
    final, final_id = paired_release(args.release)
    old = previous["rules"]["battlefield"]
    now = final["rules"]["battlefield"]
    old_maps = old["maps"]
    now_maps = now["maps"]
    engine = collect_engine(args.engine, {f"balance_roster_114_{name}": marker for name, marker in FILTERS.items()})
    profiles = {mode: [value(maps[mode]["tempoProfile"]) for maps in (old_maps, now_maps)]
                for mode in ("3v3", "5v5")}
    previous_profile, formal_profile = profiles["3v3"]
    for evidence in engine.values():
        observed_profiles = sorted({row["profile"] for row in evidence.get("events", []) if "profile" in row})
        evidence["observedProfiles"] = observed_profiles
        if evidence["status"] == "passed" and observed_profiles != [formal_profile]:
            evidence["status"] = "passed_fixture_with_unmatched_release_profile"
    previous_events = {}
    if args.before_engine:
        previous_engine = collect_engine(args.before_engine, {f"balance_roster_114_{name}": marker
                                                               for name, marker in FILTERS.items()})
        previous_events = {"scope": "正式选择出炉前的 CHERRY_V2 全员合成引擎复跑",
                           "engine": previous_engine, "current": current_events(previous_engine)}
    replays = current_events(engine)
    camps = now_maps["3v3"]["camps"]
    points = Counter(value(row["type"]) for row in camps.values())
    minions = {kind: same_or_change(profile_minion(old["minions"]["types"][kind], previous_profile),
                                   profile_minion(new, formal_profile)) for kind, new in now["minions"]["types"].items()}
    monsters = {
        kind: {
            "3v3Points": points[kind],
            "profiles": {mode: same_or_change(profile_monster(old["monsters"]["types"][kind], selected[0]),
                                              profile_monster(new, selected[1]))
                         for mode, selected in profiles.items()},
        }
        for kind, new in now["monsters"]["types"].items()
    }
    buildings = {key: same_or_change(old[key], now[key]) for key in ("towerTiers", "fountain", "crystal")}
    return {
        "before": prev_id,
        "formal": final_id,
        "sameHeroDefinitions": previous["rules"]["roles"] == final["rules"]["roles"],
        "referenceOnly": {"earlyLaneSeconds": [6, 9], "middleLaneSeconds": [3, 5], "smallCampSeconds": [5, 8],
                          "mediumCampSeconds": [9, 13], "soloCrabSeconds": [18, 25], "partyCrabSeconds": [8, 13],
                          "luoxiaWholeOuterWavePercent": 15},
        "scenarios": {
            "battlefieldLane": f"{formal_profile}，3:00 Lv4／500 金／一购点，8:00 Lv10／2,400 金／四购点。"
                               "Q+A 固定力量与定点波次。轮转在路中央从满蓝零预热起手，释放 Q/W/E/R 和实际普攻，"
                               "两轨共用构筑到下一波前。无己方兵时目标固定，有己方兵时双方小兵按引擎推进和战斗，"
                               "兵线出伤和编排带来的站位变化都会进入对照。",
            "battlefieldCamps": f"{formal_profile}，Lv1／500 金／一力量／Q1 与本角色普攻。侦察机 0:20 首刷，步行机 0:40 首刷。"
                                "单独进战，真实野怪攻击、击杀奖励、检查点及刷新。各角色用了同一预算和固定可达站位。",
            "battlefieldTowers": f"{formal_profile}，3:00 Lv1 外塔／10:00 Lv10 中塔／18:00 Lv18 高地塔。六秒同窗对照"
                                 "固定进兵与空塔的真实英雄普攻伤害。整波回放额外启用兵线攻击、塔反击及每刻掉血归因。",
            "crystal": "18:00 携首波抵达外塔，Lv18 落霞／朔／青萝，合成三层并按 40 秒刷新截止送波。"
                       "英雄与小兵的正常输入实打至水晶，记录每波开始、结束、伤害、塔赏及胜负。",
        },
        "minions": minions,
        "minionSharedRules": same_or_change(
            {key: item for key, item in old["minions"].items() if key != "types"},
            {key: now["minions"][key] for key in old["minions"] if key != "types"}),
        "maps": {key: same_or_change(old_maps[key], now_maps[key]) for key in ("3v3", "5v5")},
        "profiles": profiles,
        "modeSupply": final["rules"].get("modeProfiles", {}),
        "monsters": monsters,
        "rotation": same_or_change(old["rotatingBosses"], now["rotatingBosses"]),
        "monsterGenerationGrowth": same_or_change(old["monsters"]["spawnGenerationGrowthSamples"],
                                                  now["monsters"]["spawnGenerationGrowthSamples"]),
        "buildings": buildings,
        "decisions": DECISIONS,
        "engine": {label: {**run, "directory": str(args.engine / label)} for label, run in engine.items()},
        "coverage": {
            "fixedLaneRoles": sorted({row["hero"] for row in replays["fixedLane"]}),
            "campRoles": sorted({role for row in replays["campFights"] for role in row["party"]}),
            "towerRoles": sorted({row["empty"]["hero"] for row in replays["towerPairs"]}),
            "fixedLaneCases": len(replays["fixedLane"]),
            "rotationPairs": sum(pair["rotationWithWave"] is not None for pair in replays["rotationPairs"]),
            "campFights": len(replays["campFights"]),
            "towerPairs": sum(pair["escorted"] is not None and pair["wholeWave"] is not None for pair in replays["towerPairs"]),
        },
        "current": replays,
        "previousEngine": previous_events,
        "pairedMeasurements": paired_measurements(previous_events["current"], replays) if previous_events else {},
        "history": read_history(args.history_root),
        "gaps": GAPS,
    }


def pair_label(left, right):
    return str(left) if left == right else f"{left} → {right}"


def compare_fields(pair, keys):
    before = [value(pair["before"][key]) for key in keys]
    after = [value(pair["formal"][key]) for key in keys]
    return pair_label("／".join(map(str, before)), "／".join(map(str, after)))


def clear_time(row):
    if row is None:
        return "缺事件"
    if row["finishTicks"]:
        return f"{row['finishTicks'] / 20:.2f}"
    return f"> {row['observedTicks'] / 20:.2f}（余 {row['endWaveHealth']:.0f} HP）"


def monster_row(kind, row):
    pair = row["profiles"]["3v3"]
    stats = same_or_change(pair["before"]["combatStats"], pair["formal"]["combatStats"])
    combat = compare_fields(stats, ("health", "attack"))
    defenses = compare_fields(pair, ("basePhysicalDefense", "baseMagicDefense"))
    timing_pair = same_or_change(pair["before"]["fixedCampTiming"], pair["formal"]["fixedCampTiming"])
    timing = compare_fields(timing_pair, ("firstSpawn", "respawnDelay")) if "firstSpawn" in timing_pair["formal"] else "轮换"
    bounty = compare_fields(pair, ("baseGold", "baseExperience")) if "baseGold" in pair["formal"] else "时点金／180"
    rift = row["profiles"]["5v5"]
    rift_combat = compare_fields(same_or_change(rift["before"]["combatStats"], rift["formal"]["combatStats"]), ("health", "attack"))
    return (f"| {MONSTER_NAMES.get(kind, kind)} | {row['3v3Points']} | {combat} | {rift_combat} | {defenses} | {timing} | {bounty} |")


def markdown(report):
    before, final = report["before"], report["formal"]
    lines = [
        "# 全阵容战场定稿",
        "",
        f"改前采用 #113 联合构筑 `{before['sourceSha']}`，正式配对构筑为 `{final['sourceSha']}`。"
        f"正式 JAR SHA-256 为 `{final['jar']['sha256']}`，快照 SHA-256 为 `{final['snapshot']['sha256']}`。"
        f"两版英雄定义{'一致' if report['sameHeroDefinitions'] else '有差异'}。",
        "",
        "以下每个值比较同一场景的直接改前与正式值。相同值合写，变化用箭头表示。#109 的 V1/V2 历史候选及引擎轨迹另附。",
        "",
        "## 兵线",
        "",
        report["decisions"]["experience"],
        "",
        report["decisions"]["minions"],
        "",
        "| 兵种 | 生命／攻击 | 基础补刀金／经验底值 | 生效方式 |",
        "| --- | ---: | ---: | --- |",
    ]
    conditions = {"melee": "普通波两只", "ranged": "普通波两只", "magic": "每三波替换一远程兵",
                  "siege": "该路敌塔全毁后替换一近战兵"}
    for kind, row in report["minions"].items():
        lines.append(f"| {MINION_NAMES[kind]} | {compare_fields(row, ('baseHealth', 'baseAttack'))} | "
                     f"{compare_fields(row, ('baseGold', 'baseExperience'))} | {conditions[kind]} |")
    lines.extend(["",
                  "樱隙 0:35 首波、每 40 秒一波，16 分钟后逐步加强生命和攻击。实跑 3:00 与 8:00 波次，成长、结算前奖励与 #109 边界实测在 JSON。",
                  "",
                  report["scenarios"]["battlefieldLane"],
                  "",
                  "| 角色 | 时点 | 固定 Q+A 秒 | 完整轮转秒 | 轮转带己方兵秒 | 己方兵伤害 HP |",
                  "| --- | --- | ---: | ---: | ---: | ---: |"])
    for pair in report["current"]["rotationPairs"]:
        row = pair["rotation"]
        escorted = pair["rotationWithWave"]
        damage = sum(hit["effectiveDamage"] for hit in escorted["healthLossEvents"] if hit["source"] == "lane_tick") if escorted else 0
        lines.append(f"| {row['hero']} | {row['elapsedTicks'] // 1200}:00 | {clear_time(pair['fixed'])} | "
                     f"{clear_time(row)} | {clear_time(escorted)} | {damage:.1f} |")
    lines.extend(["", "## 野区与首领", "", report["decisions"]["monsterCamps"], "", report["decisions"]["rotatingBosses"],
                  "", report["scenarios"]["battlefieldCamps"], "",
                  "| 实体 | 樱隙点数 | 3v3 首刷生命／攻击 | 5v5 规则首刷生命／攻击 | 双防 | 3v3 首刷／重生刻 | 基础金／经验 |",
                  "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"])
    for kind, row in report["monsters"].items():
        lines.append(monster_row(kind, row))
    lines.extend(["", "没有对应固定点时，固定刷新参数仅在配置后适用。轮换首领 8:00 首次尝试，成功生成后间隔 5 分钟再尝试。",
                  "实际尾刀归属、团队增益、刷新检查点和首次掉血事件见 JSON。",
                  "", "| 当前抽检 | 击杀 | 完成用时范围秒 | 最低结束生命 HP |",
                  "| --- | ---: | ---: | ---: |"])
    for monster, name in (("_scout", "侦察机"), ("_walker", "重装步行机")):
        fights = [row for row in report["current"]["campFights"] if row["scenario"].endswith(monster)]
        kills = [row for row in fights if row["outcome"] == "killed"]
        if kills:
            ticks = [row["fightTicks"] for row in kills]
            minimum = min(row["healthAtEnd"][0] for row in kills)
            lines.append(f"| {name} | {len(kills)}/{len(fights)} | {min(ticks) / 20:.2f}–{max(ticks) / 20:.2f} | {minimum:.1f} |")
    lines.extend(["", "## 防御塔、泉水和水晶", "", report["decisions"]["buildings"], "", report["decisions"]["luoxia"],
                  "", report["decisions"]["yingxiao"], "",
                  "| 目标 | 生命／攻击／基础团队金 | 适用条件 |",
                  "| --- | ---: | --- |"])
    towers = report["buildings"]["towerTiers"]
    for tier, name in (("1", "外塔"), ("2", "中塔"), ("3", "高地塔")):
        pair = same_or_change(towers["before"][tier], towers["formal"][tier])
        fields = compare_fields(pair, ("health", "attack", "baseTeamGoldPerRecipient"))
        lines.append(f"| {name} | {fields} | 同线外侧层级已毁，中塔用于合成参照 |")
    lines.extend([
        f"| 水晶 | {compare_fields(report['buildings']['crystal'], ('health', 'attack'))}／0 | 同路敌塔全毁可攻击，摧毁时结束对局 |",
        "| 泉水 | 每秒回复 10% 生命及法力／敌方 25% 最大生命真伤且至少 40 | 6 格内的己方英雄或合格敌方英雄 |",
        "",
        report["scenarios"]["battlefieldTowers"],
        "",
        "| 3:00 代表角色 | 空推六秒英雄伤害 | 带兵六秒英雄伤害 | 整波英雄／兵线伤害 | 整波掉血 |",
        "| --- | ---: | ---: | ---: | ---: |",
    ])
    for row in report["current"]["towerPairs"]:
        empty, escorted, whole = (row[key] for key in ("empty", "escorted", "wholeWave"))
        if empty["elapsedTicks"] == 3600 and escorted and whole:
            lines.append(f"| {empty['hero']} | {empty['heroDamage']:.1f} | {escorted['heroDamage']:.1f} | "
                         f"{whole['heroDamage']:.1f}／{whole['laneDamage']:.1f} | {whole['percent']:.2f}% |")
    lines.extend(["", report["scenarios"]["crystal"], ""])
    for row in report["current"]["pushResult"]:
        winner = {"guardian": "守护", "destruction": "毁灭"}.get(row.get("winner"), "未结算")
        lines.append(f"从首波抵达到结束共 {row['elapsedSinceWaveEntry'] / 20:.2f} 秒，胜方 {winner}。"
                     f"普通输入完成 {'是' if row['finished'] else '否'}。")
    lines.extend(["", "## 引擎复跑和证据", "",
                  "| 专项 | 结果 | 实际 XML 用例 |", "| --- | --- | --- |"])
    for label, row in report["engine"].items():
        lines.append(f"| `{label}` | {row['status']} | {len(row.get('testCases', []))} |")
    coverage = report["coverage"]
    lines.extend(["", f"收到固定清线 {coverage['fixedLaneCases']} 案、完整轮转清线 {coverage['rotationPairs']} 对、"
                  f"清营 {coverage['campFights']} 案、带兵／空推／整波 {coverage['towerPairs']} 组。"
                  "JSON 收录两条轨迹、实测输入、来源、历史发行摘要和逐事件原值。", ""])
    measurements = report["pairedMeasurements"]
    if measurements:
        lines.extend(["改前全员引擎轨迹按英雄、阶段、配点场景和护送状态配对。"
                      "逐项差异、实际取整与受阶段影响的等级见 JSON 的 pairedMeasurements。", "",
                      "| 实际奖励样本 | 改前经验 → 正式经验 |", "| --- | ---: |"])
        for row in measurements["lane"]:
            hero, stage, variant, escorted = row["case"]
            if hero == "yike" and variant == "legal_rotation" and escorted:
                xp = row["measured"]["earnedExperience"]
                lines.append(f"| 伊珂带兵清线 {stage // 1200}:00 | {pair_label(xp['before'], xp['formal'])} |")
        for row in measurements["camps"]:
            if row["case"] in ("chixiao_scout", "chixiao_walker"):
                rewards = row["measured"]["rewards"]
                xp = [award[0]["experience"] if award else "无奖励" for award in (rewards["before"], rewards["formal"])]
                lines.append(f"| 赤霄清{'侦察机' if row['case'].endswith('_scout') else '重装步行机'} | {pair_label(*xp)} |")
        lines.append("")
    for issue, row in report["history"].items():
        lines.append(f"- #{issue} `{row['release']['sourceSha'][:8]}`。{row['scope']}。")
    lines.extend(["", "## 实图与真人", ""])
    lines.extend(f"- {gap}" for gap in report["gaps"])
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("baseline", "release", "engine", "output"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    parser.add_argument("--before-engine", type=Path, help="Optional previous CHERRY_V2 full-roster engine replays")
    parser.add_argument("--history-root", type=Path,
                        help="Optional parent of the retained #109, #111, #112 and #113 artifacts")
    args = parser.parse_args()
    report = build(args)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "battlefield.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output / "battlefield.md").write_text(markdown(report), encoding="utf-8")


if __name__ == "__main__":
    main()
