"""Identity and measured evidence for the calibration replays."""

from hashlib import sha256
import json
from xml.etree import ElementTree
from zipfile import ZipFile

from combo_calibration import load

def reference(path):
    return {"file": path.name, "sha256": sha256(path.read_bytes()).hexdigest()}


def paired_release(directory):
    manifest_path = directory / "release.json"
    manifest = load(manifest_path)
    jar_path = directory / manifest["file"]
    info = manifest["balance_snapshot"]
    snapshot_path = directory / info["file"]
    snapshot = load(snapshot_path)
    for path, expected in ((jar_path, manifest), (snapshot_path, info)):
        if path.stat().st_size != expected["bytes"] or reference(path)["sha256"] != expected["sha256"]:
            raise ValueError(f"Release checksum or size mismatch: {path}")
    if not (
        snapshot["sourceSha"] == manifest["source_sha"]
        and snapshot["sourceDirty"] == info["source_dirty"]
        and snapshot["formatVersion"] == info["format_version"]
        and snapshot["rulesSha256"] == info["rules_sha256"]
    ):
        raise ValueError(f"Snapshot source and rules disagree with release manifest: {directory}")
    with ZipFile(jar_path) as archive:
        if archive.read("META-INF/myriad-balance-snapshot.sha256").decode().strip() != info["sha256"]:
            raise ValueError(f"Packaged jar references a different snapshot: {jar_path}")
    return snapshot, {"sourceSha": snapshot["sourceSha"], "sourceDirty": snapshot["sourceDirty"],
                      "rulesSha256": snapshot["rulesSha256"], "snapshot": reference(snapshot_path),
                      "jar": reference(jar_path), "manifest": reference(manifest_path)}


def engine_run(label, log, xml, marker="BALANCE_CALIBRATION "):
    contents = log.read_text(encoding="utf-8")
    tests = ElementTree.parse(xml).findall(".//testcase")
    failed = [test.attrib["name"] for test in tests
              if any(test.find(tag) is not None for tag in ("failure", "error", "skipped"))]
    matching = [test.attrib["name"] for test in tests
                if f"Will run tests matching {test.attrib['name']} (1 tests)" in contents]
    success = len(tests) == 1 and bool(matching) and not failed and all(marker in contents for marker in
              ("1 GAME TESTS COMPLETE", "All 1 required tests passed", "BUILD SUCCESSFUL"))
    decoder = json.JSONDecoder()
    rows = [decoder.raw_decode(line.split(marker, 1)[1])[0]
            for line in contents.splitlines() if marker and marker in line]
    return {"label": label, "status": "passed" if success else "failed_or_incomplete",
            "basis": "Fabric GameTest synthetic fixture", "stdout": reference(log), "xml": reference(xml),
            "testCases": [test.attrib["name"] for test in tests], "failedOrSkipped": failed, "events": rows}
