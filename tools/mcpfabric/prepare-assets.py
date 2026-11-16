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

    link = root / ".gradle-user-home/caches/neoformruntime/assets"
    link.parent.mkdir(parents=True, exist_ok=True)
    if link.is_symlink():
        if link.resolve() != store.resolve():
            temporary = link.with_name("assets.persistent-link")
            temporary.symlink_to(store, target_is_directory=True)
            os.replace(temporary, link)
    elif link.exists():
        raise RuntimeError(f"Separate resource directory found: {link}. Reconcile it before launching.")
    else:
        link.symlink_to(store, target_is_directory=True)

    # This is the properties-file contract consumed by ModDevGradle's prepare*Run tasks.
    # Recreate it after build/ cleanup only after every indexed object has passed verification.
    properties = root / "build/moddev/minecraft_assets.properties"
    properties.parent.mkdir(parents=True, exist_ok=True)
    properties.write_text(f"asset_index={index_id}\nassets_root={store}\n")
    print(f"Verified {len(objects)} shared Minecraft resources; resource download is skipped: {store}")


if __name__ == "__main__":
    try:
        prepare()
    except (OSError, ValueError, KeyError, RuntimeError) as error:
        raise SystemExit(str(error))
