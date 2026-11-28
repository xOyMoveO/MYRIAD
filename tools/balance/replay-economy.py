"""Replay the fixed #108 CHERRY_V1 economy scenarios from two paired Fabric releases."""
import argparse
import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from functools import lru_cache
from math import floor
from pathlib import Path
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


class Rules:
    def __init__(self, snapshot, track):
        self.track = track
        self.battlefield = snapshot["rules"]["battlefield"]
        self.economy = self.battlefield["economy"]
        self.farm_seats = value(self.economy, "farmReward")
        if self.farm_seats == "online_participant_averages" and track == "baseline":
            # MatchBalance at #107. These constants were only exported in #108.
            self.farm = (.9, .15, .45, 500)
            self.wage_weight = 1
            self.attribute_access = self.supply_access = "own_fountain_or_waiting"
            self.assist_pool = None
        elif self.farm_seats == "locked_seat_averages" and track == "candidate":
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
        self.check_samples()

    def gold(self, base):
        return round_java(base * self.gold_scale)

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
            for sample in definition["rewardSamplesBeforeCatchUpAndGlobalGold"]:
                tick = sample["elapsedTicks"]
                gold = value(definition, "baseGold")
                xp = value(definition, "baseExperience")
                actual = (minion_reward(gold, tick, 100), minion_reward(xp, tick, 100),
                          minion_reward(gold, tick, 25), minion_reward(xp, tick, 80))
                expected = tuple(value(sample, key) for key in (
                    "lastHitGold", "lastHitExperience", "nonPlayerKillNearbyGold", "nearbyExperience"))
                if actual != expected:
                    raise ValueError(f"Minion formula differs from snapshot samples for {unit}")


