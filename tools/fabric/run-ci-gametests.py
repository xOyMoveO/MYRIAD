"""Run core Fabric GameTests serially and retain each JVM's actual evidence."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET


CORE_TESTS = (
    "melee_arc", "first_group", "shuo_pilot", "roster_a", "roster_b",
    "keheng", "yike", "rivet", "roster_delivery", "world_time", "hero_action_time",
    "arena_fixture", "arena_tower_5v5", "arena_tower_3v3",
)
EXTRA_TESTS = ("balance_calibration_damage", "calibration_keheng", "calibration_qingluo",
               "match_economy", "match_assists", "balance_haste", "balance_vertical",
               "balance_shuo_chixiao", "balance_roster_lane", "balance_roster_camps", "balance_roster_towers",
               *(f"balance_roster_{roster}_{scene}" for roster in (112, 113) for scene in ("lane", "camps", "towers")))
EXTRA_TESTS += ("balance_roster_sword_frost", "balance_mage_support_113")
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--offline", action="store_true", help="Use only already cached Gradle dependencies")
parser.add_argument("filters", nargs="*", help="Explicit check filters; default is all core checks")
args = parser.parse_args()
unknown = set(args.filters) - set(CORE_TESTS + EXTRA_TESTS)
if unknown:
    parser.error("Unknown filters: " + ", ".join(sorted(unknown)))
root = Path(__file__).resolve().parents[2]
output = root / "build/reports/fabric-ci"
output.mkdir(parents=True, exist_ok=True)
report = root / "build/reports/fabric-gametest.xml"
results = []

for name in args.filters or CORE_TESTS:
    target = output / name
    target.mkdir(parents=True, exist_ok=True)
    report.unlink(missing_ok=True)  # A stale XML must not stand in for this process.
    (target / "report.xml").unlink(missing_ok=True)
    command = ["bash", "./gradlew", "runGameTestServer", "-PmyriadGameTests", f"-PgameTestFilter=myriad:{name}"]
    if args.offline:
        command.append("--offline")
    print(f"\nRunning isolated myriad:{name}", flush=True)
    with (target / "stdout.txt").open("w") as log:
        process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        for line in process.stdout:
            print(line, end="", flush=True)
            log.write(line)
        code = process.wait()
    text = (target / "stdout.txt").read_text()
    problems = []
    if code:
        problems.append(f"Gradle exit {code}")
    for expected in (f"Will run tests matching myriad:{name} (1 tests)", "1 GAME TESTS COMPLETE", "All 1 required tests passed"):
        if expected not in text:
            problems.append("Missing actual execution evidence: " + expected)
    cases = 0
    if report.exists():
        shutil.copyfile(report, target / "report.xml")
        try:
            xml = ET.parse(report).getroot()
            cases = len(list(xml.iter("testcase")))
            if list(xml.iter("failure")) or list(xml.iter("error")):
                problems.append("XML reports test failure/error")
        except ET.ParseError as error:
            problems.append(f"Invalid XML: {error}")
    else:
        problems.append("GameTest XML report is missing")
    result = {"filter": "myriad:" + name, "exit_code": code, "passed": not problems,
              "xml_testcases": cases, "problems": problems}
    results.append(result)
    (output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps(result), flush=True)

passed = sum(result["passed"] for result in results)
print(f"Fabric CI GameTests: {passed}/{len(results)} passed; isolated logs/XML: {output}")
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(f"Fabric isolated GameTests: **{passed}/{len(results)}**\n\n| Filter | Result | XML cases |\n| --- | --- | --- |\n")
        for result in results:
            summary.write(f"| `{result['filter']}` | {'passed' if result['passed'] else 'failed'} | {result['xml_testcases']} |\n")
        summary.write("\nEmpty Fabric XML suites are recorded; success also requires this process to match, complete and pass exactly one required test. These server fixtures do not cover real dual clients, original maps or human visual/subjective acceptance.\n")
raise SystemExit(0 if passed == len(results) else 1)
