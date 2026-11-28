"""Deterministic single-target scenario runner. Time is in real game ticks (20/s)."""

from heapq import heappop, heappush
from itertools import count
from math import ceil
from economy import attributes, skill_value, support_value

LIMITS = {
    "luoxia": (65, 33, 0.9, 15),
    "yingxiao": (90, 22, 1.2, 15),
    "keheng": (65, 30, 1.8, 30),
    "qingluo": (30, 14, 2.4, 65),
}
CORRIDORS = {"luoxia": (2.8, 4), "yingxiao": (1.8, 2.8), "keheng": (3, 4.5), "qingluo": (7, None)}
AXES = ("1.5 秒爆发（%）", "6 秒均伤（%/秒）", "6 秒等效控制（秒）", "6 秒盾疗有效生命（%）")


def ranks(level, skills, bits, passive_rank):
    p, e, r = bits
    indices = [2, 3, 4 + e, 6 + r]
    result = {p: passive_rank}
    for idx in indices:
        result[idx] = 0
    # One paid point per level. Learn all base actions, then prefer R, Q, E, W.
    for lv in range(1, level + 1):
        urgent = [6 + r] if lv in (4, 8, 12) else []
        order = urgent + [i for i in indices if result[i] == 0] + [2, 4 + e, 3, 6 + r]
        for i in order:
            n = result[i]
            if n >= len(skills[i]["mana"]):
                continue
            gate = (4, 8, 12)[n] if i >= 6 else n + 1
            if gate <= lv:
                result[i] += 1
                break
    return result


