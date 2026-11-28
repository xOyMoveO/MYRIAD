"""Run the offline prototype from its archived snapshot. Requires Python 3.10+."""

import argparse
import hashlib
import json
from datetime import date
from itertools import product
from math import ceil
from pathlib import Path

from combat import AXES, CORRIDORS, LIMITS, simulate
from economy import ATTRS, NAMES, attributes, builds, ledger, skill_value, solve, support_value

HERE = Path(__file__).resolve().parent
BITS = tuple(product((0, 1), repeat=3))
MODES = ("当前", "候选")
SCENES = ("独自出手", "队友先控")


def label(bits):
    return "".join("AB"[i] for i in bits)


def tower(rules, observed):
    base = rules["baseAttributes"]
    bow = rules["roles"]["luoxia"]["attack"] * 20 / rules["basicDraw"]
    normal = rules["mageBasic"] * 20 / ceil(20 / base[2])
    hp = rules["inline"]["outerTowerHp"]
    rows = []
    for minute in (3, 8, 12):
        for wave in (True, False):
            factor = rules["towerScale"][minute] * (1 if wave else rules["inline"]["noWave"])
            trial = next(row for row in observed if (row["minute"], row["wave"]) == (minute, wave))
            predicted = [rules["roles"]["luoxia"]["attack"] * factor, rules["mageBasic"] * factor]
            tower_hits = [trial["luoxia"], trial["qingluo"]]
            rows.append(
                dict(
                    modelHits=predicted,
                    engineHits=tower_hits,
                    matches=[abs(a - b) < 0.002 for a, b in zip(predicted, tower_hits)],
                    minute=minute,
                    wave=wave,
                    bow=round(bow * factor, 2),
                    normal=round(normal * factor, 2),
                    bowSecs=round(
                        ceil(hp / (rules["roles"]["luoxia"]["attack"] * factor)) * rules["basicDraw"] / 20,
                        2,
                    ),
                    mageSecs=round(ceil(hp / (rules["mageBasic"] * factor)) * ceil(20 / base[2]) / 20, 2),
                )
            )
    return dict(bow=round(bow, 2), normal=round(normal, 2), ratio=round(bow / normal, 4), rows=rows)


