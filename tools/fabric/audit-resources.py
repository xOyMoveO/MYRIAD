"""Audit packaged MYRIAD resource references and decode every shipped audio file.

This checks files and references, not visual quality, audio quality or gameplay feel.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", type=Path, help="Write the actual audit result as JSON")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
assets = root / "src/main/resources/assets/myriad"
errors = []


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            errors.append("Duplicate JSON key: " + key)
        result[key] = value
    return result


def read(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def require(reference, folder, suffix, source):
    if not reference.startswith("myriad:"):
        return  # Vanilla resources are resolved by the target client's resource manager.
    target = assets / folder / (reference.split(":", 1)[1] + suffix)
    if not target.is_file():
        errors.append(f"Missing {folder} reference {reference} in {source}")


models = list((assets / "models").rglob("*.json"))
for path in models:
    model = read(path)
    if "parent" in model:
        require(model["parent"], "models", ".json", path.name)
    textures = model.get("textures", {})
    for name, value in textures.items():
        seen = set()
        while value.startswith("#"):
            slot = value[1:]
            if slot in seen or slot not in textures:
                errors.append(f"Unresolved local texture alias {name} in {path.name}")
                break
            seen.add(slot)
            value = textures[slot]
        if not value.startswith("#"):
            require(value, "textures", ".png", path.name)

particles = list((assets / "particles").glob("*.json"))
for path in particles:
    for texture in read(path).get("textures", []):
        require(texture, "textures/particle", ".png", path.name)


def model_references(value, source):
    if isinstance(value, dict):
        if isinstance(value.get("model"), str):
            require(value["model"], "models", ".json", source)
        for child in value.values():
            model_references(child, source)
    elif isinstance(value, list):
        for child in value:
            model_references(child, source)


items = list((assets / "items").glob("*.json"))
for path in items:
    model_references(read(path), path.name)

languages = {path.stem: read(path) for path in (assets / "lang").glob("*.json")}
primary = languages["zh_cn"]
missing_keys = {name: sorted(primary.keys() - values.keys()) for name, values in languages.items()}
for name, missing in missing_keys.items():
    if missing:
        errors.append(f"{name} is missing {len(missing)} primary language keys")
placeholder = re.compile(r"%(?:(\d+)\$)?(?:\d+)?(?:\.\d+)?([sdf])|%%")


def signature(value):
    indices = []
    implicit = 0
    for match in placeholder.finditer(value):
        if match[0] == "%%":
            continue
        implicit += 1
        indices.append((int(match[1] or implicit), match[2]))
    return sorted(indices)


for name, values in languages.items():
    for key in primary.keys() & values.keys():
        if signature(primary[key]) != signature(values[key]):
            errors.append(f"Placeholder mismatch: {name}:{key}")
literal_keys = set()
for path in (root / "src/main/java").rglob("*.java"):
    literal_keys.update(re.findall(r'(?:translatable|I18n\.get)\("([^"\n]*\.myriad[^"\n]*)"\s*[,)]', path.read_text()))
for key in sorted(literal_keys - primary.keys()):
    errors.append("Missing literal primary language key: " + key)

sounds = read(assets / "sounds.json")
references = set()
for event, definition in sounds.items():
    subtitle = definition.get("subtitle")
    if subtitle and subtitle not in primary:
        errors.append("Missing sound subtitle: " + subtitle)
    for entry in definition["sounds"]:
        name = entry if isinstance(entry, str) else entry["name"]
        if isinstance(entry, dict) and entry.get("type") == "event":
            if name.startswith("myriad:") and name.split(":", 1)[1] not in sounds:
                errors.append("Missing sound event: " + name)
        else:
            require(name, "sounds", ".ogg", event)
            if name.startswith("myriad:"):
                references.add(name.split(":", 1)[1])

decoder = shutil.which("ffmpeg")
audio = list((assets / "sounds").rglob("*.ogg"))
decoded = 0
if decoder is None:
    errors.append("ffmpeg is required to check actual audio decoding")
else:
    for path in audio:
        result = subprocess.run([decoder, "-v", "error", "-nostdin", "-i", str(path), "-f", "null", "-"], capture_output=True, text=True)
        if result.returncode or result.stderr:
            errors.append(f"Audio decode failed {path.name}: {result.stderr.strip()}")
        else:
            decoded += 1

baseline = "8da79ddc329492155cea44f3166c97f0e37d86fc"
baseline_audio = set(subprocess.check_output(
    ["git", "ls-tree", "-r", "--name-only", baseline, "--", (assets / "sounds").relative_to(root)],
    cwd=root, text=True).splitlines())
current_audio = {p.relative_to(root).as_posix() for p in audio}
unchanged_audio = current_audio == {p for p in baseline_audio if p.endswith(".ogg")} and all(subprocess.check_output(["git", "show", f"{baseline}:{p.relative_to(root)}"], cwd=root) == p.read_bytes() for p in audio)
result = {
    "source_sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
    "models": len(models), "item_definitions": len(items), "particle_definitions": len(particles),
    "sound_events": len(sounds), "referenced_audio": len(references), "audio_files": len(audio),
    "decoded_audio": decoded, "audio_bytes_unchanged_from_fixed_baseline": unchanged_audio,
    "language_keys": {name: len(values) for name, values in languages.items()},
    "literal_language_keys_checked": len(literal_keys), "missing_language_keys": missing_keys,
    "placeholder_fields": {name: sum(bool(signature(value)) for value in values.values()) for name, values in languages.items()},
    "license_files": len(list((root / "src/main/resources/META-INF/licenses").glob("*"))),
    "errors": errors, "passed": not errors,
    "limits": "Static own-namespace references and full audio decode; runtime event receipt is recorded separately. No visual or listening conclusion.",
}
if args.output:
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(result, ensure_ascii=False, indent=2))
raise SystemExit(0 if result["passed"] else 1)
