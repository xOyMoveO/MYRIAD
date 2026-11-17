"""Audit the MYRIAD source tree and optional built JAR without changing files."""
import argparse
import json
import pathlib
import re
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--jar", type=pathlib.Path)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
old = "xian" + "fanjie"
errors = []
files = list((root / "src").rglob("*"))
for path in files:
    if not path.is_file():
        continue
    rel = path.relative_to(root).as_posix()
    if old in rel.lower():
        errors.append("Old namespace path: " + rel)
    if path.suffix in {".java", ".json", ".bbmodel", ".cfg", ".toml", ".mcmeta"}:
        text = path.read_text(encoding="utf-8-sig")
        if old in text.lower() or re.search(r"\b" + "x" + r"fj(?:\b|_)", text, re.I):
            errors.append("Old runtime identifier: " + rel)
        if path.suffix in {".json", ".bbmodel", ".mcmeta"}:
            json.loads(text)
        if path.suffix == ".java":
            package = re.search(r"^package\s+([\w.]+);", text, re.M)
            if not package or not package[1].startswith("com.myriad"):
                errors.append("Unexpected package: " + rel)
properties = (root / "gradle.properties").read_text()
for expected in ("mod_id=myriad", "mod_group_id=com.myriad"):
    if expected not in properties:
        errors.append("Missing property: " + expected)
if args.jar:
    with zipfile.ZipFile(args.jar) as jar:
        names = jar.namelist()
        for required in ("com/myriad/Myriad.class", "com/myriad/MyriadClient.class",
                         "myriad.mixins.json", "assets/myriad/lang/zh_cn.json",
                         "data/myriad/role_rework/shuo.json", "data/myriad/role_rework/rivet.json"):
            if required not in names:
                errors.append("Missing JAR entry: " + required)
        for name in names:
            if old in name.lower():
                errors.append("Old JAR path: " + name)
            if name.endswith((".class", ".json", ".toml", ".cfg", ".mcmeta")):
                if old.encode() in jar.read(name).lower():
                    errors.append("Old JAR identifier: " + name)
        metadata = json.loads(jar.read("fabric.mod.json"))
        if metadata.get("id") != "myriad" or metadata.get("entrypoints") != {
                "main": ["com.myriad.Myriad"], "client": ["com.myriad.MyriadClient"]}:
            errors.append("Wrong Fabric identity or entrypoints")
        if metadata.get("depends", {}).get("minecraft") != "=26.3":
            errors.append("Wrong Fabric Minecraft target")
        if "META-INF/neoforge.mods.toml" in names or "META-INF/accesstransformer.cfg" in names:
            errors.append("Retired loader metadata in Fabric release JAR")
        if jar.testzip() is not None:
            errors.append("Invalid ZIP entry in release JAR")
        if any(name.startswith(("com/myriad/acceptance/", "com/myriad/gametest/"))
               or (name.startswith("com/myriad/game/") and ("Checks" in name or "Regression" in name or "MyriadGameTests" in name))
               or "/test_instance/" in name or name == "myriad-gametest.mixins.json"
               for name in names):
            errors.append("Test adapter, fixture or regression code in release JAR")
        if any("data/myriad/test_instance/" + n + ".json" in names
               for n in ("team_signal", "control_shield", "death_recap", "match_contribution", "match_experience", "first_group", "combat_regression", "keheng", "yike", "rivet",
                         "roster_delivery", "world_time", "hero_action_time")):
            errors.append("Development-only test instance in release JAR")
if errors:
    raise SystemExit("\n".join(errors))
print("PASS: MYRIAD namespace, packages, JSON resources" + (" and release JAR" if args.jar else ""))
