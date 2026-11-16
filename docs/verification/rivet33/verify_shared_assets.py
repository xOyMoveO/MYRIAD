"""Verify the audited Minecraft 26.2 / asset-index 32 cache before skipping downloadAssets.

This verification-only helper never downloads files or changes the shared cache.
It writes Gradle's generated asset-path properties only after every check passes.
"""
from pathlib import Path
import argparse
import hashlib
import json

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--assets", type=Path, default=Path.home()/".local/share/minecraft/assets")
args = parser.parse_args()
assets = args.assets.expanduser().resolve()
index = assets/"indexes/32.json"
raw = index.read_bytes()
assert len(raw) == 586566, "Unexpected index size; use the current version manifest"
assert hashlib.sha1(raw).hexdigest() == "941b1ea6afb4a472d164c0d12e3e3272bf3435f8", "26.2 index SHA1 mismatch"
objects = json.loads(raw)["objects"]
for name, entry in objects.items():
    file = assets/"objects"/entry["hash"][:2]/entry["hash"]
    assert file.is_file(), f"Missing shared object: {name} ({entry['hash']})"
    assert file.stat().st_size == entry["size"], f"Wrong shared object size: {name}"
    assert hashlib.sha1(file.read_bytes()).hexdigest() == entry["hash"], f"Shared object SHA1 mismatch: {name}"
project = Path(__file__).resolve().parents[3]
properties = project/"build/moddev/minecraft_assets.properties"
properties.parent.mkdir(parents=True, exist_ok=True)
properties.write_text("asset_index=32\nassets_root="+assets.as_posix()+"\n")
print(f"PASS: index 32 / {len(objects)} shared objects; prepared build/moddev/minecraft_assets.properties")
