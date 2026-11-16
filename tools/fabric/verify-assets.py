"""Verify the shared Minecraft 26.3 resources; never download or erase resources."""
import argparse
import hashlib
import json
import os
from pathlib import Path

INDEX_SHA1 = "e99f9973047785b5a9d52eaf72020b97545053c9"


def verify(store):
    index = store / "indexes/34.json"
    raw = index.read_bytes()
    if hashlib.sha1(raw).hexdigest() != INDEX_SHA1:
        raise RuntimeError(f"Index 34 checksum mismatch: {index}")
    objects = json.loads(raw)["objects"]
    invalid = []
    for name, entry in objects.items():
        path = store / "objects" / entry["hash"][:2] / entry["hash"]
        if not path.is_file() or path.stat().st_size != entry["size"]:
            invalid.append((name, entry["hash"], entry["size"]))
        else:
            with path.open("rb") as stream:
                if hashlib.file_digest(stream, "sha1").hexdigest() != entry["hash"]:
                    invalid.append((name, entry["hash"], entry["size"]))
    if invalid:
        for name, digest, size in invalid:
            print(f"INVALID\t{digest}\t{size}\t{name}")
        raise RuntimeError(f"{len(invalid)} missing or damaged objects. Restore these hashes only.")
    print(f"PASS: index 34 and {len(objects)} resource entries verified at {store}")
    return raw


def prepare(store, raw, gradle_home):
    alias = store / "indexes/26.3-34.json"
    if alias.exists():
        if alias.read_bytes() != raw:
            raise RuntimeError(f"Loom index alias differs: {alias}")
    else:
        alias.write_bytes(raw)
    link = gradle_home / "caches/fabric-loom/assets"
    if link.exists() or link.is_symlink():
        if link.resolve() != store.resolve():
            raise RuntimeError(f"Loom assets already use another directory: {link}. Reconcile existing resources first.")
    else:
        link.parent.mkdir(parents=True, exist_ok=True)
        link.symlink_to(store.resolve(), target_is_directory=True)
    print(f"Loom reuses {link} -> {store.resolve()}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--store", type=Path, default=Path.home() / ".local/share/minecraft/assets")
    parser.add_argument("--prepare-loom", action="store_true")
    parser.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))))
    args = parser.parse_args()
    try:
        content = verify(args.store)
        if args.prepare_loom:
            prepare(args.store, content, args.gradle_home)
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        raise SystemExit(str(error))
