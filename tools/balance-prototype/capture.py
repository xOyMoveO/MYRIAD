"""Export the miniature snapshot and capture the two real Fabric runs for this prototype."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "build/balance-benchmark"
JAVA = Path("src/main/java/com/myriad")
DOME = JAVA / "character/skills/keheng/BeyondOneSecondSkill.java"
BASELINE = "bae9d06c765d7c8e74a5c1200533ae8d26443912"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n")


def source_rules(root):
    """Inlined rules which have no registry descriptor yet. Accept exactly one live declaration."""
    evidence = {}

    def read(file, pattern, *types, key):
        path = JAVA / file
        source = (root / path).read_text()
        hits = list(re.finditer(pattern, source))
        if len(hits) != 1:
            raise ValueError(f"Unresolved prototype field {key} in {path}")
        m = hits[0]
        evidence[key] = f"{path}:{source[:m.start()].count(chr(10)) + 1}"
        values = [typ(value) for typ, value in zip(types, m.groups(), strict=True)]
        return values[0] if len(values) == 1 else values

    lane = "game/LaneSystem.java"
    timed = read("game/GameManager.java", r"elapsed <= 0 \|\| elapsed % (\d+) != 0", int, key="salaryTicks")
    wage = read(
        "game/GameManager.java",
        r"awards.put\(player, GlobalBalance.goldIncome\(MatchBalance.farmReward\(player, (\d+), false",
        int,
        key="salaryBase",
    )
    wave = read(lane, r"WAVE_INTERVAL = (\d+)L \* 20L", int, key="waveSeconds")
    wave_size = [
        read(lane, symbol + r" = (\d+);", int, key=symbol)
        for symbol in ("MELEE_PER_WAVE", "RANGED_PER_WAVE")
    ]
    magic_period = read(lane, r"waveNumber % (\d+) == 0 && i == 1", int, key="magicWave")
    magic = read(
        lane,
        r"case MAGIC -> (\d+);\s*case SIEGE -> \d+;\s*default -> state.ranged\(\) \? (\d+) : (\d+);",
        int,
        int,
        int,
        key="laneGold",
    )
    xp = read(
        lane,
        r"int baseXp = state.kind == Kind.SIEGE \? \d+ : state.ranged\(\) \? (\d+) : (\d+);",
        int,
        int,
        key="laneXp",
    )
    scaling = read(
        lane,
        r"long tenths = (\d+)L \+ Math.min\((\d+)L, elapsed / \((\d+)L \* 60L \* 20L\)\)",
        int,
        int,
        int,
        key="rewardSteps",
    )
    share = read(
        lane, r"sharePercent = playerKill \? 100L : experience \? (\d+)L : (\d+)L", int, int, key="share"
    )
    tower = read(
        lane,
        r"float maxHealth = \(float\)\(switch \(key.tier\) \{\s*case 1 -> (\d+)\.0F;",
        int,
        key="outerTowerHp",
    )
    no_wave = read(lane, r"amount \*= (0\.\d+)F;", float, key="noWave")
    dome = read(
        "character/skills/keheng/BeyondOneSecondSkill.java",
        r"RADIUS,(\d+),(\d+),flight-([.\d]+),flight",
        int,
        int,
        float,
        key="dome",
    )
    flight = read(
        "character/skills/keheng/BeyondOneSecondSkill.java",
        r"double flight=([.\d]+)-([.\d]+)\*\(c.rank-1\)",
        float,
        float,
        key="domeFlight",
    )
    pulse = read(
        "character/skills/keheng/KehengTimeFields.java", r"\(now-f.begins\)%(\d+)==0", int, key="domePulse"
    )
    back = read(
        "character/skills/yingxiao/rework/NightBackstabSkill.java",
        r"1\+\((\d+)\+(\d+)\*\(c.rank-1\)\)/100.0",
        int,
        int,
        key="backstab",
    )
    return dict(
        salaryTicks=timed,
        salaryBase=wage,
        waveSeconds=wave,
        waveSize=wave_size,
        magicWave=magic_period,
        laneGold=magic,
        laneXp=xp,
        steps=scaling,
        sharedXp=share[0],
        outerTowerHp=tower,
        noWave=no_wave,
        dome=dome,
        domeFlight=flight,
        domePulse=pulse,
        backstab=back,
        sources=evidence,
    )


def run_fixture(root, folder, offline, test, payload, marker):
    folder.mkdir(parents=True, exist_ok=True)
    cmd = [
        "bash",
        "./gradlew",
        *(["--offline"] if offline else []),
        "build",
        "runGameTestServer",
        "-PmyriadGameTests",
        f"-PgameTestFilter=myriad:{test}",
    ]
    raw = root / "build/balance" / payload
    raw.unlink(missing_ok=True)
    log = folder / "fabric.log"
    with log.open("w") as stream:
        proc = subprocess.run(cmd, cwd=root, stdout=stream, stderr=subprocess.STDOUT)
    output = log.read_text()
    marks = [
        f"Will run tests matching myriad:{test} (1 tests)",
        marker,
        "1 GAME TESTS COMPLETE",
        "All 1 required tests passed",
        "BUILD SUCCESSFUL",
    ]
    if proc.returncode or not all(s in output for s in marks):
        raise RuntimeError(f"Fabric observation incomplete. Inspect {log}")
    xml = root / "build/reports/fabric-gametest.xml"
    if not xml.is_file() or any(e.tag in ("failure", "error") for e in ET.parse(xml).iter()):
        raise RuntimeError(f"Fabric XML missing or contains failures. Inspect {log}")
    shutil.copy2(xml, folder / "fabric.xml")
    return json.loads(raw.read_text())


def capture(root, name, offline):
    folder = OUT / name
    game = run_fixture(
        root, folder, offline, "balance_benchmark", "engine.json", "[MYRIAD-BALANCE-BENCHMARK] PASS"
    )
    (jar,) = [p for p in (root / "build/libs").glob("*.jar") if not p.name.endswith("-sources.jar")]
    shutil.copy2(jar, folder / jar.name)
    file = DOME.with_suffix(".class").relative_to("src/main/java")
    with ZipFile(jar) as archive:
        if archive.read(str(file)) != (root / "build/classes/java/main" / file).read_bytes():
            raise RuntimeError("The exercised field class and packaged class differ")
    rules = game.pop("rules")
    rules["inline"] = source_rules(root)

    def git(*args):
        return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()

    head = git("rev-parse", "HEAD")
    game_base = (
        BASELINE
        if git("rev-parse", head + ":src/main") == git("rev-parse", BASELINE + ":src/main")
        else head
    )
    dirty = git("status", "--porcelain", "--", "src/main").splitlines()
    snapshot = dict(
        format=1,
        gameBase=game_base,
        localGameChanges=dirty,
        variant=name,
        profile="CHERRY_V1",
        rules=rules,
        rulesSha256=digest(encode(rules)),
    )
    save(folder / "balance-snapshot.json", snapshot)
    pairs = dict(
        jar=jar.name,
        jarSha256=digest(jar.read_bytes()),
        snapshotSha256=digest((folder / "balance-snapshot.json").read_bytes()),
        rulesSha256=snapshot["rulesSha256"],
        fieldSourceSha256=digest((root / DOME).read_bytes()),
        fieldClassSha256=digest((root / "build/classes/java/main" / file).read_bytes()),
        build="passed",
        serverFixture="1/1",
        xml="read; no failure/error",
        evidence=game,
    )
    save(folder / "pair.json", pairs)
    return snapshot, pairs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--offline", action="store_true", help="Use Gradle's existing dependency cache")
    args = parser.parse_args()
    base, base_pair = capture(ROOT, "current", args.offline)
    tower = run_fixture(
        ROOT,
        OUT / "tower",
        args.offline,
        "balance_tower_probe",
        "tower.json",
        "[MYRIAD-BALANCE-TOWER] PASS",
    )
    with tempfile.TemporaryDirectory(prefix="myriad-field-") as tmp:
        alt = Path(tmp)
        for name in ("gradle", "src"):
            shutil.copytree(ROOT / name, alt / name)
        for name in ("build.gradle", "gradlew", "gradle.properties", "settings.gradle", "LICENSE"):
            shutil.copy2(ROOT / name, alt / name)
        path = alt / DOME
        original = path.read_text()
        needle = "RADIUS,13,60,flight-.05"
        if original.count(needle) != 1:
            raise ValueError("Locate the field duration again before running the manual-value experiment")
        path.write_text(original.replace(needle, "RADIUS,13,100,flight-.05"))
        manual, manual_pair = capture(alt, "manual-100t", args.offline)
    with ZipFile(OUT / "current" / base_pair["jar"]) as archive:
        zh = json.loads(archive.read("assets/myriad/lang/zh_cn.json"))
    labels = {}
    for hero in base["rules"]["roles"].values():
        for skill in hero["skills"]:
            labels[skill["id"]] = zh.get("skill.myriad." + skill["id"] + ".name", skill["name"])
            for effect in skill["damage"]:
                key = effect["labelKey"]
                if key and key in zh:
                    labels[key] = zh[key]
    evidence = dict(
        current=base_pair,
        manual=manual_pair,
        manualInline=manual["rules"]["inline"],
        tower=tower,
        labels=labels,
    )
    save(HERE / "samples" / "balance-snapshot.json", base)
    save(HERE / "samples" / "engine-evidence.json", evidence)
    print(f"Two paired snapshots, JARs, logs and XML in {OUT}")


if __name__ == "__main__":
    main()
