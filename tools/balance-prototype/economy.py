"""One symmetric CHERRY_V1 lane. The event list is a scenario, not a match recording."""

from itertools import accumulate
from math import ceil, floor

SHOPS = (0, 180, 420, 720, 960, 1200)
ATTRS = ("力量", "智慧", "速度", "防御", "冷却")
NAMES = {"luoxia": "落霞", "yingxiao": "夜影", "keheng": "刻衡", "qingluo": "青萝"}


def ledger(rules, include_camps=False):
    inline = rules["inline"]
    events = [(0, "开局", rules["opening"], 0)]

    def gold(base):
        return floor(base * rules["goldScale"] + 0.5)

    for sec in range(inline["salaryTicks"] // 20, 1201, inline["salaryTicks"] // 20):
        events.append((sec, "工资", gold(inline["salaryBase"]), 0))
    trips = []
    if include_camps:
        # A declared rotation, with kill time and travel distinct from the spawn schedule.
        available = {key: camp["first"] for key, camp in rules["camps"].items()}
        for n, sec in enumerate(range(65, 1201, 100)):
            key = ("CHERRY_FROST_HARE", "CHERRY_JADE_BEAST")[n % 2]
            camp = rules["camps"][key]
            if available[key] > sec - 15 or any(abs(sec - shop) <= 28 for shop in SHOPS):
                continue
            trips.append((sec - 16, sec + 12))
            events.append((sec, "野区", camp["gold"], camp["xp"]))
            available[key] = sec + camp["respawn"]
    number = 0
    for wave, spawn in enumerate(range(rules["firstWave"], 1201, inline["waveSeconds"]), 1):
        dead = spawn + 15
        if dead > 1200:
            break
        melee, ranged = inline["waveSize"]
        types = ["近战"] * melee + ["远程"] * ranged
        if wave % inline["magicWave"] == 0:
            types[-1] = "法兵"
        for kind in types:
            mine = number % 3 == 0
            number += 1
            if any(abs(dead - shop) <= 12 for shop in SHOPS) or any(lo <= dead <= hi for lo, hi in trips):
                continue  # Travelling home/out; no local XP entitlement.
            start, cap, minutes = inline["steps"]
            step = start + min(cap, dead // (minutes * 60))
            magic, ranged_g, melee_g = inline["laneGold"]
            ranged_xp, melee_xp = inline["laneXp"]
            base = {"近战": melee_g, "远程": ranged_g, "法兵": magic}[kind]
            xp = melee_xp if kind == "近战" else ranged_xp
            earned = gold(base * step // 10) if mine else 0
            xp = xp * step * (100 if mine else inline["sharedXp"]) // 1000
            events.append((dead, "补刀" if mine else "队友补刀", earned, xp))
    events.sort(key=lambda e: e[0])
    rows = []
    for sec in range(0, 1201, 60):
        totals = {}
        xp = 0
        for t, who, amount, experience in events:
            if t > sec:
                break
            totals[who] = totals.get(who, 0) + amount
            xp += experience
        level = 1 + sum(threshold <= xp for threshold in accumulate(rules["xpNext"]))
        rows.append(
            dict(
                sec=sec, income=sum(totals.values()), sources=totals, xp=xp, level=level, shop=sec in SHOPS
            )
        )
    return events, rows


def builds(prices, budget, size=5):
    cumulative = [0, *accumulate(prices)]

    def visit(prefix, spent):
        if len(prefix) == size:
            yield prefix, spent
            return
        for rank, cost in enumerate(cumulative):
            if spent + cost > budget:
                break
            yield from visit((*prefix, rank), spent + cost)

    return visit((), 0)


def attributes(rules, role, points, candidate):
    strength, wisdom, speed, defense, cooldown = points
    base, one = rules["baseAttributes"], rules["oneEach"]
    return dict(
        ad=rules["roles"][role]["attack"] + strength * (one[0] - base[0]),
        ap=base[1] + wisdom * (one[1] - base[1]),
        extraAD=strength * (one[0] - base[0]),
        extraAP=wisdom * (one[1] - base[1]),
        speed=min(2, base[2] + speed * (one[2] - base[2])),
        resist=base[3] + defense * (15 if candidate else one[3] - base[3]),
        cdFactor=100 / (100 + 12.5 * cooldown)
        if candidate
        else 1 - min(rules["cdrCap"], cooldown * (one[5] - base[5])) / 100,
    )


def skill_value(formula, rank, stats, candidate=False, buff=0):
    stat = {
        "FIXED": 0,
        "TOTAL_PHYSICAL": stats["ad"] + buff,
        "TOTAL_SPELL": stats["ap"],
        "PURCHASED_PHYSICAL": stats["extraAD"] + (buff if candidate else 0),
        "PURCHASED_SPELL": stats["extraAP"],
    }[formula["scaling"]]
    return (
        formula["base"]
        + formula["rankGrowth"] * (rank - 1)
        + (formula["scale"] + formula["scaleGrowth"] * (rank - 1)) * stat
    ) * formula["weight"]


def support_value(formula, rank, stats):
    return formula["base"] + formula["rankGrowth"] * (rank - 1) + formula["spellScale"] * stats["ap"]


def drill(rules, role, level, points, candidate):
    stats = attributes(rules, role, points, candidate)
    hero = rules["roles"][role]
    ehp = hero["hp"][level - 1] * (1 + stats["resist"] / 100)
    if role == "luoxia":
        interval = ceil(rules["basicDraw"] * rules["baseAttributes"][2] / stats["speed"] - 1e-9)
        output = (1200 // interval) * stats["ad"]  # First shot after a complete draw.
    else:
        q = hero["skills"][2]
        rank = min(level, 5)
        cd = max(1, int(q["cooldown"][rank - 1] * stats["cdFactor"]))
        mana, cap = hero["mp"][level - 1], hero["mp"][level - 1]
        cost = q["mana"][rank - 1]
        uses = 0
        next_tick = 0
        # This micro-drill issues only Q. Regeneration is credited at its actual second boundary.
        while next_tick < 1200:
            if mana < cost:
                next_tick = (next_tick // 20 + 1) * 20
                mana = min(cap, mana + rules["manaRegen"])
                continue
            uses += 1
            next_ready = next_tick + cd
            mana = min(cap, mana - cost + (next_ready // 20 - next_tick // 20) * rules["manaRegen"])
            next_tick = next_ready
        output = uses * (
            support_value(q["support"][0], rank, stats)
            if role == "qingluo"
            else skill_value(q["damage"][0], rank, stats, candidate) / 1.45
        )
    return output, ehp


def solve(rules, role, rows, candidate):
    shops = [r for r in rows if r["shop"]]
    terminal = shops[-1]
    scores = []
    for points, spent in builds(rules["price"], terminal["income"]):
        output, ehp = drill(rules, role, terminal["level"], points, candidate)
        scores.append((output, ehp, spent, points))
    target = max(scores, key=lambda row: (row[0] * row[1], -row[2], row[3]))[3]
    # Report efficient output/survival pairs, keeping the cheapest representative for identical values.
    frontier = []
    best_hp = 0
    for output, ehp, spent, points in sorted(scores, key=lambda row: (-row[0], -row[1], row[2], row[3])):
        if ehp > best_hp:
            frontier.append(dict(points=points, cost=spent, output=round(output, 2), ehp=round(ehp, 2)))
            best_hp = ehp
    previous = (0,) * 5
    path = []
    for row in shops:
        choices = (
            (b, c)
            for b, c in builds(rules["price"], row["income"])
            if all(lo <= n <= hi for lo, n, hi in zip(previous, b, target))
        )

        def value(choice):
            (output, hp) = drill(rules, role, row["level"], choice[0], candidate)
            return output * hp, -choice[1], choice[0]

        points, spent = max(choices, key=value)
        purchases = [ATTRS[i] for i in range(5) for _ in range(points[i] - previous[i])]
        path.append(
            dict(
                sec=row["sec"],
                level=row["level"],
                points=points,
                spent=spent,
                remaining=row["income"] - spent,
                buy=purchases,
            )
        )
        previous = points
    return dict(path=path, n=len(scores), frontier=frontier, points=target)
