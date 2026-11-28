"""Replay fixed ten-seat 5v5 economy scenarios from paired Fabric releases."""
import argparse
import hashlib
import importlib
import json
from collections import defaultdict
from copy import copy
from pathlib import Path
from shlex import quote
from statistics import mean, median

economy = importlib.import_module("replay-economy")
TEAMS, TICKS = economy.TEAMS, economy.TICKS
value, round_java = economy.value, economy.round_java
active_share = economy.active_share
DEFAULT_SCENARIO = Path(__file__).with_name("scenarios") / "economy-rift-v1.json"
POSITION_LABELS = {"solo": "单人路", "roam": "打野／游走", "duo": "双人路／支援"}
VEIN_CASES = (0, 1.5, 3)


class RiftRules(economy.Rules):
    def __init__(self, snapshot, track):
        super().__init__(snapshot, track, "STANDARD", current=True)
        self.map = self.battlefield["maps"]["5v5"]
        self.roles = {row["id"] for row in snapshot["rules"]["roles"]}
        profile = snapshot["rules"].get("modeProfiles", {}).get("rift_5v5")
        self.profile = value(profile, "profile") if profile else "STANDARD（改前）"
        self.lane_baseline = value(profile, "baseline") if profile else "STANDARD"
        self.first_wave = value(profile["lane"], "firstWaveTicks") if profile else value(self.map, "firstWave")
        self.wave_interval = (value(profile["lane"], "waveIntervalTicks") if profile
                              else value(self.catalog, "waveInterval"))
        self.lane_multipliers = self.multipliers(profile, "lane")
        self.camp_multipliers = self.multipliers(profile, "camp")
        self.team_pig_cap = value(profile["camp"], "goldPigTeamGoldCap") if profile else None
        self.team_pig_seats = value(profile["camp"], "goldPigPayoutSeats") if profile else None
        self.farm_xp_probe = 1
        self.tower_multiplier = value(profile["tower"], "goldMultiplier") if profile else 1
        self.wage = value(profile["wage"], "baseGold") if profile else self.wage
        if profile:
            self.vein = {
                name: value(profile["vein"], name) for name in
                ("opensAfterTicks", "pulseIntervalTicks", "attributeCaptureTicks",
                 "attributeGoldBase", "attributeXp", "attributeBonus", "pulseClock")
            }
        else:
            # The paired pre-#114 export reports pulse interval only. GameManager supplies these retained rules.
            self.vein = {"opensAfterTicks": 60 * TICKS, "pulseIntervalTicks": value(self.economy, "spiritVeinPulse"),
                         "attributeCaptureTicks": 120, "attributeGoldBase": 10, "attributeXp": 5,
                         "attributeBonus": .05, "pulseClock": "world_tick"}
        for unit, definition in self.catalog["types"].items():
            mode = definition["byTempo"][self.lane_baseline]
            self.lane_rewards[unit] = value(mode, "baseGold"), value(mode, "baseExperience")
        if track == "candidate" and not profile:
            raise ValueError("Candidate release needs the server-exported rift_5v5 supply profile")
        if self.vein["pulseClock"] != "world_tick":
            raise ValueError(f"Unsupported vein pulse clock: {self.vein['pulseClock']!r}")
        self.checked_lane_samples = self.check_mode_lane_samples(self.profile if profile else self.lane_baseline)

    @staticmethod
    def multipliers(profile, kind):
        if not profile:
            return 1, 1
        return tuple(value(profile[kind], field) for field in ("goldMultiplier", "xpMultiplier"))

    def lane_credit(self, unit, tick, *, last, teammate):
        gold, xp = self.lane_rewards[unit]
        gold_percent, xp_percent = (round_java(multiplier * 100) for multiplier in self.lane_multipliers)
        if self.lane_baseline == "CHERRY_V2":
            cap = value(self.catalog, "lateGrowthBegins")
            growth, divisor = cap + min(tick, cap), 10000 * cap
        else:
            growth, divisor = 10 + min(10, tick // 2400), 100000
        gold_share = 100 if last else 0 if teammate else 25
        xp_share = 100 if last else 80
        return (gold * growth * gold_share * gold_percent // divisor,
                xp * growth * xp_share * xp_percent // divisor)

    def check_mode_lane_samples(self, mode):
        count = 0
        for unit, definition in self.catalog["types"].items():
            samples = definition["byTempo"][mode]["timelineBeforeCatchUpAndGlobalGold"]
            for sample in samples:
                tick = sample["elapsedTicks"]
                expected_last = tuple(value(sample, name) for name in ("lastHitGold", "lastHitExperience"))
                expected_nearby = tuple(value(sample, name) for name in ("nonPlayerKillNearbyGold", "nearbyExperience"))
                if (self.lane_credit(unit, tick, last=True, teammate=False) != expected_last
                        or self.lane_credit(unit, tick, last=False, teammate=False) != expected_nearby):
                    raise ValueError(f"Lane income differs from {mode} snapshot sample: {unit} at tick {tick}")
                count += 1
        return count

    def description(self, manifest):
        meta = manifest["balance_snapshot"]
        return {"profile": self.profile, "sourceSha": manifest["source_sha"], "sourceDirty": meta["source_dirty"],
                "rulesSha256": meta["rules_sha256"], "snapshotSha256": meta["sha256"], "jarSha256": manifest["sha256"],
                "snapshotFormat": meta["format_version"], "laneBaseline": self.lane_baseline,
                "laneMultipliers": self.lane_multipliers, "campMultipliers": self.camp_multipliers,
                "towerGoldMultiplier": self.tower_multiplier, "teamPigCap": self.team_pig_cap,
                "firstWaveTicks": self.first_wave, "waveIntervalTicks": self.wave_interval,
                "unitBaseRewardsBeforeScaling": self.lane_rewards, "vein": self.vein,
                "checkedLaneSamples": self.checked_lane_samples,
                "mapInputs": {name: self.map[name].get("status", "defined")
                              for name in ("enabledLanes", "camps", "towers", "veins")},
                "openingPurse": self.opening, "baseWage": self.wage, "wageGoldPaid": self.gold(self.wage),
                "wageIntervalTicks": self.wage_ticks, "wageCatchUpWeight": self.wage_weight,
                "globalGoldMultiplier": self.gold_scale, "xpThresholds": self.xp_thresholds, "price": self.pricing,
                "assistPool": self.assist_pool, "farmCatchUpRoster": self.farm_seats, "farmCatchUp": self.farm,
                "normalRespawnTicks": self.wait,
                "towerProtectionSamples": self.protection, "attributeAccess": self.attribute_access,
                "supplyAccess": self.supply_access, "attributePriceStartGold": self.price(0),
                "attributeVeinWageRatio": (
                    self.gold(self.vein["attributeGoldBase"]) / self.gold(self.wage)
                    * self.wage_ticks / self.vein["pulseIntervalTicks"])}


def add_event(scheduled, event):
    scheduled[economy.event_tick(event)].append(event)


def case_rate(case, second, side, ordinary, neutral):
    if "earlyRates" in case and second < case["cutoffSeconds"]:
        return min(1, ordinary * case["earlyRates"][side] / neutral)
    return ordinary


def seat_id(team, index):
    return f"{team[0].upper()}{index}"


def schedule_camps(scenario, rules, case_name, end, scheduled, trips):
    case, plan = scenario["cases"][case_name], scenario["campRoute"]
    ordinal = 0
    targets = scenario["objectives"].get(case_name, {})
    fixed = []
    for team, start in targets.get("pig", []):
        camp = scenario["camps"]["pig"]
        fixed.append(camp_event(team, "shared-pig", camp["unit"], start, start + camp["clearSeconds"]))
    boss_rules = rules.battlefield["rotatingBosses"]
    first = value(boss_rules, "firstAttempt") / TICKS
    interval = value(boss_rules, "interval") / TICKS
    bosses = scenario["bosses"]
    for index, team in enumerate(targets.get("bossOwners", [])):
        unit = bosses["order"][index]
        if unit not in value(boss_rules, "candidates"):
            raise ValueError(f"{unit} is not an exported rotating boss")
        start = first + index * interval
        event = camp_event(team, "shared-rotating-boss", unit, start, start + bosses["clearLagAfterSpawnSeconds"])
        fixed.append(dict(event, rotated=True))
    for event in fixed:
        book_camp(scheduled, trips, plan, event)
    cut = scenario["cutSeconds"] if "cut" in case else 0
    neutral = scenario["laneLastHitRate"]
    for route_start in range(plan["firstSeconds"], end, plan["everySeconds"]):
        for camp_id in plan["homeOrder"]:
            camp = scenario["camps"][camp_id]
            start = route_start + camp["offset"]
            finish = start + camp["clearSeconds"]
            ordinal += 1
            if start < cut:
                continue
            for i, team in enumerate(TEAMS):
                rate = case_rate(case, start, i, plan["defaultAcquisitionRate"], neutral)
                event = camp_event(team, camp_id, camp["unit"], start, finish)
                if any(other["player"] == event["player"] and start <= other["at"]
                       and finish >= other["startTick"] / TICKS for other in fixed):
                    event["skip"] = "fixed_objective_overlap"
                elif any(abs(shop - start) <= plan["recallAvoidSeconds"] for shop in scenario["shops"]):
                    event["skip"] = "recall_window"
                elif (ordinal * 47 % 100 + .5) / 100 > rate:
                    event["skip"] = "route_access"
                book_camp(scheduled, trips, plan, event)


def book_camp(scheduled, trips, plan, event):
    add_event(scheduled, event)
    if "skip" in event:
        return
    start = event["startTick"] / TICKS
    add_event(scheduled, dict(event, type="camp_start", at=start))
    # Attempts still take route time when the camp has not respawned.
    trips[event["player"]].append((start - plan["missBeforeSeconds"], event["at"] + plan["missAfterSeconds"]))


def camp_event(team, camp_id, unit, start, finish):
    key = camp_id if camp_id.startswith("shared-") else f"{team}-{camp_id}"
    player = seat_id(team, 2)
    return {"id": f"{key}-{player}-{start:g}", "type": "camp", "camp": key, "unit": unit,
            "player": player, "startTick": round_java(start * TICKS), "at": finish}


def schedule_lanes(scenario, rules, case, end, tower_breaks, scheduled):
    melee = value(rules.catalog, "meleeSlots")
    ranged = value(rules.catalog, "rangedSlots")
    period = value(rules.catalog, "magicInterval")
    siege_slots = value(rules.catalog, "siegeSlots")
    start, interval = rules.first_wave, rules.wave_interval
    for wave, spawn in enumerate(range(start, end * TICKS + 1, interval), 1):
        for lane_index, (lane, route) in enumerate(scenario["lanes"].items()):
            eligible = route["nearby"]
            if lane == "mid" and wave % scenario["roamerFollowsMidEveryWaves"] == 0:
                eligible = [*eligible, 2]
            swap = {}
            for i, team in enumerate(TEAMS):
                defended_by = TEAMS[1 - i]
                if all(tower_breaks.get((defended_by, lane, tier), end * TICKS + 1) < spawn
                       for tier in scenario["towerTiers"]):
                    swap[team] = True
                    add_event(scheduled, {"id": f"{team}-{lane}-{wave}-siege", "type": "siege_spawn",
                                          "at": spawn / TICKS, "team": team, "lane": lane,
                                          "wave": wave, "slots": siege_slots})
            for slot in range(melee + ranged):
                index = (wave - 1) * (melee + ranged) + slot
                tick = spawn + route["contactAfterSpawnSeconds"] * TICKS + slot
                pattern = route["lastHitRotation"]
                assigned = pattern[index % len(pattern)]
                for i in ((0, 1) if (index + lane_index) % 2 == 0 else (1, 0)):
                    team = TEAMS[i]
                    unit = "melee" if slot < melee else "ranged"
                    if slot < siege_slots and swap.get(team):
                        unit = "siege"
                    if slot == melee + ranged - 1 and wave % period == 0:
                        unit = "magic"
                    rate = case_rate(case, tick / TICKS, i, scenario["laneLastHitRate"], scenario["laneLastHitRate"])
                    hit = ((index * 37 + lane_index * 31) % 100 + .5) / 100 <= rate
                    add_event(scheduled, {"id": f"{team}-{lane}-{wave}-{slot+1}", "type": "lane", "at": tick / TICKS,
                                          "team": team, "lane": lane, "unit": unit, "wave": wave,
                                          "candidates": [seat_id(team, n) for n in eligible],
                                          "finisher": seat_id(team, assigned) if hit else None})


def first_vein_pulse(scenario, rules):
    vein = rules.vein
    first = vein["opensAfterTicks"] + vein["attributeCaptureTicks"] - 1
    phase = scenario["veins"]["worldTickPhaseAtStart"]
    return first + (-(first + phase) % vein["pulseIntervalTicks"])


def schedule_veins(scenario, rules, amount, end, scheduled):
    if not amount:
        return
    first = first_vein_pulse(scenario, rules)
    every = rules.vein["pulseIntervalTicks"]
    block_ticks = scenario["veins"]["equalControlBlockSeconds"] * TICKS
    attributes = scenario["veins"]["attributes"]
    for tick in range(first, end * TICKS + 1, every):
        if amount == 3:
            owners = [TEAMS[0]] * 3
        else:
            owners = [*TEAMS, TEAMS[((tick - first) // block_ticks) % 2]]
        for attribute, owner in zip(attributes, owners, strict=True):
            add_event(scheduled, {"id": f"vein-{attribute}-{tick}", "type": "vein", "at": tick / TICKS,
                                  "attribute": attribute, "team": owner})


class RiftReplay(economy.Replay):
    def __init__(self, scenario, rules, name, veins, *, trace=True):
        self.veins = veins
        self.reached = defaultdict(dict)
        self.active_camps = {}
        grade = scenario["cases"][name].get("cut")
        events = economy.expanded_events(scenario["eventGroups"], scenario["cases"][name].get("events"))
        super().__init__(scenario, rules, name, events, grade, trace=trace)

    def make_timeline(self, scenario, rules, case_name, events, end, _active, _tower_weight):
        scheduled, trips, tower_breaks = defaultdict(list), defaultdict(list), {}
        for action in events:
            event = dict(action)
            if event["type"] == "tower":
                enemy = TEAMS[1 - TEAMS.index(event["team"])]
                tower_breaks[(enemy, event["lane"], event["tier"])] = economy.event_tick(event)
                event.update(tower=f"{enemy}/{event['lane']}/{event['tier']}", syntheticMapSlot=True)
            add_event(scheduled, event)
        schedule_camps(scenario, rules, case_name, end, scheduled, trips)
        schedule_lanes(scenario, rules, scenario["cases"][case_name], end, tower_breaks, scheduled)
        schedule_veins(scenario, rules, self.veins, end, scheduled)
        return scheduled, trips, {}

    def award(self, seat, *args, **kwargs):
        before = seat.level
        super().award(seat, *args, **kwargs)
        for level in range(max(before + 1, 18), seat.level + 1):
            self.reached[seat.config["id"]][level] = self.tick

    def before_wage(self, actions):
        for event in actions:
            if event["type"] == "camp_start":
                player = self.seats[event["player"]]
                self.active_camps[event["id"]] = player.waiting_until if self.alive(player) else None
            if event["type"] != "vein":
                continue
            recipients = [seat for seat in self.teams[event["team"]] if seat.online]
            base = self.rules.gold(self.rules.vein["attributeGoldBase"])
            xp = self.rules.vein["attributeXp"]
            self.record("vein", id=event["id"], team=event["team"], attribute=event["attribute"],
                        goldPerRecipient=base, xpPerRecipient=xp, recipients=[s.config["id"] for s in recipients])
            for seat in recipients:
                self.award(seat, base, base_xp=xp, source="vein", cause=event["id"])

    def earn_farm(self, seat, gold, xp, kind, cause):
        super().earn_farm(seat, gold, round_java(xp * self.rules.farm_xp_probe), kind, cause)

    def lane(self, event):
        candidates = [self.seats[name] for name in event["candidates"]]
        eligible = [seat for seat in candidates if self.eligible_lane(seat)]
        finisher = self.seats.get(event["finisher"])
        if finisher in eligible:
            eligible.remove(finisher)
            eligible.insert(0, finisher)
        else:
            finisher = None
        self.record("lane", **event, actualFinisher=finisher.config["id"] if finisher else None,
                    nearby=[s.config["id"] for s in eligible],
                    missed=[s.config["id"] for s in candidates if s not in eligible])
        for seat in eligible:
            gold, xp = self.rules.lane_credit(event["unit"], self.tick, last=seat is finisher,
                                               teammate=finisher is not None)
            self.earn_farm(seat, gold, xp, "lane", event["id"])

    def camp(self, event):
        unit = event["unit"]
        definition = self.rules.monsters[unit]
        player = self.seats[event["player"]]
        available = None
        if not event.get("rotated"):
            timing = definition["fixedCampTiming"]["STANDARD"]
            available = self.camp_available.get(event["camp"], value(timing, "firstSpawn"))
        skip = event.get("skip")
        if not skip and available is not None and event["startTick"] < available:
            skip = "not_spawned"
        if not skip and (not self.alive(player) or self.active_camps.get(event["id"]) != player.waiting_until):
            skip = "death_interrupts_attempt"
        if skip:
            self.record("camp", **dict(event, skip=skip), availableTick=available)
            return
        next_spawn = None
        if available is not None:
            delay = value(timing, "respawnDelay")
            next_spawn = (((self.tick + delay + TICKS - 1) // TICKS) * TICKS) if delay else float("inf")
            self.camp_available[event["camp"]] = next_spawn
        self.record("camp", **event, nextSpawnTick=next_spawn if next_spawn != float("inf") else None)
        gold_mult, xp_mult = self.rules.camp_multipliers
        xp = round_java(value(definition, "baseExperience") * xp_mult)
        if not value(definition, "teamGold"):
            gold = round_java(value(definition, "baseGold") * gold_mult)
            self.earn_farm(player, gold, xp, "camp", event["id"])
            return
        if unit != "glod_pig":
            raise ValueError(f"Unknown team-gold camp rule for {unit}")
        # MatchBalance#pigGold. The unscaled slopes are independently checked against the export.
        def pig_base(tick):
            return 160 + int(240 * min(1, tick / 9600))
        for tick, sample in definition["preGlobalGoldSamplesByElapsedTicks"].items():
            if pig_base(int(tick)) != sample["value"]:
                raise ValueError("Pig reward formula differs from the server export")
        per_seat = self.rules.gold(round_java(pig_base(self.tick) * gold_mult))
        if self.rules.team_pig_cap is not None:
            per_seat = min(per_seat, self.rules.team_pig_cap // self.rules.team_pig_seats)
        for seat in self.teams[player.config["team"]]:
            if seat.online:
                self.award(seat, per_seat, source="camp", cause=event["id"])
        self.earn_farm(player, 0, xp, "camp", event["id"])

    def tower(self, event):
        self.fallen_towers.add(event["id"])
        recipients = [seat for seat in self.teams[event["team"]] if seat.online]
        base = value(self.rules.towers[str(event["tier"])], "baseTeamGoldPerRecipient")
        gold = self.rules.gold(round_java(base * self.rules.tower_multiplier))
        self.record("tower", **event, goldPerRecipient=gold, recipients=[s.config["id"] for s in recipients])
        self.objectives.append({"id": event["id"], "tick": self.tick, "kind": "tower", "tier": event["tier"],
                                "lane": event["lane"], "goldPerRecipient": gold, "tower": event["tower"],
                                "syntheticMapSlot": True})
        for seat in recipients:
            self.award(seat, gold, source="tower", cause=event["id"])

    def states(self, detail):
        states = super().states(detail)
        for state in states.values():
            state["normalRespawnTicksAtLevel"] = self.rules.wait[state["level"]]
            state["activeShare"] = active_share(state["sources"], state["earned"])
        return states

    def gaps(self):
        result = super().gaps()
        for name, measure in (("teamLevel", "level"), ("teamPoints", "points")):
            result[name] = mean(getattr(a, measure) - getattr(b, measure) for a, b in zip(*self.teams.values()))
        return result

    def result(self):
        result = super().result()
        result.pop("convergence")  # The 3v3 summary assumes three trailing seats.
        result.pop("towerProtection", None)  # Tower breaks are fixed inputs for this 5v5 replay.
        result["levelMilestoneTicks"] = {seat: {lv: self.reached[seat].get(lv) for lv in (18, 19, 20)}
                                        for seat in self.seats}
        return result


def group_metrics(states):
    income = sum(state["earned"] for state in states)
    sources = defaultdict(int)
    xp_sources = defaultdict(int)
    for state in states:
        for source, amount in state["sources"].items():
            sources[source] += amount
        for source, amount in state["xpSources"].items():
            xp_sources[source] += amount
    return {"medianLevel": median(state["level"] for state in states),
            "medianPoints": median(state["points"] for state in states),
            "meanLevel": mean(state["level"] for state in states),
            "meanPoints": mean(state["points"] for state in states), "incomePerSeat": income / len(states),
            "sourcesPerSeat": {source: amount / len(states) for source, amount in sources.items()},
            "xpSourcesPerSeat": {source: amount / len(states) for source, amount in xp_sources.items()},
            "activeShare": active_share(sources, income)}


def frame_metrics(scenario, frame):
    teams = {}
    for team in TEAMS:
        seats = [slot for slot in scenario["slots"] if slot["team"] == team]
        aggregate = group_metrics([frame[seat["id"]] for seat in seats])
        aggregate["positions"] = {position: group_metrics(
            [frame[seat["id"]] for seat in seats if seat["position"] == position]) for position in POSITION_LABELS}
        teams[team] = aggregate
    return teams


def first_team_level(scenario, result, team, level):
    reached = sorted(tick for seat in scenario["slots"] if seat["team"] == team
                     and (tick := result["levelMilestoneTicks"][seat["id"]][level]) is not None)
    middle = len([seat for seat in scenario["slots"] if seat["team"] == team]) // 2
    return reached[middle] if len(reached) > middle else None


def pressure(scenario, rules, dry, controlled):
    first = first_vein_pulse(scenario, rules)
    pressure_ticks = scenario["veins"]["pressureWindowSeconds"] * TICKS
    interval = rules.vein["pulseIntervalTicks"]
    last = first + (pressure_ticks - 1) // interval * interval
    g = TEAMS[0]
    snapshots = controlled["summary"]["frames"]
    before, after, by20 = (snapshots[tick][g] for tick in (first - 1, last, 1200 * TICKS))
    dry_after = dry["summary"]["frames"][last][g]
    gross = after["incomePerSeat"] - before["incomePerSeat"]
    vein_gold = after["sourcesPerSeat"].get("vein", 0)
    fast = first_team_level(scenario, controlled, g, 20)
    slow = first_team_level(scenario, dry, g, 20)
    lead = None if fast is None or slow is None else (slow - fast) / TICKS
    lower_bound = ((scenario["closingSeconds"] * TICKS - fast) / TICKS
                   if fast is not None and slow is None else None)
    return {"window": {"firstPulseTick": first, "lastPulseTick": last, "sampleBeforeTick": first - 1,
                       "pulsesPerVein": (last - first) // interval + 1},
            "goldPerSeat10m": vein_gold, "xpPerSeat10m": after["xpSourcesPerSeat"].get("vein", 0),
            "totalIncomePerSeat10m": gross, "incomeShare10m": vein_gold / gross,
            "goldPerSeatAt20m": by20["sourcesPerSeat"].get("vein", 0),
            "incomeShareAt20m": by20["sourcesPerSeat"].get("vein", 0) / by20["incomePerSeat"],
            "incomeShareAt20mExcludingOpening": (
                by20["sourcesPerSeat"].get("vein", 0) / (by20["incomePerSeat"] - rules.opening)),
            "teamMeanPointsGainAt10mVsNoVeins": after["meanPoints"] - dry_after["meanPoints"],
            "maxObservedTeamMeanPointsGainVsNoVeins": max(
                row[g]["meanPoints"] - dry["summary"]["frames"][tick][g]["meanPoints"]
                for tick, row in snapshots.items() if tick >= first),
            "teamMedianLevel20ControlledTick": fast, "teamMedianLevel20WithoutVeinsTick": slow,
            "level20LeadSeconds": lead, "level20LeadSecondsLowerBound": lower_bound}


def economy_signals(scenario, replay, result, name):
    frames = result["summary"]["frames"]
    if name == "C0":
        opening, closing = (result["gaps"][second * TICKS] for second in (480, 960))
        relative = economy.shrink(opening["teamGoldRelative"], closing["teamGoldRelative"])
        absolute = economy.shrink(opening["teamGold"], closing["teamGold"])
        result["summary"]["zeroKillRecovery"] = {"relativeIncomeGapShrunk": relative,
                                                "absoluteIncomeGapShrunk": absolute,
                                                "opening": opening, "closing": closing}
        return (["中度劣势 8 分钟零反杀相对收入差收窄超过 60%"] if relative is not None and relative > .60 else [])
    early = [seat["id"] for seat in scenario["slots"]
             if ((tick := result["levelMilestoneTicks"][seat["id"]][20]) is not None and tick < 960 * TICKS)
             or any(row[0] < 960 * TICKS and row[3] >= 15 for row in result["purchaseTimeline"][seat["id"]])]
    terminal = frames[1560 * TICKS]
    signals = [f"16 分钟前已达到 Lv20 或 15 点属性（{', '.join(early)}）"] if early else []
    for team, end in terminal.items():
        if end["medianLevel"] < 17 or end["medianPoints"] < 11:
            signals.append(f"{team} 的 26 分钟中位席低于 Lv17 或 11 点属性")
        if not .55 <= end["activeShare"] <= .75:
            signals.append(f"{team} 的 26 分钟主动占比偏出 55%–75%")
    early_towers = {team: sum(row["kind"] == "tower" and row["tick"] < 720 * TICKS
                             and row["tower"].startswith(team + "/") for row in result["objectives"])
                    for team in TEAMS}
    early_gap = max(abs(gap["teamPoints"]) for tick, gap in replay.metrics_at.items() if tick < 720 * TICKS)
    if max(early_towers.values()) >= 4 or early_gap >= 4:
        signals.append(f"12 分钟前失塔 {max(early_towers.values())} 座或人均购点差 {early_gap:.2f}")
    result["summary"].update({"earlyTowerLossByTeam": early_towers,
                              "earlyTowerLossMaxPerTeam": max(early_towers.values()),
                              "maxTeamPointsGapBefore12m": early_gap})
    return signals


def make_run(scenario, rules, name, amount, *, trace=True):
    interval = rules.vein["pulseIntervalTicks"]
    first = first_vein_pulse(scenario, rules)
    pressure_end = first + (scenario["veins"]["pressureWindowSeconds"] * TICKS - 1) // interval * interval
    seconds = sorted({*scenario["seconds"], (first - 1) / TICKS, pressure_end / TICKS})
    sampled = dict(scenario, seconds=seconds)
    replay = RiftReplay(sampled, rules, name, amount, trace=trace)
    result = replay.run()
    result["summary"] = {"frames": {tick: frame_metrics(scenario, frame)
                                    for tick, frame in result["checkpoints"].items()}}
    if trace:
        result["summary"]["level3Reminders"] = economy_signals(scenario, replay, result, name)
    return result


def diagnose(scenario, rules):
    trials = []
    for xp, wage_ratio, control in ((1, .70, 0), (2.5, None, 0), (3.2, None, 0), (3.6, None, 0),
                                    (3.2, .70, 0), (3.2, None, 3)):
        trial = copy(rules)
        trial.farm_xp_probe = xp
        if wage_ratio is not None:
            trial.wage = round_java(value(trial.economy, "passiveGoldBase") * wage_ratio)
        run = make_run(scenario, trial, "S0", control, trace=False)
        trials.append({"extraLaneAndCampXpMultiplier": xp, "hypotheticalWageMultiplier": wage_ratio,
                       "wagePerPulse": trial.gold(trial.wage), "veinControl": control,
                       "frames": {tick: row for tick, row in run["summary"]["frames"].items()
                                  if 1320 * TICKS <= tick <= 1680 * TICKS}})
    return trials


def verify_shared(baseline, candidate, slots):
    keys = ("opening", "gold_scale", "pricing", "xp_thresholds", "wage_ticks", "assist_pool", "farm_seats",
            "farm", "wage_weight", "attribute_access", "supply_access")
    checks = {name: getattr(baseline, name) == getattr(candidate, name) for name in keys}
    checks["normalRespawnSchedule"] = baseline.wait == candidate.wait
    checks["supplyOffers"] = (baseline.offers.keys() == candidate.offers.keys()
                              and baseline.pill_limit == candidate.pill_limit
                              and all(value(baseline.offers[item], field) == value(candidate.offers[item], field)
                                      for item in baseline.offers for field in ("price", "count")))
    checks["distinctRoster"] = (len({slot["role"] for slot in slots}) == len(slots)
                                and all(slot["role"] in baseline.roles and slot["role"] in candidate.roles for slot in slots))
    return checks


def build_report(scenario, digest, releases):
    rules = {track: RiftRules(snapshot, track) for track, (snapshot, _) in releases.items()}
    runs = {}
    pressures = {}
    for track, row in rules.items():
        runs[track] = {}
        for name, case in scenario["cases"].items():
            controls = (0,) if name == "C0" else VEIN_CASES
            runs[track][name] = {"label": case["label"], "veins": {
                str(amount): make_run(scenario, row, name, amount) for amount in controls}}
        probes = runs[track]["S0"]["veins"]
        pressures[track] = probe = pressure(scenario, row, probes["0"], probes["3"])
        probe["level3Reminders"] = []
        if probe["incomeShare10m"] > .20:
            probe["level3Reminders"].append("三脉全控十分钟，灵脉超过同期人均总收入的 20%")
        if (probe["level20LeadSeconds"] or probe["level20LeadSecondsLowerBound"] or 0) >= 180:
            probe["level3Reminders"].append("三脉全控令阵营中位席提早至少 3 分钟达到 Lv20")
        if probe["teamMeanPointsGainAt10mVsNoVeins"] >= 2.5:
            probe["level3Reminders"].append("三脉全控十分钟，仅灵脉造成平均购点增量达到 2.5")
        if probe["incomeShareAt20mExcludingOpening"] > .20:
            probe["level3Reminders"].append("截至 20 分钟的三脉发放超过人均新增总收入的 20%")
    shared = verify_shared(rules["baseline"], rules["candidate"], scenario["slots"])
    return {"formatVersion": 1, "mode": "5v5", "basis": "L2 固定事件离线推演", "ticksPerSecond": TICKS,
            "attributeOrder": economy.ATTRIBUTES, "mapDuration": None, "humanSample": None,
            "sources": {**economy.SOURCES, "vein": "GameManager#tickSpiritVein/rewardSpiritVeinPulse",
                        "mode": "MatchTempo#supply; balance-snapshot.json#rules.modeProfiles.rift_5v5",
                        "legacyVeinFallback": "GameManager#startGame/tickSpiritVein/rewardSpiritVeinPulse",
                        "mapFixtureReference": "src/gametest/resources/arena-fixture/"},
            "scenarioSha256": digest, "scenario": scenario, "pairedInputs": {
                track: row.description(releases[track][1]) for track, row in rules.items()},
            "sharedEconomyComparison": shared, "cases": runs, "veinPressure": pressures,
            "diagnostics": diagnose(scenario, rules["candidate"])}


def compact(frame):
    return f"Lv{frame['medianLevel']:g}／{frame['medianPoints']:g} 点"


def percentage(number):
    return "未达到" if number is None else f"{number:.1%}"


def append_growth(out, report, labels):
    out += ["", "## 发育观察", "",
            "均势脚本没有英雄击杀。守护／毁灭分别取五席中位数，主动占比用各阵营收入加总后计算。",
            "", "| 轨／灵脉 | 阵营 | 22:00 | 24:00 | 26:00 | 28:00 | 26:00 主动经济 |",
            "| --- | --- | --- | --- | --- | --- | ---: |"]
    teams_cn = dict(zip(TEAMS, ("守护", "毁灭"), strict=True))
    for track, cases in report["cases"].items():
        for amount, case in cases["S0"]["veins"].items():
            frames = case["summary"]["frames"]
            for team in TEAMS:
                cols = " | ".join(compact(frames[sec * TICKS][team]) for sec in (1320, 1440, 1560, 1680))
                out.append(f"| {labels[track]}／{amount} | {teams_cn[team]} | {cols} | "
                           f"{percentage(frames[1560 * TICKS][team]['activeShare'])} |")
    out += ["", "### 候选三位置",
            "", "守护方在均势、三种灵脉输入下的三位置进度。双人路将多数补刀留给输出席。"
            "两席位置的中位等级和点数可出现半值。席位购买、余金、下次购买等待与完整分项可在 JSON 逐笔核对。",
            "", "| 灵脉 | 位置 | 22:00 | 24:00 | 26:00 | 28:00 | 26:00 主动经济 |",
            "| ---: | --- | --- | --- | --- | --- | ---: |"]
    for amount, case in report["cases"]["candidate"]["S0"]["veins"].items():
        frames = case["summary"]["frames"]
        for code, label in POSITION_LABELS.items():
            rows = [frames[sec * TICKS][TEAMS[0]]["positions"][code] for sec in (1320, 1440, 1560, 1680)]
            out.append(f"| {amount} | {label} | " + " | ".join(compact(row) for row in rows)
                       + f" | {percentage(rows[2]['activeShare'])} |")


def append_veins(out, report, labels):
    out += ["", "## 灵脉敏感度", "",
            "0 指双方均无脉。1.5 指两方各有一条全时控制，每 60 秒轮流拿到第三条的等效收益。"
            "3 指守护全控三条。场景以开局世界刻相位零起跑，从开战 60 秒时开始无争夺占领，"
            "首次发放按实际周期对齐。1.5 按脚本指定脉冲资格，争夺时的停发时长待实图记录。"
            "大型灵脉处于暂停状态。20 分钟占比的分母扣除开局金。",
            "", "| 轨 | 1 脉／工资 | 3 脉首 10 分钟每席 | 同期收入占比 | "
            "同场景人均购点增量 | 截至 20 分钟新增收入占比 | 中位 Lv20 提前 |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"]
    for track, p in report["veinPressure"].items():
        row = report["pairedInputs"][track]
        lead = p["level20LeadSeconds"]
        bound = p["level20LeadSecondsLowerBound"]
        lead_text = (f"{lead / 60:g} 分钟" if lead is not None else f"至少 {bound / 60:g} 分钟"
                     if bound is not None else "观察窗内未测到")
        out.append(f"| {labels[track]} | {percentage(row['attributeVeinWageRatio'])} | "
                   f"{p['goldPerSeat10m']:g} 金／{p['xpPerSeat10m']:g} XP | {percentage(p['incomeShare10m'])} | "
                   f"{p['teamMeanPointsGainAt10mVsNoVeins']:+.2f} | {percentage(p['incomeShareAt20mExcludingOpening'])}"
                   f" | {lead_text} |")
    for track, p in report["veinPressure"].items():
        if p["level3Reminders"]:
            out.append(f"\n{labels[track]}三级提醒为" + "、".join(p["level3Reminders"]) + "。")


def append_comebacks(out, report, labels):
    out += ["", "## 顺逆风", "",
            "正的差值表示守护领先。破塔时间为场景前提。重压脚本在 12 分钟前安排失守四塔，"
            "触发宽防线。塔保对破塔耗时的影响和本图的实际终局仍待实图或单项推进实测。",
            "", "| 轨 | 场景 | 灵脉 | 8:00 人均金差 | 12:00 人均购点差 | 16:00 人均购点差 | 26:00 中位（守护／毁灭） |",
            "| --- | --- | ---: | ---: | ---: | ---: | --- |"]
    for track, cases in report["cases"].items():
        for name in ("S1", "S2", "S3"):
            for amount, case in cases[name]["veins"].items():
                rows = case["gaps"]
                middle = case["summary"]["frames"][1560 * TICKS]
                out.append(f"| {labels[track]} | {name} | {amount} | {rows[9600]['teamGold']:+.0f} "
                           f"| {rows[14400]['teamPoints']:+.2f} | {rows[19200]['teamPoints']:+.2f} | "
                           + " ／ ".join(compact(middle[team]) for team in TEAMS) + " |")
    for track, cases in report["cases"].items():
        c0 = cases["C0"]["veins"]["0"]["summary"]["zeroKillRecovery"]
        out.append(f"\n{labels[track]}在 C0 注入 25% 人均收入差后零反杀再发育 8 分钟，"
                   f"相对差收窄 {percentage(c0['relativeIncomeGapShrunk'])}，"
                   f"绝对差收窄 {percentage(c0['absoluteIncomeGapShrunk'])}。")
    candidate = report["cases"]["candidate"]
    counts = {name: sum(bool(case["summary"]["level3Reminders"]) for case in row["veins"].values())
              for name, row in candidate.items()}
    out.append("\n候选触发三级提醒的场景为 "
               + "、".join(f"{name} {count} 案" for name, count in counts.items()) + "。")


def append_diagnostics(out, report):
    out += ["", "## 策划试算", "",
            "均势条件下只对兵线和营地所得经验做额外乘算，另试候选区间内 0.70× 工资。"
            "这些倍率只进入离线试算，击杀、塔赏、灵脉、报价和账本事件输入沿用候选。",
            "", "| 额外兵／野经验 | 每 5 秒工资 | 灵脉 | 22:00 | 24:00 | 26:00 | 26:00 主动占比 |",
            "| ---: | ---: | ---: | --- | --- | --- | ---: |"]
    for row in report["diagnostics"]:
        frames = row["frames"]
        left = [frames[sec * TICKS][TEAMS[0]] for sec in (1320, 1440, 1560)]
        out.append(f"| {row['extraLaneAndCampXpMultiplier']:g}× | {row['wagePerPulse']} 金 | {row['veinControl']} "
                   "| " + " | ".join(compact(frame) for frame in left) + " | "
                   + percentage(left[-1]["activeShare"]) + " |")


def markdown(report, args):
    scenario = report["scenario"]
    reference = scenario["provenance"]
    code = chr(96)
    out = ["# 万象 5v5 地图供给复算", "",
           "本报告记录十席固定事件的 L2 经济。正式地图的设施与路线缺输入，营地、三路到线、占领和推塔由脚本提供。"
           "28 分钟是观察上限。水晶摧毁用时与 22–28 分钟完局目标等待十人实图。",
           "", "## 输入与结算", "",
           "| 轨 | 模式 | 兵金／经验 | 野金／经验 | 塔金 | 每 5 秒工资 | 单脉每 5 秒 |",
           "| --- | --- | ---: | ---: | ---: | ---: | ---: |"]
    labels = {"baseline": "改前", "candidate": "候选"}
    for track, row in report["pairedInputs"].items():
        g, x = row["laneMultipliers"]
        cg, cx = row["campMultipliers"]
        out.append(f"| {labels[track]} | {row['profile']} | {g:g}×／{x:g}× {code}{row['laneBaseline']}{code} "
                   f"| {cg:g}×／{cx:g}× | {row['towerGoldMultiplier']:g}× | {row['wageGoldPaid']} 金 "
                   f"| {row['vein']['attributeGoldBase']} 基础金／{row['vein']['attributeXp']} XP |")
    for track, row in report["pairedInputs"].items():
        suffix = "（构建有未提交修改，以 JAR 与快照摘要识别输入）" if row["sourceDirty"] else ""
        out.append(f"\n- {labels[track]}提交 {code}{row['sourceSha']}{code}{suffix}，"
                   f"JAR SHA-256 {code}{row['jarSha256']}{code}，"
                   f"快照 SHA-256 {code}{row['snapshotSha256']}{code}。"
                   "正式地图输入状态为 "
                   + "、".join(f"{code}{name}={status}{code}" for name, status in row["mapInputs"].items()) + "。")
    shared = report["sharedEconomyComparison"]
    changed = [key for key, matches in shared.items() if not matches]
    out += ["",
            f"场景 SHA-256 {code}{report['scenarioSha256']}{code}。每队两名单人路、一名打野游走、两名双人路。",
            (f"两轨比对的 {len(shared)} 项共享输入一致。" if not changed else "两轨共享输入差异为 " + "、".join(changed) + "。")
            + "金币按实际时机购买当前最便宜的有效档位。回泉、阵亡、野区行程期间扣除对应兵线资格。"
            "主动占比采用补刀／附近金币、野区、击杀／助攻、塔赏之和除以含开局金和灵脉的累计收入。"
            "灵脉金币另列，常驻 +5% 不改变固定战斗事件。"]
    append_growth(out, report, labels)
    out += ["", "目标是在 22–26 分钟中位玩家同时达到 Lv18–20、12–16 点属性、主动 60%–70%。"
            "顺逆风和灵脉试测采用 #101 的较宽提醒线。"
            f" [模式决议]({reference['modeDecision']})"]
    append_veins(out, report, labels)
    append_comebacks(out, report, labels)
    append_diagnostics(out, report)
    out += ["",
            "这组合成路线需要联调经验节奏、整体供给量与主动收入占比。只增加兵野经验可以推进等级，"
            "金币来源比例仍需校准。待采输入为十人实际补刀率、打野路线和争夺、"
            "灵脉失控与再占领时长、分路推塔记录和完局时长。",
            "",
            "三级提醒只供策划判断。人工值可以正常保存、构建、试玩、合并及发布。"
            f" [顺逆风决议]({reference['economyDecision']}) · [实施票]({reference['scope']})",
            "", "## 复跑", "", code * 3 + "sh"]
    command = ["python tools/balance/replay-mode-economy.py --mode 5v5",
               f"  --baseline {quote(str(args.baseline))}", f"  --candidate {quote(str(args.candidate))}",
               f"  --scenario {quote(str(args.scenario))}", f"  --output {quote(str(args.output))}"]
    out += [line + " " + chr(92) for line in command[:-1]] + [command[-1], code * 3, ""]
    return "\n".join(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", default="5v5", choices=["5v5"])
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--scenario", type=Path, default=DEFAULT_SCENARIO)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    payload = args.scenario.read_bytes()
    scenario = json.loads(payload)
    if (scenario["version"], scenario["mode"], scenario["map"]) != (1, args.mode, "RIFT"):
        parser.error("Expected a RIFT 5v5 version 1 scenario")
    releases = {track: economy.paired_release(path)
                for track, path in (("baseline", args.baseline), ("candidate", args.candidate))}
    report = build_report(scenario, hashlib.sha256(payload).hexdigest(), releases)
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / "rift-economy.json").open("w", encoding="utf-8") as output:
        json.dump(report, output, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
        output.write("\n")
    (args.output / "rift-economy.md").write_text(markdown(report, args), encoding="utf-8")
    count = sum(len(case["veins"]) for case in report["cases"]["candidate"].values())
    print(f"Replayed {count} paired 5v5 scenarios to {args.output}")


if __name__ == "__main__":
    main()
