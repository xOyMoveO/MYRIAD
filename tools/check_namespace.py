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
                         "data/myriad/role_rework/shuo.json"):
            if required not in names:
                errors.append("Missing JAR entry: " + required)
        for name in names:
            if old in name.lower():
                errors.append("Old JAR path: " + name)
            if name.endswith((".class", ".json", ".toml", ".cfg", ".mcmeta")):
                if old.encode() in jar.read(name).lower():
                    errors.append("Old JAR identifier: " + name)
        metadata = jar.read("META-INF/neoforge.mods.toml").decode()
        if 'modId="myriad"' not in metadata:
            errors.append("Wrong JAR modId")
        if any("data/myriad/test_instance/" + n + ".json" in names
               for n in ("first_group", "combat_regression")):
            errors.append("Development-only test instance in release JAR")
if errors:
    raise SystemExit("\n".join(errors))
print("PASS: MYRIAD namespace, packages, JSON resources" + (" and release JAR" if args.jar else ""))
