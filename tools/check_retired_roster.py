"""Read-only audit of retired character references in source and a release JAR.

This complements the real selection/input/combat GameTests. A clean name scan
alone does not establish that the retained characters behave correctly.
"""

import argparse
import collections
import json
import pathlib
import re
import subprocess
import zipfile


RETIRED_IDS = (
    "lige", "guanlan", "wuxiang", "feiyu", "wanyue", "xuanjia",
    "luoheng", "shenli", "zhechuan", "mingzhu",
)
RETIRED_NAMES = ("离歌", "观澜", "无相", "绯羽", "绾月", "玄甲", "洛衡", "沈砺", "折川", "明烛")
RETIRED = re.compile("|".join(RETIRED_IDS + RETIRED_NAMES), re.IGNORECASE)
# These are a shared shop item and a retained monster species, not character
# implementations or character weapons. Only the complete names are exempt.
SHARED_NAMES = re.compile("wuxiangjianxia|无相剑匣|玄甲地龟", re.IGNORECASE)
TEXT_SUFFIXES = {
    ".java", ".json", ".toml", ".cfg", ".mcmeta", ".txt", ".py", ".ps1",
    ".sh", ".bat", ".gradle", ".properties", ".csv", ".md", ".svg", ".bbmodel",
}
SOURCE_ROOTS = {"src", "tools", "scripts", "art", "art_source"}
# The assertions exercise rejection of every retired ID; they provide no skills.
ALLOWED_SOURCE_CONTENT = {
    "tools/check_retired_roster.py": "audit vocabulary",
    "src/main/java/com/myriad/game/RosterRetirementChecks.java": "retired-ID rejection assertions",
}
ALLOWED_JAR_CONTENT = {
    "com/myriad/game/RosterRetirementChecks.class": "retired-ID rejection assertions",
}


def retired_tokens(text):
    return {match.group().lower() for match in RETIRED.finditer(SHARED_NAMES.sub("", text))}


def asset_references(name, data):
    """Resolve owned model, texture, particle and sound-file references."""
    edges = set()

    def add(value, kind):
        if not isinstance(value, str) or not value.startswith("myriad:"):
            return
        value = value.split(":", 1)[1]
        directory, suffix = {"texture": ("textures", ".png"),
                             "model": ("models", ".json"), "sound": ("sounds", ".ogg")}[kind]
        edges.add((name, f"assets/myriad/{directory}/{value}{suffix}"))

    def walk(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key in {"parent", "model"}:
                    add(item, "model")
                elif key == "textures":
                    if isinstance(item, dict):
                        for texture in item.values():
                            add(texture, "texture")
                    elif isinstance(item, list):
                        for texture in item:
                            if isinstance(texture, str) and texture.startswith("myriad:"):
                                add("myriad:particle/" + texture.split(":", 1)[1], "texture")
                walk(item)
        elif isinstance(value, list):
            for item in value:
                walk(item)

    walk(data)
    if name == "assets/myriad/sounds.json":
        for event in data.values():
            for sound in event.get("sounds", []):
                if isinstance(sound, str):
                    add(sound, "sound")
                elif sound.get("type", "file") == "file":
                    add(sound.get("name"), "sound")
    return edges


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1])
    parser.add_argument("--jar", type=pathlib.Path)
    args = parser.parse_args()
    root = args.root.resolve()
    failures = []
    allowed = collections.Counter()
    source_count = 0
    source_resources = {}
    source_edges = set()

    def check(name, text, allowance=None):
        hits = retired_tokens(text)
        if not hits:
            return
        if allowance:
            allowed[allowance] += 1
        else:
            failures.append(f"{name}: {', '.join(sorted(hits))}")

    tracked = subprocess.run(
        ["git", "-C", str(root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        check=True, capture_output=True,
    ).stdout.decode().split("\0")
    for name in tracked:
        if not name or pathlib.PurePosixPath(name).parts[0] not in SOURCE_ROOTS:
            continue
        path = root / name
        if not path.is_file():
            continue
        source_count += 1
        license_record = name.startswith("src/main/resources/META-INF/licenses/")
        allowance = "preserved license records" if license_record else None
        check("source path " + name, name, allowance)
        if path.suffix.lower() in TEXT_SUFFIXES:
            check("source content " + name, path.read_text(encoding="utf-8-sig"),
                  allowance or ALLOWED_SOURCE_CONTENT.get(name))
        for resource_prefix in ("src/main/resources/", "src/generated/resources/"):
            if name.startswith(resource_prefix):
                resource = name[len(resource_prefix):]
                source_resources[resource] = path
                if resource.startswith("assets/myriad/") and resource.endswith(".json"):
                    source_edges.update(asset_references(resource, json.loads(path.read_text(encoding="utf-8-sig"))))
    for origin, target in sorted(source_edges):
        if target not in source_resources:
            failures.append(f"source resource reference {origin} -> missing {target}")

    jar_count = 0
    jar_edges = set()
    if args.jar:
        with zipfile.ZipFile(args.jar) as jar:
            jar_names = set(jar.namelist())
            for entry in jar.infolist():
                if entry.is_dir():
                    continue
                jar_count += 1
                name = entry.filename
                license_record = name.startswith("META-INF/licenses/")
                allowance = "preserved license records" if license_record else None
                check("JAR path " + name, name, allowance)
                if name.endswith(".class") or pathlib.PurePosixPath(name).suffix.lower() in TEXT_SUFFIXES:
                    # ASCII IDs remain literal in class constant pools; JSON text
                    # and localized labels are checked as UTF-8 as well.
                    check("JAR content " + name, jar.read(entry).decode("utf-8", errors="ignore"),
                          allowance or ALLOWED_JAR_CONTENT.get(name))
                if name.startswith("assets/myriad/") and name.endswith(".json"):
                    jar_edges.update(asset_references(name, json.loads(jar.read(entry).decode("utf-8-sig"))))
            for origin, target in sorted(jar_edges):
                if target not in jar_names:
                    failures.append(f"JAR resource reference {origin} -> missing {target}")

    if failures:
        raise SystemExit("FAIL: retired-character references remain\n" + "\n".join(failures))
    print(f"PASS: retired-character source/tool/asset scan ({source_count} files)"
          + (f" and release JAR scan ({jar_count} entries)" if args.jar else ""))
    print(f"PASS: owned model/texture/particle/sound references ({len(source_edges)} source edges"
          + (f", {len(jar_edges)} JAR edges)" if args.jar else ")"))
    for reason, count in sorted(allowed.items()):
        print(f"Allowed records: {reason}: {count}")
    print("Shared shop equipment wuxiangjianxia / 无相剑匣 and monster 玄甲地龟 are preserved.")
    print("Pair this audit with the retired-ID entry tests and retained-character GameTests.")


if __name__ == "__main__":
    main()