def simulate(
    rules,
    role,
    bits,
    level,
    points,
    target_points,
    candidate,
    assisted,
    exclude=None,
    full_trace=False,
    night_opener=False,
):
    hero = rules["roles"][role]
    skills = hero["skills"]
    p, eb, rb = bits
    e, r = 4 + eb, 6 + rb
    rk = ranks(level, skills, bits, rules["passiveRank"][level - 1])
    stats = attributes(rules, role, points, candidate)
    target = attributes(rules, "luoxia", target_points, candidate)
    href = rules["roles"]["luoxia"]["hp"][level - 1]
    physical = 100 / (100 + stats["resist"])
    against = 100 / (100 + target["resist"])
    # At ticks 8, 28, 48, 68, 88 and 108: five resistible hits and one true hit.
    reference_mix = (5 * (100 / (100 + rules["baseAttributes"][3])) + 1) / 6
    max_hp = hero["hp"][level - 1]
    hp = 0.55 * max_hp
    max_mana = hero["mp"][level - 1]
    mana = max_mana
    priority = {
        "luoxia": [2, e, r],
        "yingxiao": [r, e, 2],
        "keheng": [r, e, 2, 3],
        "qingluo": [e, 2, r, 3],
    }[role]
    # Separate player-reported opener from the common priority script used for fair A/B comparisons.
    if night_opener and (
        role != "yingxiao" or bits != (0, 0, 0) or exclude is not None or not all(rk.values())
    ):
        raise ValueError("21A34 uses the learned offensive AAA kit, with each input available")
    opening = [3, 2, -1, e, r] if night_opener else []
    if night_opener:
        priority = [
            2,
            e,
        ]  # After the reported opener: Q/E on recovery and full-strength basics in the gaps.
    keys = {3: "2", 2: "1", -1: "A", e: "3", r: "4"}
    inputs, hits = [], []
    next_basic = 0
    order = count()
    queue = []
    trace = []
    controls = []
    shields = {}
    ready = {}
    markers = {}
    lock = 0
    total = burst = sustained = control = used_ehp = heal_credit = 0.0
    actual_heal = absorbed_hp = action_work = 0.0
    death = None
    commits = blocked = 0
    minimum_mana = mana
    cast_token = 0
    tick = 0
    timing = rules["inline"]
    start_back = 40 if assisted else 0

    def record(what, value=None):
        if full_trace or tick < 120 or what == "击杀阈值":
            trace.append([tick, what] if value is None else [tick, what, round(value, 3)])

    def at(when, fn, *args):
        heappush(queue, (when, next(order), fn, args))

    def active(i):
        return rk.get(i, 0) > 0 and i != exclude

    def damage_amount(i, seg=0):
        return skill_value(skills[i]["damage"][seg], rk[i], stats, candidate)

    def support(i, seg=0):
        f = skills[i]["support"][seg]
        if candidate and role == "yingxiao" and i == 7:
            # Preserve zero-buy shield and the first main-attribute increment.
            return (
                support_value(f, rk[i], {"ap": rules["power"]})
                + f["spellScale"] * stats["extraAD"] * 20 / 15
            )
        return support_value(f, rk[i], stats)

    def heal(amount, source, trigger=True):
        nonlocal hp, heal_credit, actual_heal
        gain = min(amount, max_hp - hp)
        hp += gain
        if tick < 120:
            heal_credit += gain
            actual_heal += gain
        record(source + " · 有效治疗", gain)
        if gain > 0 and trigger:
            on_support()

    def shield(i, seg, duration):
        key = skills[i]["id"]
        previous = shields.get(key, (0, 0))[0]
        amount = max(previous, support(i, seg))
        shields[key] = (amount, tick + duration)
        record(skills[i]["name"] + " · 当前盾", amount)
        if amount > previous:
            on_support()

    def on_support():
        if role != "qingluo" or not active(p):
            return
        if p == 0 and hp < 0.6 * max_hp and tick >= markers.get("herbReady", 0):
            markers["herbReady"] = tick + 80
            heal(support(p), skills[p]["name"], False)
        if p == 1 and markers.get("stacks", 0):
            stacks = markers.pop("stacks")
            heal(support(p) * stacks, skills[p]["name"], False)

    def incoming():
        nonlocal hp, heal_credit, used_ehp, absorbed_hp
        kind = ("物理", "法术", "真实", "物理", "法术")[(tick // 20) % 5]
        factor = 1 if kind == "真实" else physical
        remaining = 0.09 * href * factor
        for key, (amt, end) in sorted(shields.items(), key=lambda kv: (kv[1][1], kv[0])):
            used = min(remaining, amt)
            shields[key] = (amt - used, end)
            remaining -= used
            used_ehp += used / factor
            absorbed_hp += used
        attributable = min(heal_credit, remaining, hp)
        heal_credit -= attributable
        used_ehp += attributable / factor
        hp = max(0, hp - remaining)
        record(kind + "来伤后受援者生命", hp)

    def hit(i, amount, token=0, direct=True):
        nonlocal total, death, start_back
        is_basic = i == -1
        if role == "yingxiao" and active(0) and direct and tick < start_back:
            base, step = timing["backstab"]
            amount *= 1 + (base + step * (rk[0] - 1)) / 100
        total += amount * against
        if night_opener and death is None:
            hits.append(
                dict(
                    key=keys[i],
                    tick=tick,
                    damage=round(amount * against, 3),
                    hpLeft=round(max(0.0, href - total), 3),
                )
            )
        record("普攻命中" if is_basic else skills[i]["name"] + " · 命中", amount * against)
        if death is None and total >= href:
            death = tick / 20
            record("击杀阈值", death)
        if not direct:
            return
        if role == "luoxia":
            if is_basic:
                markers["quickUntil"] = tick + 24
            if i == 2:
                markers["markUntil"] = tick + 60
            elif tick < markers.get("markUntil", 0):
                markers["markUntil"] = 0
                hit(2, damage_amount(2, 1), token, False)
        if role == "yingxiao" and active(1) and tick >= markers.get("ambushReady", 0):
            markers["ambushReady"] = tick + 160
            hit(1, damage_amount(1), token, False)
        if role == "keheng" and active(0) and not is_basic:
            seen = markers.setdefault("echoCasts", set())
            if token not in seen:
                seen.add(token)
                if tick >= markers.get("echoUntil", 0):
                    markers["echoCount"], markers["echoUntil"] = 0, tick + 80
                markers["echoCount"] += 1
                if markers["echoCount"] == 3:
                    markers["echoCount"] = 0
                    markers["echoUntil"] = 0
                    hit(0, damage_amount(0), token, False)
        if role == "qingluo" and active(1) and tick >= markers.get("stackReady", 0):
            if is_basic or token != markers.get("stackCast"):
                markers["stackReady"] = tick + 4
                markers["stackCast"] = None if is_basic else token
                markers["stacks"] = min(3, markers.get("stacks", 0) + 1)

    def timed_control(delay, duration, rate, action="all"):
        controls.append((tick + delay, tick + delay + duration, rate, action))

    def enter_field():
        if active(1) and tick >= markers.get("stepReady", 0):
            markers["stepReady"] = tick + 240
            shield(1, 0, 40)

    def ability(i, token):
        nonlocal start_back
        rank = rk[i]

        def strike(delay, seg=0):
            at(tick + delay, hit, i, damage_amount(i, seg), token)

        def give_heal(delay, seg=0):
            at(tick + delay, heal, support(i, seg), skills[i]["name"])

        if role == "luoxia":
            if i == 2:
                strike(2)
            elif i == 4:
                for delay, seg in ((2, 0), (7, 0), (12, 1)):
                    strike(delay, seg)
            elif i == 5:
                strike(1)
            elif i == 6:
                for delay in (2, 14, 26):
                    strike(delay)
            elif i == 7:
                strike(1, 0)  # One first hero, never add later pierced targets.
        elif role == "yingxiao":
            if i == 3:
                # Shadow Vanish grants invisibility and move speed. Facing at the next hit is a scene input.
                record("化影 · 隐身与移速生效")
            elif i == 7:
                shield(i, 0, 80)
            elif i == 6:

                def execution():
                    nonlocal start_back
                    start_back = tick + (30 if assisted and not night_opener else 10)
                    hit(i, damage_amount(i), token)

                at(tick + 16, execution)
            else:
                strike(2)
                if i == 2:
                    timed_control(2, 30, 0.75, "move")
        elif role == "keheng":
            if i == 2:
                strike(6)
                timed_control(6, 25, 1 - 0.05 * (rank + 2))
            if i == 3:
                shield(i, 0, 40)
                enter_field()
                timed_control(0, 20 if assisted else 8, 0.8)
            if i in (4, 5, 6):
                warning, span, rate = (
                    (9, 60 + 5 * (rank - 1), 0.65 - 0.025 * (rank - 1))
                    if i == 4
                    else (5, 50 + 3 * (rank - 1), 0.75 - 0.025 * (rank - 1))
                )
                if i == 6:
                    warning, span, actor_delta = timing["dome"]
                    base, step = timing["domeFlight"]
                    rate = base - step * (rank - 1) - actor_delta
                stay = span if assisted else min(span, 20)
                timed_control(warning, stay, rate)
                at(tick + warning, enter_field)
                if i == 4:
                    strike(warning)
                if i == 5 and stay == span:
                    strike(warning + span)
                if i == 6:
                    for delay in range(timing["domePulse"], stay + 1, timing["domePulse"]):
                        strike(warning + delay)
            if i == 7:
                timed_control(0, 16, 0.6)
                strike(16)
        else:
            if i == 2:
                give_heal(0)
            if i == 3:
                strike(2)
                timed_control(2, 14, 0, "move")
                give_heal(2)
            if i == 4:
                for t in range(0, 81, 20):
                    at(tick + t, shield, i, 0, 25)
            if i == 5:
                shield(i, 0, 80)
                for t in range(20, 81, 20):
                    give_heal(t, 1)
            if i == 6:
                for t in range(20, 121, 20):
                    give_heal(t)
            if i == 7:
                give_heal(0)
                at(tick, shield, i, 1, 100)

    def commit(i, token):
        nonlocal mana, minimum_mana, commits
        cost = skills[i]["mana"][rk[i] - 1]
        mana -= cost
        minimum_mana = min(minimum_mana, mana)
        commits += 1
        base_cd = skills[i]["cooldown"][rk[i] - 1]
        ready[i] = tick + max(1, int(base_cd * stats["cdFactor"]))
        if night_opener and death is None:
            inputs.append(dict(key=keys[i], tick=tick, readyAt=ready[i]))
        record(skills[i]["name"] + " · 提交后法力", mana)
        ability(i, token)

    def basic():
        nonlocal lock, next_basic
        base = hero["attack"] + stats["extraAD"]
        if role == "luoxia":
            work = rules["basicDraw"]
            if active(0) and tick < markers.get("quickUntil", 0):
                work = rules["quickDraw"][rk[0] - 1]
                base = damage_amount(0)
            if active(1):
                work = 20
                base = damage_amount(1)
            duration = ceil(work * rules["baseAttributes"][2] / stats["speed"] - 1e-9)
            at(tick + duration + 1, hit, -1, base)
            lock = tick + duration
        else:
            if role == "qingluo":
                base = rules["mageBasic"] + rules["mageRatio"] * stats["extraAP"]
            at(tick + (1 if role == "qingluo" else 0), hit, -1, base)
            next_basic = tick + ceil(20 / stats["speed"] - 1e-9)
            if night_opener:
                if death is None:
                    inputs.append(dict(key="A", tick=tick, readyAt=next_basic))
                # One input per tick. Vanilla melee recovery limits the next A, while E can start earlier.
                lock = tick + 1
            else:
                lock = next_basic

    def drain():
        while queue and queue[0][0] <= tick:
            _, _, fn, args = heappop(queue)
            fn(*args)

    for tick in range(1200):
        if tick and tick % 20 == 0:
            mana = min(max_mana, mana + rules["manaRegen"])
        for key, (amt, end) in list(shields.items()):
            if tick >= end:
                del shields[key]
        drain()
        if tick >= lock:
            requested = opening[:1] if opening else priority
            available = [i for i in requested if active(i) and tick >= ready.get(i, 0)]
            chosen = next((i for i in available if skills[i]["mana"][rk[i] - 1] <= mana), None)
            if chosen is None:
                if available:
                    blocked += 1
                if (not opening or opening[0] == -1) and (not night_opener or tick >= next_basic):
                    if opening:
                        opening.pop(0)
                    basic()
            else:
                if opening:
                    opening.pop(0)
                cast_token += 1
                i = chosen
                prepare = skills[i]["prepare"]
                record(skills[i]["name"] + " · 起手")
                at(tick + prepare, commit, i, cast_token)
                post_lock = {
                    "luoxia": {4: 11, 6: 25},
                    "yingxiao": {4: 5, 5: 4, 6: 17},
                    "keheng": {2: 5, 3: 6, 5: 6, 7: 17},
                }.get(role, {}).get(i, 0)
                lock = tick + prepare + max(skills[i]["recovery"], post_lock)
            drain()
        if tick < 120:
            # A fixed receiver attempts movement for the first 20 ticks in each 60-tick block.
            rate = min(
                (
                    v
                    for start, end, v, kind in controls
                    if start <= tick < end and (kind == "all" or tick % 60 < 20)
                ),
                default=1,
            )
            control += (1 - rate) / 20
            # Counterfactual stationary attacker. Roots and movement-only slows do not stop its basic.
            action_work += min(
                (v for start, end, v, kind in controls if start <= tick < end and kind == "all"), default=1
            )
            if tick % 20 == 8:
                incoming()
        if tick == 29:
            burst = 100 * total / href
        if tick == 119:
            sustained = 100 * total / href / 6
    enemy_interval = ceil(20 / rules["baseAttributes"][2])
    missed = int(120 // enemy_interval) - int((action_work + 1e-8) // enemy_interval)
    denied_hp = missed * rules["baseAttributes"][0] * physical
    budget = sustained * 6 + 100 * (actual_heal + absorbed_hp + denied_hp) / href
    lower, upper = CORRIDORS[role]
    if level < 12:
        corridor = "未到适用等级"
    elif death is None:
        corridor = "60 秒内未达击杀阈值"
    elif death < lower:
        corridor = "快于参考"
    elif upper is not None and death > upper:
        corridor = "慢于参考"
    else:
        corridor = "在参考范围"
    result = dict(
        corridor=corridor,
        budget=round(budget, 4),
        deniedBasics=missed,
        metrics=[round(v, 4) for v in (burst, sustained, control, 100 * used_ehp / (href / reference_mix))],
        ttk=death,
        heal=round(actual_heal, 2),
        absorbed=round(absorbed_hp, 2),
        manaEnd=round(mana, 2),
        manaMin=round(minimum_mana, 2),
        casts60=commits,
        dryDecisions=blocked,
        trace=trace,
        ranks=rk,
    )
    if night_opener:
        # The first R impact includes the whole 21A34 opener. Subsequent hits are recorded up to lethal.
        first_r = next((hit for hit in hits if hit["key"] == "4"), None)
        result["rotation"] = dict(
            inputs=inputs,
            hits=hits,
            targetHp=href,
            firstR=first_r,
            kill=hits[-1] if death is not None else None,
        )
    return result