def minion_reward(base, tick, share):
    return base * (10 + min(10, tick // 2400)) * share // 1000


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
                total += multiplier * pressure["rawHealthFraction"]
                waves.append([second, multiplier, total])
                if total >= 1:
                    break
        projection[event["id"]] = {"breakSecond": waves[-1][0] if total >= 1 else None,
                                   "fixedSecond": event["at"], "waves": waves,
                                   "assumedEntity": event["tower"]}
    return projection


def make_timeline(scenario, rules, case, events, end, active, tower_weight):
    scheduled = defaultdict(list)
    trips = defaultdict(list)
    relevant_towers = tower_projection(events, scenario, rules, tower_weight)
    for action in events:
        event = dict(action)
        tower = relevant_towers.get(event.get("requiresTower"))
        if tower_weight != 1:
            if event["type"] == "tower":
                changed = relevant_towers[event["id"]]["breakSecond"]
                if changed is None:
                    continue
                event["at"] = changed
            if tower:
                if tower["breakSecond"] is None:
                    continue
                event["at"] += tower["breakSecond"] - tower["fixedSecond"]
        scheduled[event["at"] * TICKS].append(event)
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
    for events_at_tick in scheduled.values():
        for event in events_at_tick:
            if event["type"] != "camp" or "skip" in event or event["at"] > end:
                continue
            second = event["at"]
            trips[event["player"]].append((second - camp_plan["laneMissBeforeSeconds"],
                                           second + camp_plan["laneMissAfterSeconds"]))

    offset = scenario["waveDeathAfterSpawnSeconds"] * TICKS
    start = value(rules.map, "firstWave")
    interval = value(rules.catalog, "waveInterval")
    melee = value(rules.catalog, "meleeSlots")
    ranged = value(rules.catalog, "rangedSlots")
    period = value(rules.catalog, "magicInterval")
    defaults = scenario["lane"]
    for wave, spawn in enumerate(range(start, end * TICKS + 1, interval), 1):
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
                scheduled[tick].append({"id": f"{team}-mid-{wave}-{slot + 1}", "at": tick / TICKS,
                                        "type": "lane", "team": team, "unitFaction": TEAMS[1 - i],
                                        "unit": unit, "wave": wave,
                                        "finisher": prefix + str(POSITIONS[role]) if qualifies else None})
    return scheduled, trips, relevant_towers


class Replay:
    def __init__(self, scenario, rules, case, events, grade=None, *, trace=True, wage_weight=1,
                 active=1, farm_weight=1, kill_weight=1, tower_weight=1):
        self.scenario = scenario
        self.rules = rules
        self.case = case
        self.grade = grade
        self.wage_weight = rules.wage_weight * wage_weight
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
        self.scheduled, self.trips, self.projection = make_timeline(
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

    def wage(self):
        # Snapshot all awards before committing any money so team iteration cannot alter the pulse.
        base = self.rules.gold(self.rules.wage)
        awards = [(seat, self.rules.gold(round_java(self.rules.wage * (1 + self.farm_bonus(seat)[0] * self.wage_weight))))
                  for seat in self.seats.values() if seat.online]
        for seat, actual in awards:
            self.award(seat, base, actual - base, source="wage", cause="scheduled wage")

    def earn_farm(self, seat, gold, xp, kind, cause):
        gb, xb = self.farm_bonus(seat)
        base_gold = self.rules.gold(gold)
        final_gold = self.rules.gold(round_java(gold * (1 + gb * self.farm_weight)))
        final_xp = round_java(xp * (1 + xb * self.farm_weight))
        self.award(seat, base_gold, final_gold - base_gold, xp, final_xp - xp, source=kind, cause=cause)

    def lane(self, event):
        team = self.teams[event["team"]]
        assigned = event["finisher"]
        eligible = [seat for seat in team if self.eligible_lane(seat)]
        finisher = self.seats[assigned] if assigned else None
        leading = finisher in eligible
        if leading:
            eligible.remove(finisher)
            eligible.insert(0, finisher)
        definition = self.rules.catalog["types"][event["unit"]]
        gold, xp = value(definition, "baseGold"), value(definition, "baseExperience")
        self.record("lane", **event, actualFinisher=assigned if leading else None,
                    nearby=[seat.config["id"] for seat in eligible],
                    missed=[seat.config["id"] for seat in team if seat not in eligible])
        for seat in eligible:
            last = leading and seat is finisher
            self.earn_farm(seat, minion_reward(gold, self.tick, 100 if last else (0 if leading else 25)),
                           minion_reward(xp, self.tick, 100 if last else 80), "lane", event["id"])

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
        timing = definition["fixedCampTiming"][self.scenario["map"]]
        available = self.camp_available.get(event["camp"], value(timing, "firstSpawn"))
        if self.tick < available:
            self.record("camp", **event, unit=unit, skip="not spawned", availableTick=available)
            return
        if value(definition, "teamGold"):
            raise ValueError(f"Team gold camp {unit} is outside the fixed CHERRY_V1 event inputs")
        self.camp_available[event["camp"]] = self.tick + value(timing, "respawnDelay")
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
        pool = self.rules.assist_pool
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
            states[id_] = state
        return states

    def run(self):
        self.seed()
        wanted = {second * TICKS for second in self.scenario["seconds"] if self.start <= second * TICKS <= self.end}
        moments = wanted | set(self.scheduled) | {self.start, self.end}
        wage = self.rules.wage_ticks
        moments.update(range((self.start // wage + 1) * wage, self.end + 1, wage))
        shops = {s * TICKS for s in self.scenario["shops"]}
        moments.update(shops)
        for event_list in self.scheduled.values():
            for event in event_list:
                if event["type"] == "kill":
                    moments.update(event["at"] * TICKS + wait for wait in self.rules.wait.values())
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
            if self.tick in shops:
                for task in self.scenario["supplyPlan"]:
                    if self.tick // TICKS in task["occasions"]:
                        for team in TEAMS:
                            seat = self.teams[team][POSITIONS[task["player"]] - 1]
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


def closing_stats(result):
    seats = list(result["end"].values())
    split = Counter()
    for state in seats:
        split.update(state["sources"])
    active = sum(split[key] for key in ("lane", "laneAdjustment", "camp", "campAdjustment",
                                       "kill", "killAdjustment", "assist", "tower"))
    earned = sum(s["earned"] for s in seats)
    return {"income": earned, "sources": dict(split), "activeShare": active / earned if earned and not split["cutSeed"] else None,
            "avgLevel": sum(s["level"] for s in seats) / len(seats),
            "avgPoints": sum(s["points"] for s in seats) / len(seats)}


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
    return {"sourceSha": manifest["source_sha"], "sourceDirty": metadata["source_dirty"],
            "snapshotFormat": metadata["format_version"], "snapshotSha256": metadata["sha256"],
            "rulesSha256": metadata["rules_sha256"], "jarSha256": manifest["sha256"],
            "attrAccess": rules.attribute_access, "supplyAccess": rules.supply_access,
            "catchUpSeats": rules.farm_seats, "assistPool": rules.assist_pool,
            "wageCatchUpWeight": rules.wage_weight, "normalWaitTicks": rules.wait,
            "mapTowerSlots": list(rules.map["towers"])}


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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True, help="directory with the baseline release.json, JAR and snapshot")
    parser.add_argument("--candidate", type=Path, required=True, help="directory with the candidate release.json, JAR and snapshot")
    parser.add_argument("--scenario", type=Path, default=SCENARIO)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    scenario = load(args.scenario)
    if (scenario["version"], scenario["map"], scenario["mode"]) != (1, "CHERRY_V1", "3v3"):
        parser.error("Expected scenario version 1 for CHERRY_V1 3v3")
    releases = {"baseline": paired_release(args.baseline), "candidate": paired_release(args.candidate)}
    tracks = {track: Rules(snapshot, track) for track, (snapshot, _) in releases.items()}
    results, frontiers = calculate(scenario, tracks)
    report = {"formatVersion": 1, "basis": "fixed-event offline replay", "scenario": scenario,
              "scenarioSha256": hashlib.sha256(args.scenario.read_bytes()).hexdigest(), "sources": SOURCES,
              "ticksPerSecond": TICKS, "pointOrder": ATTRIBUTES, "fullGameHumanSample": None,
              "amountsSelectedForFormalCalibration": False, "snapshots": {
                  track: snapshot_summary(tracks[track], manifest) for track, (_, manifest) in releases.items()},
              "cases": results, "frontiers": frontiers}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "economy-report.md").write_text(markdown(report), encoding="utf-8")
    (args.output / "economy-report.json").write_text(
        json.dumps(report, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Replayed {len(results)} paired cases to {args.output}")


if __name__ == "__main__":
    main()
