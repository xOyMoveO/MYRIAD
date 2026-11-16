#!/usr/bin/env python
"""Create a NEW synthetic server fixture; never read or modify an existing world."""
import argparse
import hashlib
import json
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[2] / "src/gametest/resources/arena-fixture"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-dir", type=Path, required=True)
    parser.add_argument("--port", type=int, default=25643)
    args = parser.parse_args()
    target = args.server_dir.resolve()
    if not 1024 <= args.port <= 65535:
        parser.error("port must be between 1024 and 65535")
    if target.exists() and any(target.iterdir()):
        parser.error("server-dir must be new or empty; existing worlds are never overwritten")
    target.mkdir(parents=True, exist_ok=True)
    world = target / "world"
    files = []
    for source in sorted(SOURCE.rglob("*.json")):
        relative = source.relative_to(SOURCE)
        destination = world / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        content = source.read_bytes()
        destination.write_bytes(content)
        files.append(dict(path=str(relative), bytes=len(content), sha256=hashlib.sha256(content).hexdigest()))
    settings = dict(layers=[dict(block="minecraft:bedrock", height=1),
                           dict(block="minecraft:stone", height=126),
                           dict(block="minecraft:grass_block", height=1)], biome="minecraft:plains")
    properties = {"server-ip": "127.0.0.1", "server-port": args.port, "online-mode": "false",
                  "enforce-secure-profile": "false", "white-list": "false", "max-players": 10,
                  "level-name": "world", "level-type": "minecraft:flat",
                  "generator-settings": json.dumps(settings, separators=(",", ":")),
                  "generate-structures": "false", "spawn-protection": 0, "difficulty": "peaceful",
                  "gamemode": "creative", "view-distance": 6, "simulation-distance": 4,
                  "motd": "MYRIAD synthetic arena fixture (not an original map)"}
    (target / "server.properties").write_text("\n".join(f"{k}={v}" for k, v in properties.items()) + "\n")
    points = []
    for mode, directory in [("5v5", SOURCE), ("3v3", SOURCE / "myriad_arenas/3v3")]:
        for name in ["myriad_lane_layout.json", "myriad_monster_spawns.json", "myriad_brush_zones.json",
                     "myriad_vein_spawns.json", "myriad_minimap.json"]:
            points.append(dict(mode=mode, file=name, data=json.loads((directory / name).read_text())))
    manifest = dict(kind="synthetic-only", baseline="8da79ddc329492155cea44f3166c97f0e37d86fc",
                    dimension="minecraft:overworld", surfaceY=64, port=args.port, files=files, configurations=points)
    (target / "arena-fixture-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"Created synthetic fixture at {target}; surface y=64; port={args.port}")
    print("Install the current Fabric server and MYRIAD JAR in this new directory; reuse cached dependencies.")
    print("Handle Minecraft EULA explicitly before starting. No EULA acceptance is written by this tool.")
    print("Console: op <tester>; myriad mod on; myriad arena 3v3 (or 5v5); myriad start")
    print("See docs/fabric/arena-fixture.md for complete two-mode, lifecycle and human-map steps.")


if __name__ == "__main__":
    main()
