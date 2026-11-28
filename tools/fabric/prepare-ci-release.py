"""Audit and stage the paired Fabric release JAR and gameplay snapshot."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile


root = Path(__file__).resolve().parents[2]
properties = dict(
    line.split("=", 1)
    for line in (root / "gradle.properties").read_text().splitlines()
    if "=" in line and not line.lstrip().startswith("#")
)
filename = f"{properties['mod_id']}-{properties['mod_version']}-no-arms.jar"
jar = root / "build/libs" / filename
binaries = sorted(p.name for p in jar.parent.glob("*.jar") if not p.name.endswith("-sources.jar"))
if binaries != [filename]:
    raise SystemExit(f"Expected exactly one release JAR {filename}, found {binaries}")

snapshot = root / "build/balance/balance-snapshot.json"
if not snapshot.is_file():
    raise SystemExit("Missing balance snapshot. Run ./gradlew build before staging the release.")
snapshot_contents = snapshot.read_bytes()
snapshot_digest = hashlib.sha256(snapshot_contents).hexdigest()
snapshot_metadata = json.loads(snapshot_contents)
revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
if snapshot_metadata["sourceSha"] != revision:
    raise SystemExit("Snapshot source differs from the current source revision. Rebuild both release files.")

subprocess.run([sys.executable, str(root / "tools/check_namespace.py"), "--jar", str(jar)], check=True)
with zipfile.ZipFile(jar) as archive:
    embedded_digest = archive.read("META-INF/myriad-balance-snapshot.sha256").decode("ascii").strip()
    if embedded_digest != snapshot_digest:
        raise SystemExit("Snapshot SHA-256 differs from the digest embedded in this JAR. Rebuild both release files.")
    metadata = json.loads(archive.read("fabric.mod.json"))
    if metadata["version"] != properties["mod_version"]:
        raise SystemExit("Unresolved or incorrect release version")
    if metadata.get("license") != properties["mod_license"]:
        raise SystemExit("Unresolved or incorrect release license scope")
    license_sources = {
        "LICENSE": root / "LICENSE",
        "META-INF/LICENSE-SCOPE.txt": root / "src/main/resources/META-INF/LICENSE-SCOPE.txt",
    }
    for notice in (root / "src/main/resources/META-INF/licenses").glob("*"):
        if notice.is_file():
            license_sources["META-INF/licenses/" + notice.name] = notice
    for name, source in license_sources.items():
        if archive.read(name) != source.read_bytes():
            raise SystemExit(f"Release license differs from source: {name}")
    for key, value in {
        "minecraft": "=" + properties["minecraft_version"],
        "fabricloader": "=" + properties["loader_version"],
        "fabric-api": "=" + properties["fabric_version"],
        "java": ">=25",
    }.items():
        if metadata["depends"].get(key) != value:
            raise SystemExit(f"Incorrect Fabric dependency {key}: expected {value}")
    for name in archive.namelist():
        if name.startswith(("tools/", "config/", "world/", "run-", ".git/")) or Path(name).name.startswith(".env") or name.endswith((".pem", ".key", ".p12")):
            raise SystemExit(f"Development data or credentials in release JAR: {name}")

def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


digest = sha256_file(jar)
output = root / "build/ci-release"
if output.exists():
    shutil.rmtree(output)
output.mkdir(parents=True, exist_ok=True)
shutil.copyfile(jar, output / filename)
(output / snapshot.name).write_bytes(snapshot_contents)
(output / (filename + ".sha256")).write_text(f"{digest}  {filename}\n")
(output / "release.json").write_text(json.dumps({
    "source_sha": revision,
    "file": filename,
    "bytes": jar.stat().st_size,
    "sha256": digest,
    "balance_snapshot": {
        "file": snapshot.name,
        "bytes": len(snapshot_contents),
        "sha256": snapshot_digest,
        "format_version": snapshot_metadata["formatVersion"],
        "rules_sha256": snapshot_metadata["rulesSha256"],
        "source_dirty": snapshot_metadata["sourceDirty"],
    },
    "loader": "fabric",
    "minecraft": properties["minecraft_version"],
    "fabric_loader": properties["loader_version"],
    "fabric_api": properties["fabric_version"],
}, indent=2) + "\n")
manifest = json.loads((output / "release.json").read_text())
for artifact in (manifest, manifest["balance_snapshot"]):
    staged = output / artifact["file"]
    if staged.stat().st_size != artifact["bytes"] or sha256_file(staged) != artifact["sha256"]:
        raise SystemExit(f"Staged release file failed manifest readback: {staged.name}")
print(f"PASS: Fabric release {filename}, source {revision}, SHA256 {digest}, snapshot SHA256 {snapshot_digest}")
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(f"Fabric release: `{filename}` ({jar.stat().st_size} bytes)\n\nSource: `{revision}`\n\nSHA256: `{digest}`\n\nSnapshot SHA256: `{snapshot_digest}`\n\n")
