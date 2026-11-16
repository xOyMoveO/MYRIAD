"""Audit and stage the single Fabric release JAR for GitHub Actions artifacts."""
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
filename = f"{properties['mod_id']}-{properties['mod_version']}.jar"
jar = root / "build/libs" / filename
binaries = sorted(p.name for p in jar.parent.glob("*.jar") if not p.name.endswith("-sources.jar"))
if binaries != [filename]:
    raise SystemExit(f"Expected exactly one release JAR {filename}, found {binaries}")

subprocess.run([sys.executable, str(root / "tools/check_namespace.py"), "--jar", str(jar)], check=True)
with zipfile.ZipFile(jar) as archive:
    metadata = json.loads(archive.read("fabric.mod.json"))
    if metadata["version"] != properties["mod_version"]:
        raise SystemExit("Unresolved or incorrect release version")
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

digest = hashlib.sha256(jar.read_bytes()).hexdigest()
revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
output = root / "build/ci-release"
if output.exists():
    shutil.rmtree(output)
output.mkdir(parents=True, exist_ok=True)
shutil.copyfile(jar, output / filename)
(output / (filename + ".sha256")).write_text(f"{digest}  {filename}\n")
(output / "release.json").write_text(json.dumps({
    "source_sha": revision,
    "file": filename,
    "bytes": jar.stat().st_size,
    "sha256": digest,
    "loader": "fabric",
    "minecraft": properties["minecraft_version"],
    "fabric_loader": properties["loader_version"],
    "fabric_api": properties["fabric_version"],
}, indent=2) + "\n")
print(f"PASS: Fabric release {filename}, source {revision}, SHA256 {digest}")
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(f"Fabric release: `{filename}` ({jar.stat().st_size} bytes)\n\nSource: `{revision}`\n\nSHA256: `{digest}`\n\n")
