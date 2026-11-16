"""Verify the persistent resource store before launching without the download task."""

import hashlib
import json
import os
from pathlib import Path


def prepare():
    root = Path(__file__).resolve().parents[2]
    installation = json.loads((root / "tools/mcpfabric/installation.json").read_text())
    store = Path.home() / ".local/share/minecraft/assets"
    index_id = installation["asset_index"]
    index_file = store / "indexes" / f"{index_id}.json"
    if not index_file.is_file():
        raise RuntimeError(f"Shared resources not found: {index_file}. Locate existing resources before downloading.")
    if hashlib.sha1(index_file.read_bytes()).hexdigest() != installation["asset_index_sha1"]:
        raise RuntimeError(f"Resource index does not match this client: {index_file}. Download was not started.")

    objects = json.loads(index_file.read_text())["objects"]
    invalid = []
    for name, entry in objects.items():
        resource = store / "objects" / entry["hash"][:2] / entry["hash"]
        if not resource.is_file() or resource.stat().st_size != entry["size"]:
            invalid.append(name)
            continue
        with resource.open("rb") as stream:
            if hashlib.file_digest(stream, "sha1").hexdigest() != entry["hash"]:
                invalid.append(name)
    if invalid:
        raise RuntimeError(f"{len(invalid)} resources missing or corrupt: {invalid[:8]}. Restore only these files.")

    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    link = gradle_home / "caches/fabric-loom/assets"
    link.parent.mkdir(parents=True, exist_ok=True)
    if link.is_symlink():
        if link.resolve() != store.resolve():
            raise RuntimeError(f"Loom assets points to a different store: {link}. Reconcile it before launching.")
    elif link.exists():
        raise RuntimeError(f"Separate resource directory found: {link}. Reconcile it before launching.")
    else:
        link.symlink_to(store, target_is_directory=True)

    # Loom uses a version-prefixed ID; both names refer to the same verified index.
    alias = store / "indexes" / f"{installation['loom_asset_index']}.json"
    if alias.exists():
        if hashlib.sha1(alias.read_bytes()).hexdigest() != installation["asset_index_sha1"]:
            raise RuntimeError(f"Loom resource index alias differs: {alias}")
    else:
        alias.write_bytes(index_file.read_bytes())
    print(f"Verified {len(objects)} shared Minecraft resources; resource download is skipped: {store}")


if __name__ == "__main__":
    try:
        prepare()
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        raise SystemExit(str(error))