def experiment(rules, evidence, configs, today):
    ref = [0, LIMITS["keheng"][2]]
    record = next(
        (
            x
            for x in configs
            if (x["role"], x["skill"], x["metric"], x["scenario"])
            == ("keheng", "keheng_yimiao", "lostActionSeconds", "rank3-stationary-single-target-6s")
        ),
        None,
    )
    rows = {}
    for variant in ("current", "manual"):
        pair = evidence[variant]
        measurement = pair["evidence"]["dome"]
        inline = rules["inline"] if variant == "current" else evidence["manualInline"]
        warning, duration, delta = inline["dome"]
        base, step = inline["domeFlight"]
        rank = measurement["rank"]
        rate = base - step * (rank - 1) - delta
        pulse = inline["domePulse"]
        spec = rules["roles"]["keheng"]["skills"][6]
        stats = attributes(rules, "keheng", (0,) * 5, False)
        damage = skill_value(spec["damage"][0], rank, stats)
        expected = {
            i: (rate if i < warning + duration else 1, (i - warning) // pulse * damage)
            for i in [warning, *range(warning + pulse, warning + duration + 1, pulse)]
        }
        expected[warning + duration] = (1, duration // pulse * damage)
        observed = {x["tick"]: (x["rate"], x["hpLost"]) for x in measurement["events"]}
        mismatches = [
            t
            for t in sorted(set(expected) | set(observed))
            if t not in expected
            or t not in observed
            or any(abs(x - y) > 0.002 for x, y in zip(expected[t], observed[t]))
        ]
        contract = (
            abs(measurement["manaBefore"] - measurement["manaAfter"] - spec["mana"][rank - 1]) < 0.002
            and measurement["cooldown"] == spec["cooldown"][rank - 1]
        )
        if not contract:
            mismatches.insert(0, 0)
        # Integrate observed actor rates, independently of the predicted source parameters.
        times = [
            (0, 1),
            *((x["tick"], x["rate"]) for x in measurement["events"] if x["tick"] < 120),
            (120, 1),
        ]
        loss = sum((b - a) * (1 - v) / 20 for (a, v), (b, _) in zip(times, times[1:]))
        observed_hits = sum(
            y["hpLost"] > x["hpLost"]
            for x, y in zip([{"hpLost": 0}] + measurement["events"], measurement["events"])
        )
        messages = []
        if mismatches:
            messages.append("一级 · 逐刻对照出现差异")
        if loss > ref[1]:
            messages.append("三级 · 超过灵巧战士单目标试测线")
        if record:
            if loss <= ref[1]:
                messages.append("已回到常规范围，可清理记录")
            elif not record["overrideRange"][0] <= loss <= record["overrideRange"][1]:
                messages.append("超出已记区间，建议复审")
            if loss > ref[1] and date.fromisoformat(record["reviewAt"]) <= today:
                messages.append("复审日期已到")
        elif loss > ref[1]:
            messages.append("无设计例外记录")
        if observed_hits != spec["damage"][0]["maxHits"]:
            messages.append("一级 · 伤害段声明次数与实际脉冲数不一致")
        rows[variant] = dict(
            seconds=round(loss, 3),
            hits=observed_hits,
            hp=measurement["events"][-1]["hpLost"] if measurement["events"] else 0,
            signals=messages,
            expectedHits=spec["damage"][0]["maxHits"],
            firstMismatch=mismatches[0] if mismatches else None,
            build=pair["build"],
            fixture=pair["serverFixture"],
            ticks=[warning, warning + duration],
            events=measurement["events"],
            sampleCount=len(set(expected) | set(observed)),
            jarHash=pair["jarSha256"],
        )
    return dict(reference=ref, record=record, rows=rows)


def micro(rules, actual):
    base = attributes(rules, "yingxiao", (1, 0, 0, 0, 0), False)
    night = rules["roles"]["yingxiao"]["skills"]
    q = night[2]["damage"][0]
    shield = night[7]["support"][0]
    raw = skill_value(q, 1, base)
    bonus = base["ad"] * 0.15
    herb = rules["roles"]["qingluo"]["skills"]
    s = attributes(rules, "qingluo", (0, 0, 0, 1, 0), False)

    def use_shield(resist):
        left = support_value(herb[5]["support"][0], 1, s)
        used = []
        for factor in (100 / (100 + resist), 100 / (100 + resist), 1):
            post = 120 * factor
            absorbed = min(left, post)
            used.append([post, absorbed])
            left -= absorbed
        return used

    joined = []

    def row(key, name, predicted, candidate=None):
        observed = actual[key]
        joined.append(
            dict(
                name=name,
                model=round(predicted, 3),
                engine=round(observed, 3),
                candidate=None if candidate is None else round(candidate, 3),
                match=abs(predicted - observed) <= 0.002,
            )
        )

    row("bowHit", "落霞普通满拉一箭 · 零抗目标", rules["roles"]["luoxia"]["attack"])
    row("nightFront", "夜影一阶「影杀」 · 一力量，正面", raw)
    row(
        "nightBuffedFront",
        "同一技能 · 有效物攻再加 15%",
        skill_value(q, 1, base, False, bonus),
        skill_value(q, 1, base, True, bonus),
    )
    row("nightBack", "同一技能 · 切到背面", raw * (1 + rules["inline"]["backstab"][0] / 100))
    row(
        "nightShield",
        "夜影一阶「灵盾」 · 一力量",
        support_value(shield, 1, base),
        support_value(shield, 1, base) + shield["spellScale"] * 20,
    )
    row(
        "herbRealHeal",
        "青萝一阶「青藤玉露」 · 只缺 50 血",
        min(50, support_value(herb[2]["support"][0], 1, s)),
    )
    row("herbShield", "青萝一阶「千丝渡厄」 · 一防御", support_value(herb[5]["support"][0], 1, s))
    mixed = use_shield(s["resist"])
    candidate = use_shield(attributes(rules, "qingluo", (0, 0, 0, 1, 0), True)["resist"])
    for i, hit in enumerate(actual["herbMixed"]):
        for j, (k, unit) in enumerate((("post", "减伤后"), ("absorbed", "实际耗盾"))):
            v = mixed[i][j]
            joined.append(
                dict(
                    name="青萝 · " + ("物理", "法术", "真伤")[i] + " 120 / " + unit,
                    model=round(v, 3),
                    engine=round(hit[k], 3),
                    candidate=round(candidate[i][j], 3),
                    match=abs(v - hit[k]) <= 0.002,
                )
            )
    return joined


def static_terms(hero, ranks, ids, stats, candidate, href, resist):
    # Keep alternative hits (e.g. first hero versus second pierced hero) as separate terms.
    # maxHits describes a segment; it never licenses adding every row together.
    terms = []
    for index in ids:
        skill = hero["skills"][index]
        rank = ranks.get(index, 0)
        if not rank:
            continue
        for f in skill["damage"]:
            raw = skill_value(f, rank, stats, candidate)
            effective = raw if f["kind"] == "TRUE" else raw * 100 / (100 + resist)
            terms.append(
                dict(
                    skill=skill["name"],
                    term=f["labelKey"] or f["kind"],
                    kind="伤害",
                    raw=round(raw, 3),
                    maxHits=f["maxHits"],
                    onHitPoints=round(100 * effective / href, 3),
                )
            )
        for f in skill["support"]:
            amount = support_value(f, rank, stats)
            if candidate and skill["id"] == "g1_yingxiao_ub":
                amount = (
                    support_value(f, rank, {"ap": stats["ap"] - stats["extraAP"]})
                    + f["spellScale"] * stats["extraAD"] * 20 / 15
                )
            terms.append(
                dict(
                    skill=skill["name"],
                    term=f["effect"],
                    kind="治疗" if f["effect"] == "heal" else "护盾",
                    raw=round(amount, 3),
                    maxHits=None,
                    onHitPoints=None,
                )
            )
    return terms


def defender_build(rules, gold):
    return max(
        builds(rules["price"], gold),
        key=lambda x: (min(x[0]), -max(x[0]) + min(x[0]), sum(x[0]), x[0][3], x[1]),
    )


def roster_audit(rules, curves):
    roles = {}
    for role in NAMES:
        roles[role] = {}
        for mode, candidate in zip(MODES, (False, True)):
            solved = solve(rules, role, curves, candidate)
            info = dict(economy=solved, stages={})
            roles[role][mode] = info
            g = curves[-1]["income"]
            balanced = defender_build(rules, g)
            info["defender"] = dict(points=balanced[0], spent=balanced[1], left=g - balanced[1])
            for level in sorted({curves[-1]["level"], 12, 20}):
                variants = {}
                for bits in BITS:
                    indices = [bits[0], 2, 3, 4 + bits[1], 6 + bits[2]]
                    sims = {}
                    for assisted, scene in enumerate(SCENES):
                        sims[scene] = simulate(
                            rules, role, bits, level, solved["points"], balanced[0], candidate, assisted
                        )
                    stats = attributes(rules, role, solved["points"], candidate)
                    resist = attributes(rules, "luoxia", balanced[0], candidate)["resist"]
                    terms = static_terms(
                        rules["roles"][role],
                        sims[SCENES[0]]["ranks"],
                        indices,
                        stats,
                        candidate,
                        rules["roles"]["luoxia"]["hp"][level - 1],
                        resist,
                    )
                    variants[label(bits)] = dict(
                        terms=terms,
                        ids=[rules["roles"][role]["skills"][i]["id"] for i in indices],
                        names=[rules["roles"][role]["skills"][i]["name"] for i in indices],
                        scenes=sims,
                    )
                envelope = []
                for j, limit in enumerate(LIMITS[role]):
                    best = max(
                        (sim["metrics"][j], key, scene)
                        for key, v in variants.items()
                        for scene, sim in v["scenes"].items()
                    )
                    envelope.append(
                        dict(
                            value=best[0],
                            branch=best[1],
                            scene=best[2],
                            limit=limit,
                            evidence="单目标固定脚本的试算峰值",
                            status="未到筛查等级"
                            if level < 12
                            else "试测越线"
                            if best[0] > limit
                            else "本组未越线",
                        )
                    )
                info["stages"][level] = dict(branches=variants, envelope=envelope)
                if level != 20:
                    continue
                comparisons = []
                for dim in range(3):
                    for a in (b for b in BITS if b[dim] == 0):
                        b = list(a)
                        b[dim] = 1
                        removed = (a[0], 4 + a[1], 6 + a[2])[dim]
                        common = (
                            sum(
                                simulate(
                                    rules,
                                    role,
                                    a,
                                    level,
                                    solved["points"],
                                    balanced[0],
                                    candidate,
                                    c,
                                    exclude=removed,
                                )["budget"]
                                for c in (False, True)
                            )
                            / 2
                        )
                        av, bv = [
                            sum(v["budget"] for v in variants[label(bits)]["scenes"].values()) / 2 - common
                            for bits in (a, b)
                        ]
                        mean = (av + bv) / 2
                        comparisons.append(
                            dict(
                                layer=("被动", "E", "R")[dim],
                                a=label(a),
                                b=label(b),
                                aNet=round(av, 3),
                                bNet=round(bv, 3),
                                gap=round(abs(av - bv) * 100 / mean, 2) if mean > 1e-6 else None,
                            )
                        )
                info["branchMargins"] = comparisons
    return roles


def markdown(data):
    current = data["experiment"]["rows"]["current"]
    manual = data["experiment"]["rows"]["manual"]
    last = data["curve"][-1]
    rotation = data["rotation"][-1]
    probes = data["probes"]
    tower_matched = sum(sum(row["matches"]) for row in data["tower"]["rows"])
    client = data["client"]
    client_line = (
        f"手改 JAR 已在独立 Minecraft 26.3 客户端进入新训练场。原生 R 和左键确认后，法力从 {client['before']['mana']:g} 降到 {client['after']['mana']:g}，读回 R 冷却 {client['after']['cooldowns'][3]} 刻，结束时持有输入为 {client['heldAtEnd']}。"
        if client
        else "该 JAR 的客户端试玩记录待采集。"
    )
    top = [
        "# 万象 3v3 平衡标杆原型",
        "",
        "使用樱隙前线的实际规则和一条假设兵线路线。策划可以直接打开同目录的 benchmark.html，切换角色、终局分支、购物时点和场景。",
        "",
        "|核对项|结果|证据|",
        "|---|---|---|",
        f"|落霞与青萝零购普攻|{data['tower']['bow']} / {data['tower']['normal']}，比值 {data['tower']['ratio']:.2f}×；拆塔参考上限 2×|持续输出按完整节拍推算。三个时点、两种兵线条件的合成 3v3 外塔逐击对照 {tower_matched}/12|",
        f"|夜影一阶「影杀」|一力量正面 {probes[1]['engine']:g}。叠加 15% 有效物攻后为 {probes[2]['engine']:g}，候选口径为 {probes[2]['candidate']:g}。背面 {probes[3]['engine']:.1f}|独立施放的服务端记录与公式对照|",
        f"|青萝「千丝渡厄」|一阶 {probes[6]['engine']:g} 盾，一点防御。三次减伤前 120 的物、法、真伤实际消耗 {probes[8]['engine']:g}、{probes[10]['engine']:g}、{probes[12]['engine']:g} 盾。Q 在只缺 50 血时实回 {probes[5]['engine']:g}|服务端治疗、减伤和耗盾路径|",
        f"|刻衡三阶「一秒之外」|原版 {current['seconds']:.2f} 秒行动损失、{current['hits']} 跳。手改后 {manual['seconds']:.2f} 秒、{manual['hits']} 跳|两次实际构建及合成世界施放。后者的伤害段声明仍是 6 跳|",
        f"|20 分钟三人同线|该角色累计 {last['income']:,} 金、{last['xp']:,} 经验，Lv{last['level']}。常规目标为 Lv18–20|逐笔经济场景推演，三人轮流补刀、零击杀及野区收益|",
        f"|增加轮转清野|同一角色 {rotation['income']:,} 金、{rotation['xp']:,} 经验，Lv{rotation['level']}|清营期间移除离线兵线收益；属于路线敏感性对照|",
        "",
        client_line,
        "",
        "## 四个角色的终局筛查",
        "",
        "下表为强制 Lv20、持有同一笔 20 分钟收入、当前规则下两条固定脚本的逐列最大值。百分比使用同级参考脆皮。分支按被动、E、R 顺序显示。",
        "",
        "|角色|1.5 秒爆发|6 秒均伤|6 秒控制|6 秒盾疗 EHP|独自出手 TTK 范围|",
        "|---|---:|---:|---:|---:|---|",
    ]
    for role, name in NAMES.items():
        stage = data["roles"][role]["当前"]["stages"][20]
        vals = [
            f"{e['value']:.2f}{unit} ({e['branch']})"
            for e, unit in zip(stage["envelope"], ("%", "%/秒", "秒", "%"))
        ]
        times = [b["scenes"]["独自出手"]["ttk"] for b in stage["branches"].values()]
        found = [v for v in times if v is not None]
        timer = (
            (f"{min(found):g} 秒" if min(found) == max(found) else f"{min(found):g}–{max(found):g} 秒")
            if found
            else "60 秒内未达"
        ) + ("，部分 >60 秒" if found and len(found) < 8 else "")
        top.append("|" + "|".join([name, *vals, timer]) + "|")
    top += [
        "",
        "## 采用口径",
        "",
        "四列逐列比较八种分支。有限脚本找到的峰值是模型场景值，实际连招、命中率和空间收益另待对局核实。单目标外的收益不计入；夜影隐身、撤退路线等缺少对手反应数据的收益留空。受援者起始为 55% 生命，6 秒内按固定顺序承受六次来伤。双抗、用掉的盾、实际补回并再次消耗的血分别计算。",
        "",
        "L2 穷举五项非负整数点数，用各角色指定的 60 秒单动作收益乘有效生命选择配点。每项报价独立递增，回泉时只购买最终配点所需的点数。独立配点最优只适用于所列目标。L3 使用这条可达购买路径的终点，再分别运行经济场景等级、强制 Lv12 与 Lv20。每条连招记录起手、付款、命中、击杀阈值及 60 秒法力。",
        "",
        "设计例外为可选演示数据。报告按所选日期提示超出记录及到期；两个 JAR 使用各自人工设值成功构建，Fabric 在各自的独立合成测试世界施放。正式六人局与真人校准待补充。",
        "",
        "## 入口",
        "",
        "- [可交互报告](benchmark.html)　·　[完整 JSON](samples/report.json)　·　[重跑方法、范围与证据](README.md)",
    ]
    return "\n".join(top) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--as-of", type=date.fromisoformat, default=date.today())
    parser.add_argument("--exceptions", type=Path, default=HERE / "balance_exceptions.json")
    parser.add_argument("--out", type=Path, default=HERE)
    parser.add_argument(
        "--trace", nargs=2, metavar=("ROLE", "BRANCH"), help="Print a full 60-second event trace as JSON"
    )
    parser.add_argument("--scene", choices=SCENES, default=SCENES[0])
    parser.add_argument("--mode", choices=MODES, default=MODES[0])
    parser.add_argument("--level", type=int, choices=range(1, 21), default=20)
    args = parser.parse_args()
    sample = HERE / "samples"
    snap = json.loads((sample / "balance-snapshot.json").read_text())
    evidence = json.loads((sample / "engine-evidence.json").read_text())
    rules = snap["rules"]
    rules_hash = hashlib.sha256(
        json.dumps(rules, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    ).hexdigest()
    if (
        snap["rulesSha256"] != rules_hash
        or rules_hash != evidence["current"]["rulesSha256"]
        or (
            hashlib.sha256((sample / "balance-snapshot.json").read_bytes()).hexdigest()
            != evidence["current"]["snapshotSha256"]
        )
    ):
        raise ValueError("The sample snapshot does not match its archived Fabric capture")
    for hero in rules["roles"].values():
        for skill in hero["skills"]:
            skill["name"] = evidence["labels"].get(skill["id"], skill["name"])
            for f in skill["damage"]:
                if f["labelKey"]:
                    f["labelKey"] = evidence["labels"].get(f["labelKey"], f["labelKey"])
    configs = json.loads(args.exceptions.read_text()) if args.exceptions.exists() else []
    income, curve = ledger(rules)
    if args.trace:
        role, branch = args.trace
        if role not in NAMES or branch not in {label(b) for b in BITS}:
            parser.error("--trace expects luoxia/yingxiao/keheng/qingluo and three A/B choices")
        candidate = args.mode == MODES[1]
        solved = solve(rules, role, curve, candidate)
        defender = defender_build(rules, curve[-1]["income"])[0]
        result = simulate(
            rules,
            role,
            tuple(int(x == "B") for x in branch),
            args.level,
            solved["points"],
            defender,
            candidate,
            args.scene == SCENES[1],
            full_trace=True,
        )
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return
    rotation_events, rotation = ledger(rules, True)
    trial_file = sample / "client-trial.json"
    client = json.loads(trial_file.read_text()) if trial_file.exists() else None
    if client and client["jarSha256"] != evidence["manual"]["jarSha256"]:
        client = None
    data = dict(
        asOf=args.as_of.isoformat(),
        baseline=snap["gameBase"],
        rulesHash=rules_hash,
        attributes=ATTRS,
        names=NAMES,
        modes=MODES,
        scenes=SCENES,
        axes=AXES,
        limits=LIMITS,
        corridors=CORRIDORS,
        levels=sorted({curve[-1]["level"], 12, 20}),
        inventory=dict(
            roster=rules["roster"],
            registered=rules["registeredSkills"],
            covered={k: len(rules["roles"][k]["skills"]) for k in NAMES},
        ),
        curve=curve,
        incomeEvents=income,
        rotation=rotation,
        rotationEvents=rotation_events,
        client=client,
        tower=tower(rules, evidence["tower"]),
        roles=roster_audit(rules, curve),
        experiment=experiment(rules, evidence, configs, args.as_of),
        probes=micro(rules, evidence["current"]["evidence"]["samples"]),
        manualProbes=micro(rules, evidence["manual"]["evidence"]["samples"]),
        count5000=sum(1 for _ in builds(rules["price"], 5000)),
    )
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "samples").mkdir(exist_ok=True)
    encoded = json.dumps(data, ensure_ascii=False, separators=(",", ":"), allow_nan=False)
    (args.out / "samples/report.json").write_text(encoded + "\n")
    (args.out / "REPORT.md").write_text(markdown(data))
    html = (HERE / "viewer.template.html").read_text()
    (args.out / "benchmark.html").write_text(
        html.replace("/*AUDIT_DATA*/null", encoded.replace("<", r"\u003c"))
    )
    print(
        f"{len(NAMES)} roles × 8 builds; {data['count5000']} legal allocations at 5,000 gold; {args.out/'benchmark.html'}"
    )


if __name__ == "__main__":
    main()
