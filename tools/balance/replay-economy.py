"""Replay fixed Cherry 3v3 economy scenarios from paired Fabric releases."""
import argparse
import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from functools import lru_cache
from math import floor
from pathlib import Path
from shlex import quote
from zipfile import ZipFile


ATTRIBUTES = ("strength", "wisdom", "speed", "defense", "cooldown")
TEAMS = ("guardian", "destruction")
POSITIONS = {"core": 1, "roam": 2, "support": 3}
PILL_IDS = ("blood_pill", "mana_pill")
WEIGHTS = (0, .5, 1, 1.5)
TICKS = 20
SCENARIO = Path(__file__).with_name("scenarios") / "economy-cherry-v1.json"
SOURCES = {
    "wage": "GameManager#tickPassiveGold → MatchBalance#farmReward → GlobalBalance#goldIncome",
    "lane": "LaneSystem#distributeMinionReward/rewardMinion/minionReward",
    "camp": "MonsterManager#onKill → MatchBalance#farmReward",
    "kill": "GameManager#onHeroKill → MatchBalance#killReward",
    "assist": "GameManager#recordHeroDeath → MatchAssists; locked roster order",
    "tower": "LaneSystem#onDeath/grantTeamGold",
    "level": "LevelSystem#addXp",
    "attribute": "FirstGroupGrowth#purchaseError/price",
    "supply": "BaseRules → TacticalSupplySystem#buy",
    "towerProtection": "MatchBalance#towerDamageTakenScale",
}


def round_java(number):
    return floor(number + .5)


def value(fields, key):
    return fields[key]["value"]


def load(path):
    return json.loads(path.read_bytes())


def paired_release(folder):
    manifest = load(folder / "release.json")
    metadata = manifest["balance_snapshot"]
    snapshot_bytes = (folder / metadata["file"]).read_bytes()
    snapshot = json.loads(snapshot_bytes)
    snapshot_hash = hashlib.sha256(snapshot_bytes).hexdigest()
    jar = folder / manifest["file"]
    with jar.open("rb") as file:
        jar_hash = hashlib.file_digest(file, "sha256").hexdigest()
    with ZipFile(jar) as archive:
        embedded = archive.read("META-INF/myriad-balance-snapshot.sha256").decode("ascii").strip()
    if (snapshot_hash != metadata["sha256"] or snapshot_hash != embedded
            or len(snapshot_bytes) != metadata["bytes"] or jar_hash != manifest["sha256"]
            or jar.stat().st_size != manifest["bytes"] or snapshot["sourceSha"] != manifest["source_sha"]
            or snapshot["rulesSha256"] != metadata["rules_sha256"]
            or snapshot["formatVersion"] != metadata["format_version"]
            or snapshot["sourceDirty"] != metadata["source_dirty"]):
        raise ValueError(f"JAR, snapshot and release manifest differ in {folder}")
    return snapshot, manifest


def read_battlefield_evidence(path, scenario, rules, manifest):
    raw = path.read_bytes()
    report = json.loads(raw)
    identity = report["candidate"]
    if (identity["sourceSha"] != manifest["source_sha"]
            or identity["rulesSha256"] != manifest["balance_snapshot"]["rules_sha256"]):
        raise ValueError("Battlefield observations do not belong to the reference release")
    engine = {key: [row for row in report["engine"][key] if row.get("profile") == "CHERRY_V2"]
              for key in ("lane", "monster", "building")}
    lane_rewards = [row for row in engine["lane"] if row["scenario"] == "minion_death"]
    if not lane_rewards:
        raise ValueError("Missing CHERRY_V2 lane reward observations")
    for row in lane_rewards:
        gold, xp = rules.lane_rewards[row["kind"].lower()]
        if (rules.lane_reward(gold, row["elapsedTicks"], 100) != row["goldBeforeCatchUpAndGlobalScale"]
                or rules.lane_reward(xp, row["elapsedTicks"], 100) != row["experienceBeforeCatchUp"]):
            raise ValueError(f"Lane reward differs from the engine observation for {row['kind']}")

    camps = {row["scenario"]: row for row in engine["monster"] if row["event"] == "fight"}
    camp_observations = {}
    for unit, fixture in scenario["campObservationByType"].items():
        row = camps[fixture]
        definition = rules.monsters[unit]
        timing = definition["fixedCampTiming"][rules.tempo]
        rewards = [(item["gold"], item["experience"]) for item in row["rewards"]]
        fixed_reward = (rules.gold(value(definition, "baseGold")), value(definition, "baseExperience"))
        if row["outcome"] != "killed" or row["respawnDelayTicks"] != value(timing, "respawnDelay") or rewards != [fixed_reward]:
            raise ValueError(f"Camp reward or respawn differs from engine observation {fixture}")
        camp_observations[unit] = {key: row[key] for key in (
            "source", "scenario", "elapsedStartTick", "fightTicks", "respawnDelayTicks", "rewards")}

    tower_observations = {}
    for tier in value(rules.map, "towerTiers"):
        row = next(row for row in engine["building"] if row["scenario"] == "late_push_wave" and row["tier"] == tier)
        gold = rules.gold(value(rules.towers[str(tier)], "baseTeamGoldPerRecipient"))
        if not row["broken"] or sorted(row["goldByRecipient"].values()) != [0] * 3 + [gold] * 3:
            raise ValueError(f"Tower {tier} reward differs from the engine observation")
        tower_observations[str(tier)] = row

    tower_pressure = {}
    for event_list in scenario["eventGroups"].values():
        for event in event_list:
            if event.get("type") != "tower":
                continue
            if event["tower"] not in rules.map["towers"] or not event["tower"].endswith('/' + str(event["tier"])):
                raise ValueError(f"Tower {event['tower']} is not an actual slot for tier {event['tier']}")
            if event["observation"] == "mid_team_outer":
                row = next(row for row in engine["building"] if row["scenario"] == "mid_team_outer")
            else:
                row = tower_observations[str(event["tier"])]
            if not row["broken"]:
                raise ValueError(f"No actual break in tower observation {event['observation']}")
            start = row["elapsedTicks"] / TICKS
            duration = row["waveEndTick"] / TICKS
            event["at"] = start + duration
            event["observationSource"] = row["source"]
            tower_pressure[event["id"]] = {"from": start, "waveDuration": duration, "count": 8,
                                          "waveSpacing": value(rules.catalog, "waveInterval") / TICKS,
                                          "rawHealthFraction": 1 / rules.tower_scale(row["elapsedTicks"]),
                                          "requiresTower": event.get("requiresTower")}
    tower_at = {event["id"]: event["at"] for events in scenario["eventGroups"].values()
                for event in events if event.get("type") == "tower"}
    for events in scenario["eventGroups"].values():
        for event in events:
            if "secondsAfterTower" in event:
                event["at"] = tower_at[event["requiresTower"]] + event["secondsAfterTower"]
    lane_clear = {row["elapsedTicks"] // TICKS: row["finishTicks"]
                  for row in engine["lane"] if row["scenario"] == "clear_wave"}
    scenario["laneClearTicksByWaveSpawn"] = [[start, lane_clear[sample]]
                                           for start, sample in scenario["laneClearObservationSeconds"]]
    scenario["campFightTicks"] = {unit: row["fightTicks"] for unit, row in camp_observations.items()}
    scenario["towerPressure"] = tower_pressure
    return {"reportSha256": hashlib.sha256(raw).hexdigest(), "identity": identity,
            "engineEvidence": report["engineEvidence"], "laneRewardObservations": lane_rewards,
            "laneClearObservations": [row for row in engine["lane"] if row["scenario"] == "clear_wave"],
            "campObservations": camp_observations, "towerRewardObservations": tower_observations,
            "midOuterObservation": next(row for row in engine["building"] if row["scenario"] == "mid_team_outer")}


class Rules:
    def __init__(self, snapshot, track, tempo="CHERRY_V1", *, current=False):
        self.track = track
        self.tempo = tempo
        self.cherry_curve = tempo in ("CHERRY_V2", "CHERRY_V3")
        if tempo == "CHERRY_V3":
            profile = snapshot["rules"]["modeProfiles"]["cherry_3v3"]
            self.farm_xp_percent = {kind: value(profile, kind + "ExperiencePercent") for kind in ("lane", "camp")}
        else:
            self.farm_xp_percent = {"lane": 100, "camp": 100}
        self.battlefield = snapshot["rules"]["battlefield"]
        self.economy = self.battlefield["economy"]
        self.farm_seats = value(self.economy, "farmReward")
        if self.farm_seats == "online_participant_averages" and track == "baseline":
            # MatchBalance at #107. These constants were only exported in #108.
            self.farm = (.9, .15, .45, 500)
            self.wage_weight = 1
            self.attribute_access = self.supply_access = "own_fountain_or_waiting"
            self.assist_pool = None
        elif self.farm_seats == "locked_seat_averages" and (track == "candidate" or current):
            self.farm = tuple(value(self.economy, name) for name in
                              ("farmGoldGapWeight", "farmLevelGapWeight", "farmCatchUpCap", "farmGoldIncomeFloor"))
            self.wage_weight = value(self.economy, "passiveGoldCatchUpWeight")
            self.attribute_access = value(self.economy, "attributeShopAccess")
            self.supply_access = value(self.economy, "supplyShopAccess")
            self.assist_pool = value(self.economy, "assistPool")
        else:
            raise ValueError(f"Unexpected {track} farm reward rule {self.farm_seats}")
        self.gold_scale = value(self.economy, "globalIncomeMultiplier")
        self.opening = value(self.economy, "openingPurse")
        self.wage = value(self.economy, "passiveGoldBase")
        self.wage_ticks = value(self.economy, "passiveGoldInterval")
        self.xp_thresholds = value(self.economy, "xpToNextByLevel")
        self.wait = {int(level): value(row, "normal")
                     for level, row in self.economy["respawnByHeroLevel"].items()}
        self.catalog = self.battlefield["minions"]
        self.map = self.battlefield["maps"]["3v3"]
        self.offers = self.battlefield["supplies"]["offers"]
        self.pill_limit = value(self.battlefield["supplies"], "pillCarryLimit")
        self.monsters = self.battlefield["monsters"]["types"]
        self.towers = self.battlefield["towerTiers"]
        self.protection = [(row["elapsedTicks"], value(row, "incomingMultiplier"))
                           for row in self.towers["scalingSamples"]]
        self.pricing = value(snapshot["rules"]["attributePurchases"], "pricing")
        self.lane_rewards = {}
        for unit, definition in self.catalog["types"].items():
            if self.cherry_curve:
                definition = definition["byTempo"][tempo]
            self.lane_rewards[unit] = (value(definition, "baseGold"), value(definition, "baseExperience"))
        if self.cherry_curve:
            if value(self.map, "tempoProfile") != tempo:
                raise ValueError(f"{track} is not a {tempo} map release")
            self.permanent = value(snapshot["rules"]["attributePurchases"], "permanentGain")
            self.roles = {role["id"]: role for role in snapshot["rules"]["roles"]}
        self.check_samples()

    def gold(self, base):
        return round_java(base * self.gold_scale)

    def lane_reward(self, base, tick, share):
        if self.cherry_curve:
            cap = value(self.catalog, "lateGrowthBegins")
            return base * (cap + min(tick, cap)) * share // (100 * cap)
        return base * (10 + min(10, tick // 2400)) * share // 1000

    def farm_experience(self, base, kind):
        return (base * self.farm_xp_percent[kind] + 50) // 100

    @lru_cache(maxsize=None)
    def price(self, rank):
        if rank == 0:
            return self.pricing["startingGold"]
        prior = self.price(rank - 1)
        divisor = self.pricing["stepDenominator"]
        return (prior * self.pricing["stepNumerator"] + divisor // 2) // divisor

    def tower_scale(self, tick):
        for (start, a), (end, b) in zip(self.protection, self.protection[1:]):
            if tick <= end:
                return a + (b - a) * max(0, tick - start) / (end - start)
        return self.protection[-1][1]

    def check_samples(self):
        # The historical exporter records formula observations instead of an expression.
        for sample in self.economy["heroKillRewards"]:
            args = (sample["elapsedTicks"], sample["victimStreak"], sample["victimDeathsWithoutKill"],
                    sample["victimLevelMinusKiller"], 1)
            if (kill_reward(False, *args)[1] != value(sample, "preGlobalGold")
                    or kill_reward(True, *args)[1] != value(sample, "experience")):
                raise ValueError("Kill formula differs from a snapshot sample")
        for unit, definition in self.catalog["types"].items():
            if self.cherry_curve:
                samples = definition["byTempo"][self.tempo]["timelineBeforeCatchUpAndGlobalGold"]
            else:
                samples = definition["rewardSamplesBeforeCatchUpAndGlobalGold"]
            for sample in samples:
                tick = sample["elapsedTicks"]
                gold, xp = self.lane_rewards[unit]
                actual = (self.lane_reward(gold, tick, 100), self.farm_experience(self.lane_reward(xp, tick, 100), "lane"),
                          self.lane_reward(gold, tick, 25), self.farm_experience(self.lane_reward(xp, tick, 80), "lane"))
                expected = tuple(value(sample, key) for key in (
                    "lastHitGold", "lastHitExperience", "nonPlayerKillNearbyGold", "nearbyExperience"))
                if actual != expected:
                    raise ValueError(f"Minion formula differs from snapshot samples for {unit}")

def kill_reward(xp, tick, streak, deaths, gap, weight):
    # MatchBalance#killReward at #107 and #108. Weight scales the delta from timed base.
    time = min(1, tick / 9600)
    base = 30 + 50 * time if xp else 70 + 30 * time
    match = (base + min(5, streak) * (12 if xp else 30)) * max(.30, 1 - .15 * max(0, deaths - 1))
    if gap >= 2:
        match *= 1 + min(1, .25 * (gap - 1))
    return round_java(base), round_java(base + weight * (match - base))


@lru_cache(maxsize=None)
def frontier(rules, budget):
    """Count feasible integer vectors and return the maximal vectors in the five purchase dimensions."""
    costs = [0]
    while costs[-1] + rules.price(len(costs) - 1) <= budget:
        costs.append(costs[-1] + rules.price(len(costs) - 1))
    maximum = []

    def visit(prefix, left):
        if len(prefix) == len(ATTRIBUTES):
            if left < min(rules.price(rank) for rank in prefix):
                maximum.append([*prefix, left])
            return 1
        return sum(visit((*prefix, rank), left - cost)
                   for rank, cost in enumerate(costs) if cost <= left)

    count = visit((), budget)
    return {"allocationCount": count, "maximalVectorsAndRemainingGold": maximum}


@dataclass
class Seat:
    config: dict
    wallet: int = 0
    earned: int = 0
    level: int = 1
    xp: int = 0
    total_xp: int = 0
    streak: int = 0
    prior_deaths: int = 0
    waiting_until: int = -1
    online: bool = True
    pending_gold: int = 0
    pending_xp: int = 0
    attribute_spend: int = 0
    buy: list = field(default_factory=list)
    income: Counter = field(default_factory=Counter)
    experience: Counter = field(default_factory=Counter)
    basket: Counter = field(default_factory=Counter)
    ranks: dict = field(default_factory=lambda: dict.fromkeys(ATTRIBUTES, 0))

    @property
    def points(self):
        return sum(self.ranks.values())

    def next_attribute(self, rules):
        return min(self.config["preference"], key=lambda name: rules.price(self.ranks[name]))


def expanded_events(groups, name):
    result = []
    for event in groups.get(name, []):
        if "include" in event:
            result.extend(expanded_events(groups, event["include"]))
        else:
            result.append(event)
    return result


def camp_event(camp, player, second, prefix):
    return {"id": f"{prefix}-{player}-{camp}-{second}", "at": second, "type": "camp", "player": player, "camp": camp}


def event_tick(event):
    return round_java(event["at"] * TICKS)


def tower_projection(events, scenario, rules, weight):
    projection = {}
    for event in events:
        if event["type"] != "tower":
            continue
        pressure = scenario["towerPressure"][event["id"]]
        total, waves = 0, []
        required = pressure.get("requiresTower")
        start_after = projection[required]["breakSecond"] if required else -1
        if start_after is not None:
            for n in range(pressure["count"]):
                second = pressure["from"] + n * pressure["waveSpacing"]
                if second <= start_after:
                    continue
                multiplier = 1 - weight * (1 - rules.tower_scale(second * TICKS))
                fraction = multiplier * pressure["rawHealthFraction"]
                duration = pressure.get("waveDuration")
                if duration is not None and fraction:
                    second += duration * min(1, (1 - total) / fraction)
                total += fraction
                waves.append([second, multiplier, total])
                if total >= 1 - 1e-12:
                    break
        projection[event["id"]] = {"breakSecond": waves[-1][0] if total >= 1 - 1e-12 else None,
                                   "fixedSecond": event["at"], "waves": waves,
                                   "assumedEntity": event["tower"]}
    return projection


def make_timeline(scenario, rules, case, events, end, active, tower_weight):
    scheduled = defaultdict(list)
    trips = defaultdict(list)
    relevant_towers = tower_projection(events, scenario, rules, tower_weight)
    v2 = rules.cherry_curve
    for action in events:
        event = dict(action)
        tower = relevant_towers.get(event.get("requiresTower"))
        if tower_weight != 1:
            if event["type"] == "tower":
                changed = relevant_towers[event["id"]]["breakSecond"]
                if changed is None:
                    continue
                event["at"] = changed
            elif tower:
                if tower["breakSecond"] is None:
                    continue
                event["at"] += tower["breakSecond"] - tower["fixedSecond"]
        scheduled[event_tick(event)].append(event)
    camp_plan = scenario["campRotation"]
    for index, second in enumerate(camp_plan["opportunitiesSeconds"]):
        recalling = any(abs(second - shop) <= camp_plan["returnAvoidSeconds"] for shop in scenario["shops"])
        for i, team in enumerate(TEAMS):
            rate = camp_plan["defaultAcquisitionRate"]
            if i == 1 and case == "S3" and second < 960:
                rate = camp_plan["heavyTrailingAcquisitionRate"]
            event = camp_event(camp_plan[team][index % len(camp_plan[team])],
                               team[0].upper() + "2", second, "rotation")
            if recalling:
                event["skip"] = "recall window"
            elif camp_plan["difficulty"][index] > rate * (active if i == 1 else 1):
                event["skip"] = "acquisition threshold"
            scheduled[second * TICKS].append(event)
    ready = defaultdict(list)
    for start_tick, events_at_tick in sorted(scheduled.items()):
        for event in events_at_tick:
            finish_tick = start_tick
            if v2 and event["type"] == "camp":
                unit = value(rules.map["camps"][event["camp"]], "type")
                finish_tick += scenario["campFightTicks"][unit]
                event.update(startTick=start_tick, at=finish_tick / TICKS)
            ready[finish_tick].append(event)
            if event["type"] != "camp" or "skip" in event or event["at"] > end:
                continue
            second = start_tick / TICKS
            trips[event["player"]].append((second - camp_plan["laneMissBeforeSeconds"],
                                           event["at"] + camp_plan["laneMissAfterSeconds"]))
    scheduled = ready

    start = value(rules.map, "firstWave")
    interval = value(rules.catalog, "waveInterval")
    melee = value(rules.catalog, "meleeSlots")
    ranged = value(rules.catalog, "rangedSlots")
    period = value(rules.catalog, "magicInterval")
    defaults = scenario["lane"]
    siege_slots = value(rules.catalog, "siegeSlots") if v2 else 0
    tower_breaks = {event["tower"]: tick for tick, actions in scheduled.items()
                    for event in actions if event["type"] == "tower"}
    opponent_towers = {team: [key for key in rules.map["towers"] if key.startswith(team + "/mid/")]
                       for team in TEAMS}
    for wave, spawn in enumerate(range(start, end * TICKS + 1, interval), 1):
        clear = 0
        for second, ticks in scenario.get("laneClearTicksByWaveSpawn", []):
            if spawn >= second * TICKS:
                clear = ticks
        offset = scenario["waveDeathAfterSpawnSeconds"] * TICKS + clear
        sieged = {team for team, towers in opponent_towers.items() if siege_slots and towers and all(
            tower_breaks.get(key, end * TICKS + 1) < spawn for key in towers)}
        for team in TEAMS:
            if team in sieged:
                scheduled[spawn].append({"id": f"{team}-mid-{wave}-siege", "type": "siege_spawn",
                                         "defendingTeam": team, "wave": wave, "slots": siege_slots})
        for slot in range(melee + ranged):
            unit = "melee" if slot < melee else "ranged"
            if slot == melee + ranged - 1 and wave % period == 0:
                unit = "magic"
            index = (wave - 1) * (melee + ranged) + slot
            tick = spawn + offset + slot
            for i in ((0, 1) if index % 2 == 0 else (1, 0)):
                team = TEAMS[i]
                role = defaults["finisherRotation"][index % len(defaults["finisherRotation"])]
                rate = defaults["defaultLastHitRate"]
                for lo, hi, leading, trailing in defaults["cases"].get(case, []):
                    if lo * TICKS <= tick < hi * TICKS:
                        rate = (leading, trailing)[i]
                rate = min(1, rate * (active if i == 1 else 1))
                qualifies = (index * 37 % 100 + .5) / 100 <= rate
                prefix = team[0].upper()
                lane_unit = "siege" if slot < siege_slots and team in sieged else unit
                scheduled[tick].append({"id": f"{team}-mid-{wave}-{slot + 1}", "at": tick / TICKS,
                                        "type": "lane", "team": team, "unitFaction": TEAMS[1 - i],
                                        "unit": lane_unit, "wave": wave,
                                        "finisher": prefix + str(POSITIONS[role]) if qualifies else None})
    return scheduled, trips, relevant_towers


class Replay:
    make_timeline = staticmethod(make_timeline)

    def __init__(self, scenario, rules, case, events, grade=None, *, trace=True, wage_weight=1,
                 active=1, farm_weight=1, kill_weight=1, tower_weight=1,
                 wage_rate=None, assist_gold=None, assist_xp=None):
        self.scenario = scenario
        self.rules = rules
        self.case = case
        self.grade = grade
        self.wage_weight = rules.wage_weight * wage_weight if wage_rate is None else wage_rate
        self.assist_pool = rules.assist_pool
        if self.assist_pool:
            self.assist_pool = {"gold": self.assist_pool["gold"] if assist_gold is None else assist_gold,
                                "xp": self.assist_pool["xp"] if assist_xp is None else assist_xp}
        self.farm_weight = farm_weight
        self.kill_weight = kill_weight
        self.trace = [] if trace else None
        self.seats = {slot["id"]: Seat(slot) for slot in scenario["slots"]}
        self.teams = {team: [seat for seat in self.seats.values() if seat.config["team"] == team] for team in TEAMS}
        self.camps = rules.map["camps"]
        self.camp_available = {}
        self.fallen_towers = set()
        self.checkpoints = {}
        self.recall_states = {}
        self.metrics_at = {}
        self.objectives = []
        self.start = scenario["cutSeconds"] * TICKS if grade else 0
        self.end = (scenario["cutEndSeconds"] if grade else scenario["closingSeconds"]) * TICKS
        self.scheduled, self.trips, self.projection = self.make_timeline(
            scenario, rules, case, events, self.end // TICKS, active, tower_weight)
        self.tick = self.start

    def record(self, event, **detail):
        if self.trace is not None:
            self.trace.append({"tick": self.tick, "event": event, **detail})

    def award(self, seat, base_gold=0, gold_extra=0, base_xp=0, xp_extra=0, *, source, cause):
        gold, xp = base_gold + gold_extra, base_xp + xp_extra
        seat.wallet += gold
        seat.earned += gold
        seat.income[source] += base_gold
        seat.experience[source] += base_xp
        if gold_extra or xp_extra:
            seat.income[source + "Adjustment"] += gold_extra
            seat.experience[source + "Adjustment"] += xp_extra
        seat.total_xp += xp
        seat.xp += xp
        while seat.level <= len(self.rules.xp_thresholds) and seat.xp >= self.rules.xp_thresholds[seat.level - 1]:
            seat.xp -= self.rules.xp_thresholds[seat.level - 1]
            seat.level += 1
        self.record("income", source=source, cause=cause, player=seat.config["id"], goldBase=base_gold,
                    goldAdjustment=gold_extra, xpBase=base_xp, xpAdjustment=xp_extra, wallet=seat.wallet,
                    earned=seat.earned, xp=seat.xp, level=seat.level)

    def seed(self):
        for index, (guardian, destruction) in enumerate(zip(*self.teams.values())):
            for seat, leading in ((guardian, True), (destruction, False)):
                if not self.grade:
                    self.award(seat, self.rules.opening, source="opening", cause="open")
                    continue
                cut = self.scenario["cut"]
                gold = cut["trailingIncome"][index]
                level = cut["trailingLevels"][index]
                if leading:
                    gold = gold * self.grade["incomePercent"] // 100
                    level = cut["trailingLevels"][0] + self.grade["coreLevelLead"]
                    seat.streak = 3 if index == 0 else 0
                    seat.prior_deaths = 3 if index == 2 else 0
                xp = sum(self.rules.xp_thresholds[:level - 1])
                self.award(seat, gold, base_xp=xp, source="cutSeed", cause="8-minute seed")
                self.buy_attributes(seat, "precut-allocation")

    def team_average(self, team):
        seats = self.teams[team]
        eligible = [seat for seat in seats if seat.online or self.rules.farm_seats == "locked_seat_averages"]
        if not eligible:
            return 0, None, None
        return len(eligible), sum(s.earned for s in eligible) / len(eligible), sum(s.level for s in eligible) / len(eligible)

    def farm_bonus(self, seat):
        team = seat.config["team"]
        own_n, own_gold, _ = self.team_average(team)
        enemy_n, enemy_gold, enemy_level = self.team_average(TEAMS[1 - TEAMS.index(team)])
        if not own_n or not enemy_n:
            return 0, 0
        g, x, cap, income_floor = self.rules.farm
        return (min(cap, max(0, (enemy_gold - own_gold) / max(income_floor, enemy_gold)) * g),
                min(cap, max(0, enemy_level - seat.level) * x))

    def eligible_lane(self, seat):
        second = self.tick / TICKS
        if not self.alive(seat):
            return False
        miss = self.scenario["recallMissSeconds"]
        if any(abs(second - shop) <= miss for shop in self.scenario["shops"]):
            return False
        return not any(low <= second <= high for low, high in self.trips[seat.config["id"]])

    def alive(self, seat):
        return seat.online and self.tick >= seat.waiting_until

    def before_wage(self, actions):
        pass

    def wage(self):
        # Snapshot all awards before committing any money so team iteration cannot alter the pulse.
        base = self.rules.gold(self.rules.wage)
        awards = [(seat, self.rules.gold(round_java(self.rules.wage * (1 + self.farm_bonus(seat)[0] * self.wage_weight))))
                  for seat in self.seats.values() if seat.online]
        for seat, actual in awards:
            self.award(seat, base, actual - base, source="wage", cause="scheduled wage")

    def earn_farm(self, seat, gold, xp, kind, cause):
        gold, xp = self.farm_base(gold, xp, kind)
        gb, xb = self.farm_bonus(seat)
        base_gold = self.rules.gold(gold)
        final_gold = self.rules.gold(round_java(gold * (1 + gb * self.farm_weight)))
        final_xp = round_java(xp * (1 + xb * self.farm_weight))
        self.award(seat, base_gold, final_gold - base_gold, xp, final_xp - xp, source=kind, cause=cause)

    def farm_base(self, gold, xp, kind):
        return gold, self.rules.farm_experience(xp, kind)

    def lane(self, event):
        team = self.teams[event["team"]]
        assigned = event["finisher"]
        eligible = [seat for seat in team if self.eligible_lane(seat)]
        finisher = self.seats[assigned] if assigned else None
        leading = finisher in eligible
        if leading:
            eligible.remove(finisher)
            eligible.insert(0, finisher)
        gold, xp = self.rules.lane_rewards[event["unit"]]
        self.record("lane", **event, actualFinisher=assigned if leading else None,
                    nearby=[seat.config["id"] for seat in eligible],
                    missed=[seat.config["id"] for seat in team if seat not in eligible])
        for seat in eligible:
            last = leading and seat is finisher
            self.earn_farm(seat, self.rules.lane_reward(gold, self.tick, 100 if last else (0 if leading else 25)),
                           self.rules.lane_reward(xp, self.tick, 100 if last else 80), "lane", event["id"])

    def camp(self, event):
        player = self.seats[event["player"]]
        if "skip" in event:
            self.record("camp", **event)
            return
        if event.get("requiresTower") and event["requiresTower"] not in self.fallen_towers:
            self.record("camp", **event, skip="required tower is standing")
            return
        if not self.alive(player):
            self.record("camp", **event, skip="dead or offline")
            return
        unit = value(self.camps[event["camp"]], "type")
        definition = self.rules.monsters[unit]
        timing = definition["fixedCampTiming"][self.rules.tempo]
        available = self.camp_available.get(event["camp"], value(timing, "firstSpawn"))
        if event.get("startTick", self.tick) < available:
            self.record("camp", **event, unit=unit, skip="not spawned", availableTick=available)
            return
        if value(definition, "teamGold"):
            raise ValueError(f"Team gold camp {unit} is outside the fixed event inputs")
        self.camp_available[event["camp"]] = self.tick + value(timing, "respawnDelay")
        # Fixed V2 replays treat world tick zero as aligned to the polling clock.
        if self.rules.cherry_curve:
            self.camp_available[event["camp"]] = (self.camp_available[event["camp"]] + TICKS - 1) // TICKS * TICKS
        self.record("camp", **event, unit=unit, nextSpawnTick=self.camp_available[event["camp"]])
        self.earn_farm(player, value(definition, "baseGold"), value(definition, "baseExperience"), "camp", event["id"])

    def tower(self, event):
        self.fallen_towers.add(event["id"])
        recipients = [seat for seat in self.teams[event["team"]] if seat.online]
        gold = self.rules.gold(value(self.rules.towers[str(event["tier"])], "baseTeamGoldPerRecipient"))
        self.record("tower", **event, goldPerRecipient=gold, recipients=[s.config["id"] for s in recipients])
        self.objectives.append({"id": event["id"], "tick": self.tick, "kind": "tower", "tier": event["tier"],
                                "goldPerRecipient": gold, "syntheticMapSlot": event.get("syntheticMapSlot", False)})
        for seat in recipients:
            self.award(seat, gold, source="tower", cause=event["id"])

    def assist(self, event, killer):
        pool = self.assist_pool
        eligible = [seat for seat in self.teams[killer.config["team"]]
                    if seat is not killer and seat.config["id"] in event["assists"]]
        ns = len(eligible)
        if not pool or not ns:
            return []
        split = []
        for i, seat in enumerate(eligible):
            g, g_left = divmod(round_java(pool["gold"] * self.kill_weight), ns)
            x, x_left = divmod(round_java(pool["xp"] * self.kill_weight), ns)
            gold, xp = g + (i < g_left), x + (i < x_left)
            split.append({"player": seat.config["id"], "gold": gold, "xp": xp, "deferred": not seat.online})
            if seat.online:
                self.award(seat, gold, base_xp=xp, source="assist", cause=event["id"])
            else:
                seat.pending_gold += gold
                seat.pending_xp += xp
        return split

    def kill(self, event):
        killer, victim = self.seats[event["killer"]], self.seats[event["victim"]]
        if not self.alive(killer) or not self.alive(victim):
            raise ValueError(f"Fixed engagement {event['id']} is invalid for {self.rules.track} at {self.tick}")
        gap_before = self.gaps()
        victim_level = victim.level
        args = (self.tick, victim.streak, victim.prior_deaths, victim.level - killer.level, self.kill_weight)
        base, final = kill_reward(False, *args)
        base_xp, final_xp = kill_reward(True, *args)
        base_gold, final_gold = self.rules.gold(base), self.rules.gold(final)
        splits = self.assist(event, killer)
        self.award(killer, base_gold, final_gold - base_gold, base_xp, final_xp - base_xp,
                   source="kill", cause=event["id"])
        victim.waiting_until = self.tick + self.rules.wait[victim_level]
        self.scheduled[victim.waiting_until].append({"type": "respawn", "player": victim.config["id"],
                                                    "id": event["id"]})
        self.record("kill", **event, victimLevel=victim_level, victimStreak=victim.streak,
                    victimPriorDeaths=victim.prior_deaths, waitTicks=victim.waiting_until - self.tick,
                    orderedShares=splits)
        killer.streak += 1
        killer.prior_deaths = 0
        victim.streak = 0
        victim.prior_deaths += 1
        self.objectives.append({"id": event["id"], "tick": self.tick, "kind": "kill", "killer": event["killer"],
                                "victim": event["victim"], "round": event.get("round"), "coreBefore": gap_before,
                                "killerGold": final_gold, "killerXp": final_xp, "assists": splits,
                                "victimLevel": victim_level, "waitTicks": self.rules.wait[victim_level]})

    def connection(self, event):
        player = self.seats[event["player"]]
        old = {team: self.team_average(team) for team in TEAMS}
        player.online = event["type"] == "reconnect"
        frozen = {"income": player.earned, "level": player.level, "wallet": player.wallet,
                  "xp": player.xp, "queuedGold": player.pending_gold, "queuedXp": player.pending_xp}
        self.record(event["type"], **event, frozenSeat=frozen, priorAverages=old,
                    nextAverages={team: self.team_average(team) for team in TEAMS})
        self.objectives.append({"id": event["id"], "tick": self.tick, "kind": event["type"], **frozen})
        if player.online and (player.pending_gold or player.pending_xp):
            self.award(player, player.pending_gold, base_xp=player.pending_xp,
                       source="assist", cause=event["id"])
            player.pending_gold, player.pending_xp = 0, 0

    def in_shop(self, seat):
        if not self.alive(seat):
            return False
        return self.tick == seat.waiting_until or self.tick / TICKS in self.scenario["shops"]

    def permitted(self, seat, access):
        if not seat.online:
            return False
        if access == "participant_alive_or_waiting":
            return True
        if access == "participant_alive_at_own_fountain":
            return self.in_shop(seat)
        if access == "own_fountain_or_waiting":
            return not self.alive(seat) or self.in_shop(seat)
        raise ValueError(f"Unsupported shop rule {access}")

    def buy_supply(self, seat, item, why):
        definition = self.rules.offers[item]
        quote, count = value(definition, "price"), value(definition, "count")
        if not self.permitted(seat, self.rules.supply_access):
            result = "access"
        elif item in PILL_IDS and sum(seat.basket[p] for p in PILL_IDS) + count > self.rules.pill_limit:
            result = "pill_limit"
        elif quote > seat.wallet:
            result = "gold"
        else:
            if item == "scout_totem" or item == "true_sight_charm":
                seat.basket["scout_totem"] = seat.basket["true_sight_charm"] = 0
            seat.basket[item] += count
            seat.wallet -= quote
            seat.income["supply"] -= quote
            result = "bought"
        self.record("supply", player=seat.config["id"], cause=why, item=item, quote=quote, result=result,
                    wallet=seat.wallet, delivered=count if result == "bought" else 0)
        if why != "recall plan":
            self.objectives.append({"id": why, "kind": "supply", "tick": self.tick, "result": result,
                                    "item": item, "player": seat.config["id"], "wallet": seat.wallet})

    def buy_attributes(self, seat, cause):
        while True:
            attr = seat.next_attribute(self.rules)
            price = self.rules.price(seat.ranks[attr])
            if price > seat.wallet:
                break
            seat.wallet -= price
            seat.attribute_spend += price
            seat.ranks[attr] += 1
            seat.buy.append([self.tick, attr, price, seat.points])
            self.record("attribute", player=seat.config["id"], cause=cause, attribute=attr, price=price,
                        points=seat.points, wallet=seat.wallet)

    def gaps(self):
        g, d = (self.teams[team][0] for team in TEAMS)
        gi, di = (sum(seat.earned for seat in self.teams[team]) / len(self.teams[team]) for team in TEAMS)
        return {"coreGold": g.earned - d.earned, "coreLevel": g.level - d.level,
                "corePoints": g.points - d.points, "teamGold": gi - di,
                "teamGoldRelative": (gi - di) / di if di else None,
                "teamLevel": sum(a.level - b.level for a, b in zip(*self.teams.values())) / 3,
                "teamPoints": sum(a.points - b.points for a, b in zip(*self.teams.values())) / 3}

    def states(self, detail):
        states = {}
        for id_, seat in self.seats.items():
            quote = self.rules.price(seat.ranks[seat.next_attribute(self.rules)])
            state = {"earned": seat.earned, "level": seat.level, "wallet": seat.wallet, "xp": seat.xp,
                     "totalXp": seat.total_xp, "points": seat.points, "nextGap": max(0, quote - seat.wallet),
                     "sources": dict(seat.income), "xpSources": dict(seat.experience)}
            if detail:
                state.update({"allocation": [seat.ranks[name] for name in ATTRIBUTES],
                              "spentAttributes": seat.attribute_spend, "nextQuote": quote, "online": seat.online,
                              "waitingUntilTick": seat.waiting_until if self.tick < seat.waiting_until else None,
                              "ultimateRanksUnlocked": sum(seat.level >= lv for lv in (4, 8, 12)),
                              "passiveRanksUnlocked": sum(seat.level >= lv for lv in (1, 4, 7, 10, 13)),
                              "frontierBudget": seat.wallet + seat.attribute_spend})
            if self.rules.cherry_curve:
                state["normalRespawnTicksAtLevel"] = self.rules.wait[seat.level]
                state["activeShare"] = active_share(state["sources"], seat.earned)
            states[id_] = state
        return states

    def run(self):
        self.seed()
        wanted = {round_java(second * TICKS) for second in self.scenario["seconds"]
                  if self.start <= round_java(second * TICKS) <= self.end}
        moments = wanted | set(self.scheduled) | {self.start, self.end}
        wage = self.rules.wage_ticks
        moments.update(range((self.start // wage + 1) * wage, self.end + 1, wage))
        shops = {s * TICKS for s in self.scenario["shops"]}
        moments.update(shops)
        for event_list in self.scheduled.values():
            for event in event_list:
                if event["type"] == "kill":
                    moments.update(event_tick(event) + wait for wait in self.rules.wait.values())
        for self.tick in sorted(t for t in moments if self.start <= t <= self.end):
            if self.grade and self.tick == self.start:
                self.checkpoints[self.tick] = self.states(self.trace is not None)
                self.metrics_at[self.tick] = self.gaps()
                continue
            actions = self.scheduled.get(self.tick, [])
            for event in actions:
                if event["type"] == "disconnect" or event["type"] == "reconnect":
                    self.connection(event)
                if event["type"] == "respawn":
                    self.record("respawn", player=event["player"], cause=event["id"])
            self.before_wage(actions)
            if self.tick > self.start and self.tick % self.rules.wage_ticks == 0:
                self.wage()
            before = len(self.objectives)
            for event in actions:
                kind = event["type"]
                if kind == "lane":
                    self.lane(event)
                elif kind == "camp":
                    self.camp(event)
                elif kind == "tower":
                    self.tower(event)
                elif kind == "kill":
                    self.kill(event)
                elif kind == "supply":
                    self.buy_supply(self.seats[event["player"]], event["item"], event["id"])
                elif kind == "siege_spawn":
                    self.record("siege_spawn", **event)
            if self.tick in shops:
                for task in self.scenario["supplyPlan"]:
                    if self.tick // TICKS in task["occasions"]:
                        for team in TEAMS:
                            index = task["seatIndex"] if "seatIndex" in task else POSITIONS[task["player"]] - 1
                            seat = self.teams[team][index]
                            self.buy_supply(seat, task["item"], "recall plan")
            for seat in self.seats.values():
                if self.permitted(seat, self.rules.attribute_access):
                    self.buy_attributes(seat, "accessible")
            if self.tick in shops and self.trace is not None:
                self.recall_states[self.tick] = self.states(True)
            if self.tick in wanted:
                self.checkpoints[self.tick] = self.states(self.trace is not None)
            for objective in self.objectives[before:]:
                if objective["kind"] in ("kill", "tower"):
                    objective["immediate"] = self.gaps()
                    objective["shopAfter"] = [x for x in sorted(shops) if self.tick < x <= self.end][:1]
                    if self.trace is not None:
                        objective["immediateStates"] = self.states(True)
            self.metrics_at[self.tick] = self.gaps()
        return self.result()

    def result(self):
        buys = {name: seat.buy for name, seat in self.seats.items()}
        if self.trace is not None:
            for tick, states in self.checkpoints.items():
                for name, state in states.items():
                    following = next((row[0] for row in buys[name] if row[0] > tick), None)
                    state["nextPurchaseWaitTicks"] = None if following is None else following - tick
        milestones = {name: {point: next((row[0] for row in log if row[3] == point), None)
                             for point in (3, 6, 9, 12)} for name, log in buys.items()}
        result = {"checkpoints": self.checkpoints, "gaps": {t: self.metrics_at[t] for t in self.checkpoints},
                  "objectives": self.objectives, "purchaseMilestonesTicks": milestones,
                  "end": self.states(False), "endGaps": self.gaps()}
        result["convergence"] = analysis(self, result)
        if self.trace is not None:
            result["purchaseTimeline"] = buys
            result["trace"] = self.trace
            result["towerProtection"] = self.projection
            result["nextRecallStates"] = {}
            for row in self.objectives:
                if row.get("shopAfter"):
                    tick = row["shopAfter"][0]
                    if tick in self.checkpoints:
                        result["nextRecallStates"][tick] = self.checkpoints[tick]
                    else:
                        result["nextRecallStates"][tick] = self.recall_states[tick]
        return result


def shrink(opening, closing):
    return (opening - closing) / opening if opening is not None and opening > 0 else None


def contestable(gaps):
    return abs(gaps["coreLevel"]) <= 1 and abs(gaps["corePoints"]) <= 1


def analysis(replay, result):
    cut = replay.scenario["cutSeconds"] * TICKS
    at_cut = replay.metrics_at.get(cut)
    defense = []
    if at_cut and replay.case in ("S1", "C0"):
        for start, end in ((480, 720), (720, 960), (480, 960), (960, 1200)):
            before, after = replay.metrics_at.get(start * TICKS), replay.metrics_at.get(end * TICKS)
            if before is not None and after is not None:
                defense.append({"startSecond": start, "endSecond": end,
                                "relativeTeamIncomeShrink": shrink(before["teamGoldRelative"], after["teamGoldRelative"]),
                                "teamIncomeAbsoluteShrink": shrink(before["teamGold"], after["teamGold"]),
                                "teamLevelNarrowed": before["teamLevel"] - after["teamLevel"]})
    shut = [row for row in replay.objectives if row.get("round")]
    exchanges = [{"id": row["id"], "tick": row["tick"], "round": row["round"], "before": row["coreBefore"],
                  "after": row["immediate"],
                  "coreGoldShrink": shrink(row["coreBefore"]["coreGold"], row["immediate"]["coreGold"])}
                 for row in shut]
    contest = None
    if shut:
        opened = shut[0]["tick"]
        tick = next((time for time, gaps in replay.metrics_at.items() if time >= opened and contestable(gaps)), None)
        contest = {"firstExchangeTick": opened, "tick": tick, "secondsAfterFirstExchange": None if tick is None else
                   (tick - opened) / TICKS, "rounds": None if tick is None else sum(row["tick"] <= tick for row in shut),
                   "alreadyContestable": contestable(shut[0]["coreBefore"])}
    trailing = [result["end"][name] for name in ("D1", "D2", "D3")]
    wage_bonus = sum(s["sources"].get("wageAdjustment", 0) for s in trailing)
    farm_bonus = sum(s["sources"].get("laneAdjustment", 0) + s["sources"].get("campAdjustment", 0) for s in trailing)
    return {"defense": defense, "exchanges": exchanges, "contest": contest, "wageCatchUpGold": wage_bonus,
            "farmCatchUpGold": farm_bonus, "wageShareOfTrailingCatchUpGold": (
                wage_bonus / (wage_bonus + farm_bonus) if wage_bonus + farm_bonus else None)}


def active_share(sources, earned):
    active = sum(sources.get(key, 0) for key in ("lane", "laneAdjustment", "camp", "campAdjustment",
                                               "kill", "killAdjustment", "assist", "tower"))
    return active / earned if earned and not sources.get("cutSeed") else None


def team_stats(seats):
    split = Counter()
    for state in seats:
        split.update(state["sources"])
    earned = sum(s["earned"] for s in seats)
    result = {"income": earned, "sources": dict(split), "activeShare": active_share(split, earned),
            "avgLevel": sum(s["level"] for s in seats) / len(seats),
            "avgPoints": sum(s["points"] for s in seats) / len(seats)}
    if "normalRespawnTicksAtLevel" in seats[0]:
        result["avgNormalRespawnTicksAtLevel"] = sum(s["normalRespawnTicksAtLevel"] for s in seats) / len(seats)
    return result


def closing_stats(result):
    return team_stats(list(result["end"].values()))


def reminders(case, result):
    found = []
    convergence = result["convergence"]
    for interval in convergence["defense"]:
        if interval["startSecond"] != 480:
            continue
        close = interval["relativeTeamIncomeShrink"]
        window = interval["endSecond"] - interval["startSecond"]
        if close is not None and close > (.35 if window == 240 else .60):
            found.append("纯防守相对收入差收窄偏快")
        if close is not None and close < 0:
            found.append("纯防守相对收入差继续扩大")
    share = convergence["wageShareOfTrailingCatchUpGold"]
    if case in ("S1", "C0") and share is not None and share > .40:
        found.append("工资占落后方追赶金超过 40%")
    if case in ("S1", "C0") and result["endGaps"]["corePoints"] < 0:
        found.append("纯防守时落后核心购点反超")
    exchanges = convergence["exchanges"]
    if exchanges and case in ("S2", "C1"):
        close = exchanges[0]["coreGoldShrink"]
        if close is not None and close < .15:
            found.append("首次终结缩差低于 15%")
        if (close is not None and close > 1
                or exchanges[0]["after"]["corePoints"] < 0 <= exchanges[0]["before"]["corePoints"]):
            found.append("首次终结后落后核心反超")
    if case == "S3":
        towers = [row for row in result["objectives"] if row["kind"] == "tower"]
        if sum(row["tick"] < 600 * TICKS for row in towers) >= 2:
            found.append("10 分钟前同侧失守两座固定塔")
        if result["gaps"][960 * TICKS]["teamPoints"] < 1.5:
            found.append("重压至 16 分钟人均购点优势小于 1.5")
        if any(step["teamPoints"] >= 4 for step in result["gaps"].values()):
            found.append("人均购点差达到 4 点")
    return [{"level": 3, "message": message} for message in dict.fromkeys(found)]


def scenario_matrix(scenario):
    cases = []
    for case, data in scenario["fullCases"].items():
        cases.append((case, case, data["label"], expanded_events(scenario["eventGroups"], data.get("events")), None))
    for grade in scenario["cut"]["grades"]:
        for case, row in scenario["cut"]["cases"].items():
            name = f"{grade['id']}-{case}"
            label = f"{grade['incomePercent'] - 100}%／{grade['coreLevelLead']}级 · {row['label']}"
            cases.append((name, case, label, [row["event"]] if "event" in row else [], grade))
    cases.append(("reconnect", "reconnect", "掉线冻结／重连接账", scenario["eventGroups"]["reconnect"], None))
    return cases


def sensitivity(scenario, rules, case, events, grade):
    sweeps = {}
    for name, options in (("wage_weight", WEIGHTS), ("active", (.85, 1, 1.15)), ("farm_weight", WEIGHTS),
                          ("kill_weight", WEIGHTS), ("tower_weight", WEIGHTS)):
        if name == "tower_weight" and not any(action["type"] == "tower" for action in events):
            continue
        if name == "kill_weight" and not any(action["type"] == "kill" for action in events):
            continue
        sweeps[name] = {}
        for amount in options:
            replay = Replay(scenario, rules, case, events, grade, trace=False, **{name: amount})
            result = replay.run()
            sweeps[name][amount] = {"gaps": result["gaps"], "endGaps": result["endGaps"],
                                    "end": result["end"], "purchaseMilestonesTicks": result["purchaseMilestonesTicks"],
                                    "convergence": result["convergence"], "towerProtection": replay.projection}
    return sweeps


def calculate(scenario, tracks):
    output = {}
    for name, case, label, events, grade in scenario_matrix(scenario):
        runs = {}
        for track, rules in tracks.items():
            replay = Replay(scenario, rules, case, events, grade)
            result = replay.run()
            result["closingStats"] = closing_stats(result)
            result["reminders"] = reminders(case, result)
            if case not in ("S0", "reconnect"):
                result["sensitivity"] = sensitivity(scenario, rules, case, events, grade)
            runs[track] = result
        output[name] = {"label": label, "runs": runs}
    # Alternate allocation frontiers share quote schedules in these paired tracks. Keep each budget once per track.
    frontiers = {}
    for track, rules in tracks.items():
        IDs = set()
        for data in output.values():
            run = data["runs"][track]
            frames = list(run["checkpoints"].values()) + list(run["nextRecallStates"].values())
            frames.extend(row["immediateStates"] for row in run["objectives"] if "immediateStates" in row)
            for frame in frames:
                IDs.update(seat["frontierBudget"] for seat in frame.values())
        frontiers[track] = {budget: frontier(rules, budget) for budget in sorted(IDs)}
    return output, frontiers


def summarize_public_trial(result):
    return {"checkpoints": result["checkpoints"], "gaps": result["gaps"], "end": result["end"],
            "closingStats": closing_stats(result), "endGaps": result["endGaps"],
            "objectives": result["objectives"], "convergence": result["convergence"],
            "purchaseMilestonesTicks": result["purchaseMilestonesTicks"]}


def public_trials(scenario, rules):
    settings = scenario["publicTrials"]
    sweep = {}
    for id_, case, _, events, grade in scenario_matrix(scenario):
        trials = {}
        for field, keyword in (("wageCatchUpWeight", "wage_rate"), ("assistGold", "assist_gold"),
                               ("assistXp", "assist_xp")):
            if field != "wageCatchUpWeight" and not any(e.get("assists") for e in events):
                continue
            results = {}
            for amount in settings[field]:
                run = Replay(scenario, rules, case, events, grade, trace=False, **{keyword: amount}).run()
                results[amount] = summarize_public_trial(run)
            trials[field] = results
        sweep[id_] = trials
    return sweep


def defense_trials(scenario, rules, s0):
    settings = scenario["publicTrials"]
    hits = settings["defenseRawHits"]
    patterns = {kind: [kind] * len(hits) for kind in ("physical", "spell", "true")}
    patterns["mixed"] = settings["defenseMixedKinds"]
    comparisons = []
    for second in settings["defenseTimesSeconds"]:
        seat = s0["checkpoints"][second * TICKS]["G1"]
        level, budget = seat["level"], seat["frontierBudget"]
        for role in ("luoxia", "yingxiao", "keheng", "qingluo"):
            stats = rules.roles[role]["attributes"]
            hp = value(stats, "health")[level - 1]
            base = {"physical": value(stats, "physicalDefense"), "spell": value(stats, "magicResistance")}
            reference_hp = 575 + 75 * (level - 1)
            for ranks in settings["defenseRanks"]:
                cost = sum(rules.price(rank) for rank in range(ranks))
                variants = {}
                for defense in settings["defensePerPoint"]:
                    outcomes = {}
                    for pattern, kinds in patterns.items():
                        health, registered, death, trace = hp, 0, None, []
                        without_purchases = hp
                        for (tick, raw), kind in zip(hits, kinds, strict=True):
                            multiplier = 1 if kind == "true" else 100 / (100 + base[kind] + ranks * defense)
                            damage = raw * multiplier
                            loss = min(health, damage)
                            without_purchases -= raw if kind == "true" else raw * 100 / (100 + base[kind])
                            health -= loss
                            registered += damage
                            if health == 0 and death is None:
                                death = tick
                            trace.append({"tick": tick, "kind": kind, "raw": raw, "mitigated": damage,
                                          "effectiveLoss": loss, "remainingHp": health})
                        avoided = health - max(0, without_purchases)
                        outcomes[pattern] = {"incomingAfterMitigation": registered, "remainingHp": health,
                                             "effectiveLifeLost": hp - health, "firstLethalTick": death,
                                             "avoidedVsZeroPurchaseHp": avoided,
                                             "budgetPoints": 100 * avoided / reference_hp,
                                             "budgetPointsPer1000Gold": 100000 * avoided / (reference_hp * cost) if cost else None,
                                             "events": trace}
                    variants[defense] = outcomes
                comparisons.append({"atSecond": second, "role": role, "level": level, "maximumHealth": hp,
                                    "baselineDefense": base, "referenceHp": reference_hp, "spendableBudget": budget,
                                    "defenseRanks": ranks, "cost": cost, "affordable": cost <= budget,
                                    "remainingGold": budget - cost if cost <= budget else None,
                                    "alternativesAtSameCost": {"strengthPhysicalAttack": ranks * rules.permanent["physicalAttackPerStrength"],
                                                              "wisdomSpellPower": ranks * rules.permanent["spellPowerPerWisdom"]},
                                    "trialsByResistancePerPoint": variants})
    return {"source": "DamageCalc#mitigation | FirstGroupGrowth#price | paired playable role attributes",
            "basis": "fixed incoming hit vectors, zero penetration, no regen/SR/shield/other mitigation",
            "economyAnchor": "S0 guardian core same-time spendable budget, with four separate candidate targets",
            "comparisons": comparisons}


def add_v2_summary(report, evidence, scenario, tracks):
    for data in report["cases"].values():
        for run in data["runs"].values():
            run["teamCheckpoints"] = {tick: {team: team_stats([state for id_, state in states.items()
                                                             if id_.startswith(team[0].upper())])
                                            for team in TEAMS} for tick, states in run["checkpoints"].items()}
            run["eventCounts"] = {
                "laneDeaths": dict(Counter(row["unit"] for row in run["trace"] if row["event"] == "lane")),
                "campDeaths": dict(Counter(row["unit"] for row in run["trace"]
                                           if row["event"] == "camp" and "skip" not in row)),
                "siegeSpawns": sum(row["slots"] for row in run["trace"] if row["event"] == "siege_spawn"),
                "towerBreaks": [row["id"] for row in run["objectives"] if row["kind"] == "tower"],
                "confirmedRespawns": [row for row in run["trace"] if row["event"] == "respawn"],
            }
    baseline = tracks["baseline"]
    report.update({"formatVersion": 2, "battlefieldEvidence": evidence,
                   "publicTrials": public_trials(scenario, baseline), "defenseTrials": defense_trials(
                       scenario, baseline, report["cases"]["S0"]["runs"]["baseline"]),
                   "parameterDefinitions": {"defensePerPoint": "physical and magic resistance added per defense point",
                                            "assistGold": "actual credited gold pool split by eligible locked roster order",
                                            "assistXp": "credited experience pool split by eligible locked roster order",
                                            "wageCatchUpWeight": "only the additional catch-up fraction on the timed base wage"},
                   "missingEvidence": ["真人 3v3 补刀归属、交战、塔前路线与破塔过程",
                                       "真实候选局终局时间及参战体验反馈",
                                       "同态同预算三种伤害序列的引擎抽检"]})


def elapsed(tick):
    if tick is None:
        return "未达"
    second = tick / TICKS
    sec = f"{second % 60:05.2f}".rstrip("0").rstrip(".")
    return f"{int(second // 60):02}:{sec}"


def signed(number):
    return f"{number:+.2f}".rstrip("0").rstrip(".")


def pct(number):
    return "不适用" if number is None else f"{number:.1%}"


def snapshot_summary(rules, manifest):
    metadata = manifest["balance_snapshot"]
    summary = {"sourceSha": manifest["source_sha"], "sourceDirty": metadata["source_dirty"],
            "snapshotFormat": metadata["format_version"], "snapshotSha256": metadata["sha256"],
            "rulesSha256": metadata["rules_sha256"], "jarSha256": manifest["sha256"],
            "attrAccess": rules.attribute_access, "supplyAccess": rules.supply_access,
            "catchUpSeats": rules.farm_seats, "assistPool": rules.assist_pool,
            "wageCatchUpWeight": rules.wage_weight, "normalWaitTicks": rules.wait,
            "mapTowerSlots": list(rules.map["towers"])}
    if rules.tempo == "CHERRY_V2":
        summary.update({"defensePerPoint": rules.permanent["defensePerPoint"], "tempo": rules.tempo,
                        "laneRewardsBeforeScaling": rules.lane_rewards,
                        "openingPurse": rules.opening, "baseWageBeforeGoldMultiplier": rules.wage,
                        "wageIntervalTicks": rules.wage_ticks,
                        "activeFarmCatchUp": dict(zip(("goldGapWeight", "levelGapWeight", "cap", "goldIncomeFloor"),
                                                      rules.farm, strict=True))})
    return summary


def markdown(report):
    names = {"baseline": "改前", "candidate": "候选"}
    out = ["# 樱隙前线 3v3 经济复演", "",
           "两轨均为固定实体事件的离线复演。候选池额和工资权重待首轮 3v3 选定。所有三级提醒仅供试测参考。", "",
           "| 轨道 | 源码提交 | 规则摘要 | 助攻池 | Lv18／19／20 复活秒 |",
           "| --- | --- | --- | ---: | ---: |"]
    for track, version in report["snapshots"].items():
        pool = version["assistPool"]
        pool_text = f"{pool['gold']}g／{pool['xp']} XP" if pool else "无经济池"
        times = "/".join(f"{version['normalWaitTicks'][lv] / TICKS:g}" for lv in (18, 19, 20))
        out.append(f"| {names[track]} | {version['sourceSha']} | {version['rulesSha256']} | {pool_text} | {times} |")
    out.extend(["", "终局参考为 Lv18–20、12–16 点、主动收入 60%–70%、复活 42–45 秒。切入脚本的预置历史没有来源拆分，因此主动比例留空。", "",
                "| 场景 | 轨道 | 终点 | 蓝／红核心累计金 | 蓝／红核心等级／购点 | 人均等级／购点 | 主动占比 | 三级信号 |",
                "| --- | --- | ---: | ---: | --- | --- | ---: | --- |"])
    for id_, data in report["cases"].items():
        for track, result in data["runs"].items():
            stats = result["closingStats"]
            end = result["end"]
            g, d = end["G1"], end["D1"]
            signal = "；".join(r["message"] for r in result["reminders"]) or "—"
            active = pct(stats["activeShare"]) if "-" not in id_ else "预置历史未分"
            finish = 960 if "-" in id_ else 1200
            out.append(f"| {id_} {data['label']} | {names[track]} | {finish // 60}:00 | {g['earned']}／{d['earned']} | "
                       f"{g['level']}级/{g['points']}点 · {d['level']}级/{d['points']}点 | "
                       f"{stats['avgLevel']:.2f}/{stats['avgPoints']:.2f} | {active} | {signal} |")
    out.extend(["", "## 六个进点与 8:00 切入", "",
                "每格按「累计金／等级／已购点／余金／下一点缺金」列核心。逐席 XP、分账、候选规则准入、整数配点前沿和下一次实购等待看 JSON。切入场景范围为 8:00 至 16:00。", "",
                "| 场景 | 轨道 | 时间 | 蓝方核心 | 红方核心 | 蓝减红人均累计金／等级／购点 |",
                "| --- | --- | ---: | ---: | ---: | ---: |"])
    for id_, data in report["cases"].items():
        for track, result in data["runs"].items():
            for tick, states in result["checkpoints"].items():
                g, d = states["G1"], states["D1"]
                digits = lambda s: f"{s['earned']}/{s['level']}/{s['points']}/{s['wallet']}/{s['nextGap']}"
                gap = result["gaps"][tick]
                out.append(f"| {id_} | {names[track]} | {elapsed(tick)} | {digits(g)} | {digits(d)} | "
                           f"{signed(gap['teamGold'])}/{signed(gap['teamLevel'])}/{signed(gap['teamPoints'])} |")
    out.extend(["", "## 纯防守和首次反打", "",
                "| 场景 | 轨道 | 8→12／8→16 相对队伍收入差收窄 | 首次交锋后核心累计金差变化 | 首次达到可争夺线 |",
                "| --- | --- | ---: | ---: | --- |"])
    for id_, data in report["cases"].items():
        for track, result in data["runs"].items():
            convergence = result["convergence"]
            defense = [d for d in convergence["defense"] if d["startSecond"] == 480]
            first = next(iter(convergence["exchanges"]), None)
            contest = convergence["contest"]
            if not defense and not first:
                continue
            closes = "／".join(pct(d["relativeTeamIncomeShrink"]) for d in defense) or "—"
            hit = (f"{first['before']['coreGold']}→{first['after']['coreGold']}g（{pct(first['coreGoldShrink'])}）"
                   if first else "—")
            reach = f"{elapsed(contest['tick'])}（{contest['rounds']}轮）" if contest and contest["tick"] else "未达"
            if contest and contest["alreadyContestable"]:
                reach += "，首次交锋前已在参考线内"
            out.append(f"| {id_} | {names[track]} | {closes} | {hit} | {reach if contest else '—'} |")
    out.extend(["", "## 单因子敏感度", "",
                "工资只调整追赶分量。主动获取用红方补刀和野营的事件资格分别乘 0.85／1／1.15。主动追赶、交锋额外赏金与助攻池、塔保各自单独调整。每格列终点的红方核心累计金／等级／点数，破塔投影、进点、金差、结算来源与反打时点见 JSON。", "",
                "| 场景 | 轨道 | 因子 | 0× 或 0.85× | 0.5× | 1× | 1.5× 或 1.15× |",
                "| --- | --- | --- | ---: | ---: | ---: | ---: |"])
    factors = {"wage_weight": "工资追赶", "active": "主动获取率", "farm_weight": "主动追赶",
               "kill_weight": "交锋额外赏金及助攻池", "tower_weight": "塔保"}
    for id_, data in report["cases"].items():
        for track, result in data["runs"].items():
            for key, sweep in result.get("sensitivity", {}).items():
                order = (.85, .5, 1, 1.15) if key == "active" else WEIGHTS
                columns = []
                for variant in order:
                    if variant in sweep:
                        d = sweep[variant]["end"]["D1"]
                        columns.append(f"{d['earned']}/{d['level']}/{d['points']}")
                    else:
                        columns.append("—")
                out.append(f"| {id_} | {names[track]} | {factors[key]} | " + " | ".join(columns) + " |")
    out.extend(["", "## 掉线、等待和回泉", "",
                "| 轨道 | 蓝方离线席位分母 | 冻结金／等级 | 重连待发助攻金／XP | 死亡等待买回春丹 |",
                "| --- | ---: | ---: | ---: | --- |"])
    for track, run in report["cases"]["reconnect"]["runs"].items():
        by_id = {row["id"]: row for row in run["objectives"]}
        receipt = by_id["reconnect-G3"]
        connection = next(row for row in run["trace"] if row["event"] == "disconnect")
        denominator = connection["nextAverages"]["guardian"][0]
        out.append(f"| {names[track]} | {denominator} | {receipt['income']}/{receipt['level']} | "
                   f"{receipt['queuedGold']}/{receipt['queuedXp']} | "
                   f"{'成交' if by_id['waiting-pill']['result'] == 'bought' else '权限拒绝'} |")
    out.extend(["", "## 依据与缺口", "",
                "- 游戏来源、两份发行摘要、固定场景摘要、完整逐笔账本和预算前沿在配套 JSON。蓝方为守护／预设领先侧，红方为毁灭／预设受压侧。",
                "- 纯防守参考为 4 分钟相对差收窄 10%–30% 和 8 分钟 20%–45%。单次有效终结参考缩差 25%–55%，可争夺线同时需要核心等级差及购点差各至多一。",
                "- 二阶塔为决议测试用固定合成塔位。樱隙地图快照登记中路一阶、三阶塔。推进保护投影使用公开的约 15% 一波塔血锚点及场景给定压力，没有碰撞、英雄对塔节拍或真实破塔测量。",
                "- 固定交锋为输入。路线、输出、塔前兵线及团战胜率需要后续引擎、真人样本。复活档位来自快照，死后保留工资及离线席位规则在账本逐事件重放。",
                "- 独立离线入口为 tools/balance/replay-economy.py。服务器、购买界面及真人 3v3 的证据应与本报告分别引用。",
                ""])
    return "\n".join(out)


def v2_markdown(report):
    snapshots = report["snapshots"]
    same_release = snapshots["baseline"]["snapshotSha256"] == snapshots["candidate"]["snapshotSha256"]
    selected = report["amountsSelectedForFormalCalibration"]
    tracks = {"baseline": "联验现值"}
    if not same_release:
        tracks["candidate"] = "正式选值构建" if selected else "复测构建"
    out = ["# 樱隙前线 V2 经济与公共参数复跑", "",
           "3v3 六席、四条全程剧本、九条中途切入剧本及一个掉线回归。兵、野、塔奖励取配对快照与 #109 合成 GameTest 读数。交锋和路线为固定输入。三级提醒供策划试测。", "",
           "| 构建 | 源码 | 规则 SHA-256 | 防御每点 | 助攻金／XP | 工资追赶 |",
           "| --- | --- | --- | ---: | ---: | ---: |"]
    for track, name in tracks.items():
        r = snapshots[track]
        out.append(f"| {name} | `{r['sourceSha']}` | `{r['rulesSha256']}` | +{r['defensePerPoint']:g} | "
                   f"{r['assistPool']['gold']}／{r['assistPool']['xp']} | {r['wageCatchUpWeight']:g}× |")
    if selected:
        out.extend(["", f"策划决定为{report['decision']}。以下复测读数使用新构建配对快照中的生效值。", "",
                    "| 20:00 剧本 | 复测平均等级 | 复测平均购点 | 主动收入占比 | 普通复活均值 | 蓝减红人均累计金，改前 → 复测 |",
                    "| --- | ---: | ---: | ---: | ---: | ---: |"])
        for case in ("S0", "S1", "S2", "S3"):
            runs = report["cases"][case]["runs"]
            before, after = runs["baseline"], runs["candidate"]
            st = after["closingStats"]
            out.append(f"| {case} | Lv{st['avgLevel']:.2f} | {st['avgPoints']:.2f} | {pct(st['activeShare'])} | "
                       f"{st['avgNormalRespawnTicksAtLevel'] / TICKS:.1f}s | "
                       f"{before['endGaps']['teamGold']:+,.2f}g → {after['endGaps']['teamGold']:+,.2f}g |")
    out.extend(["", "## 六个主时点", "",
                "两侧各用三席均值。每格依次是人均累计金、等级、已购点、主动收入占比、该等级的普通复活秒数。实有阵亡与回场时刻见 JSON 的逐人事件。", "",
                "| 剧本 | 构建 | 时点 | 守护 | 毁灭 |", "| --- | --- | ---: | --- | --- |"])
    for case in ("S0", "S1", "S2", "S3"):
        for track, name in tracks.items():
            run = report["cases"][case]["runs"][track]
            for tick, teams in run["teamCheckpoints"].items():
                cells = []
                for team in TEAMS:
                    t = teams[team]
                    cells.append(f"{t['income'] / 3:,.0f}g／Lv{t['avgLevel']:.2f}／{t['avgPoints']:.2f} 点／"
                                 f"{pct(t['activeShare'])}／{t['avgNormalRespawnTicksAtLevel'] / TICKS:.1f}s")
                out.append(f"| {case} | {name} | {elapsed(tick)} | " + " | ".join(cells) + " |")
    out.extend(["", "终局参考是 Lv18–20、12–16 点、主动收入 60%–70%、复活显示 42–45 秒。选值试算与这些节奏缺口一并保留。已买属性沿实际过线购入，整数预算前沿可供角色配点。", "",
                "## 兵、野、塔与复活", "",
                "| 剧本 | 已结算兵死亡事件 | 已结算营地 | 攻城兵生成 | 塔破时点／每席金 | 实际完成的复活 |",
                "| --- | ---: | ---: | ---: | --- | ---: |"])
    for case in ("S0", "S1", "S2", "S3"):
        run = report["cases"][case]["runs"]["baseline"]
        counts = run["eventCounts"]
        towers = [f"{elapsed(row['tick'])}／{row['goldPerRecipient']}g" for row in run["objectives"] if row["kind"] == "tower"]
        out.append(f"| {case} | {sum(counts['laneDeaths'].values())} | {sum(counts['campDeaths'].values())} | "
                   f"{counts['siegeSpawns']} | {'、'.join(towers) or '—'} | {len(counts['confirmedRespawns'])} |")
    e = report["battlefieldEvidence"]
    out.extend(["", f"配对核实 {len(e['laneRewardObservations'])} 条 V2 兵死亡读数、{len(e['campObservations'])} 类尾刀与重生读数、"
                f"{len(e['towerRewardObservations'])} 个实际樱隙塔阶的到账金额。外塔固定用多人护送合成破塔刻，高地塔固定用后段第三波合成破塔刻。最后一波已生成的攻城兵可能到 20 分钟后才结算。"
                "保护敏感度是同一入塔波压力下的投影，实际真人推进速度留待实测。", "",
                "兵线采用 V2 线性收益成长，法兵与攻城兵分别以 40 和 65 基础金计算。清线用合成测试的时长加约定到线时间。野区路线为固定开打时刻，尾刀增加同类型合成击杀时长，每 20 刻轮询刷新。不同真人配装、站位和仇恨可能改变机会。", "",
                "## 同预算双抗", "",
                "六笔合计 960 原始来伤，物理、法术、真实伤害分别走同一事件轴，另列混合序列。零穿透、满血、无回复无盾。两种每点双抗以同报价比较。价格和四个目标的各事件剩余生命见 JSON。", "",
                "| 防御购点 | 花费 | 物理 +5／+15 | 法术 +5／+15 | 真实 +5／+15 | 混合 +5／+15 |",
                "| ---: | ---: | ---: | ---: | ---: | ---: |"])
    for row in report["defenseTrials"]["comparisons"]:
        if (row["atSecond"], row["role"]) != (1200, "luoxia"):
            continue
        trials = row["trialsByResistancePerPoint"]
        cells = ["／".join(f"{trials[resist][kind]['incomingAfterMitigation']:.1f}" for resist in (5, 15))
                 for kind in ("physical", "spell", "true", "mixed")]
        note = "，超出该时点预算" if not row["affordable"] else ""
        out.append(f"| {row['defenseRanks']} | {row['cost']:,}g{note} | " + " | ".join(cells) + " |")
    out.extend(["", "## 工资追赶", "", "| 倍率 | S1 红方追赶金中工资占比 | S1 20:00 红核心累计金／点 | S1 20:00 蓝减红人均收入 | C0 中档 8→12／8→16 相对差收窄 |",
                "| ---: | ---: | ---: | ---: | ---: |"])
    trials = report["publicTrials"]
    for amount, s1 in trials["S1"]["wageCatchUpWeight"].items():
        c0 = trials["medium-C0"]["wageCatchUpWeight"][amount]
        d = s1["end"]["D1"]
        windows = [pct(row["relativeTeamIncomeShrink"]) for row in c0["convergence"]["defense"] if row["startSecond"] == 480]
        out.append(f"| {amount:g}× | {pct(s1['convergence']['wageShareOfTrailingCatchUpGold'])} | "
                   f"{d['earned']:,}g／{d['points']} 点 | {s1['endGaps']['teamGold']:+.2f}g | {'／'.join(windows)} |")
    out.extend(["", "C0 相对差用落后方收入作分母，双方共同继续赚金本身也会缩小这个比值。JSON 同时列绝对差缩窄。4／8 分钟正常参考为 10%–30%／20%–45%，工资占追赶金超过 40% 时提示。", "",
                "## 助攻金池与经验池", "",
                "以联验构建为起点，每次只改变一个池额，工资追赶保持联验值。助攻池是实际结算总额。合格一人取全池，两人按锁定名单顺序分钱并分配奇数余份。", "",
                "| 助攻金池 | S2 红游走累计金／已购点 | S3 20:00 蓝减红人均收入 | 助攻 XP 池 | S2 六席平均等级 | S2 红核心等级／经验槽 |",
                "| ---: | ---: | ---: | ---: | ---: | ---: |"])
    for (gold, s2_gold), (xp, s2_xp) in zip(trials["S2"]["assistGold"].items(), trials["S2"]["assistXp"].items(), strict=True):
        roam, core = s2_gold["end"]["D2"], s2_xp["end"]["D1"]
        s3 = trials["S3"]["assistGold"][gold]
        out.append(f"| {gold}g | {roam['earned']:,}g／{roam['points']} 点 | {s3['endGaps']['teamGold']:+.2f}g | "
                   f"{xp} XP | {s2_xp['closingStats']['avgLevel']:.2f} | Lv{core['level']}／{core['xp']} XP |")
    out.extend(["", "S2 核心在首轮反打前已经达到 #100 的可争夺线。中档固定切入 C1 用于独立归因，仍按 8:00 预置差距重放。两类数字和预算、实际购点与下一点缺金均可从 JSON 回查。", "",
                "## 选值依据与待采证据" if selected else "## 试算建议与待采证据", "",
                "- 防御保留每点 +5 双抗。+15 在 4 点时对单类物理和法术各少承约 114 生命，同价保留真实伤害的全部压力。需要和同经济刺客收尾、持续输出和多角色防御配点一起测试。",
                "- 助攻金／经验暂选 101g／51 XP。更高金币池会同时提高连续获胜方的领先，当前助攻金试算没有改变 S2 红游走终点 14 点。高 XP 档只在边界使个别席升级，终局经验缺口需要另采供给与全局 XP 记录。",
                "- 工资追赶建议 0×，保留所有常规工资和主动发育追赶。0.5× 是偏向纯守方的备选。由 1× 降为 0× 时，S1 红核心会少一购点，S3 终局蓝方平均购点差多约三分之一个点。",
                "- 六人真人全局没有样本。四种入伤序列为固定输入的公式试算，等待同态引擎读数。关卡路线、推塔压力、补刀分配、有效团战与终局时长也需要真人对照。",
                "- JSON 记录每份 JAR、快照、规则、战场对照和脚本场景的 SHA-256，以及两轨 14 案、逐笔记录、各时点购买与整数配点前沿。", ""])
    if not selected:
        out.extend(["四项正式值待策划确认。", ""])
    out.extend(["## 复跑", "", "从仓库根目录传入联验发行目录、新构建发行目录及联验战场对照。", "", "```sh",
                "python tools/balance/replay-economy.py \\",
                "  --scenario tools/balance/scenarios/economy-cherry-v2.json \\",
                "  --baseline /release/cherry-v2-reference \\",
                "  --candidate /release/cherry-v2-retest \\",
                "  --battlefield-evidence /release/battlefield-report.json \\"])
    if selected:
        out.append(f"  --decision {quote(report['decision'])} \\")
    out.extend(["  --output /review/economy", "```", ""])
    return "\n".join(out) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True, help="directory with the baseline release.json, JAR and snapshot")
    parser.add_argument("--candidate", type=Path, required=True, help="directory with the candidate release.json, JAR and snapshot")
    parser.add_argument("--scenario", type=Path, default=SCENARIO)
    parser.add_argument("--battlefield-evidence", type=Path,
                        help="paired replay-battlefield.py JSON from the CHERRY_V2 reference release")
    parser.add_argument("--decision", help="planner decision recorded with a paired candidate build")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    scenario = load(args.scenario)
    version, tempo = scenario["version"], scenario["map"]
    if (version, tempo) not in ((1, "CHERRY_V1"), (2, "CHERRY_V2")) or scenario["mode"] != "3v3":
        parser.error("Expected scenario version 1 or 2 for its corresponding CHERRY 3v3 map")
    if version == 2 and args.battlefield_evidence is None:
        parser.error("CHERRY_V2 requires paired --battlefield-evidence")
    if version == 1 and (args.battlefield_evidence or args.decision):
        parser.error("The historical CHERRY_V1 replay does not support these options")
    releases = {"baseline": paired_release(args.baseline), "candidate": paired_release(args.candidate)}
    tracks = {track: Rules(snapshot, track, tempo, current=version == 2)
              for track, (snapshot, _) in releases.items()}
    if version == 2:
        evidence = read_battlefield_evidence(args.battlefield_evidence, scenario, tracks["baseline"], releases["baseline"][1])
    results, frontiers = calculate(scenario, tracks)
    report = {"formatVersion": 1, "basis": "fixed-event offline replay", "scenario": scenario,
              "scenarioSha256": hashlib.sha256(args.scenario.read_bytes()).hexdigest(), "sources": SOURCES,
              "ticksPerSecond": TICKS, "pointOrder": ATTRIBUTES, "fullGameHumanSample": None,
              "amountsSelectedForFormalCalibration": bool(args.decision), "snapshots": {
                  track: snapshot_summary(tracks[track], manifest) for track, (_, manifest) in releases.items()},
              "cases": results, "frontiers": frontiers}
    args.output.mkdir(parents=True, exist_ok=True)
    name = "economy-report"
    render = markdown
    if version == 2:
        report["decision"] = args.decision
        add_v2_summary(report, evidence, scenario, tracks)
        name, render = "economy", v2_markdown
    (args.output / f"{name}.md").write_text(render(report), encoding="utf-8")
    (args.output / f"{name}.json").write_text(
        json.dumps(report, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Replayed {len(results)} paired cases to {args.output}")


if __name__ == "__main__":
    main()
