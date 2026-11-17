"""Install only the two test mods into one isolated Fabric run directory."""
import hashlib
import json
import shutil
import sys
import uuid
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
role, source = sys.argv[1:]
if role not in ("pilot", "observer"):
    raise SystemExit("Role must be pilot or observer")
bridge = Path(source).resolve()
installation = json.loads((root / "tools/mcpfabric/installation.json").read_text())
if bridge.stat().st_size != installation["mod_size"] or hashlib.sha512(bridge.read_bytes()).hexdigest() != installation["mod_sha512"]:
    raise SystemExit("MCPFabric artifact does not match the verified Fabric 26.3 release")
with zipfile.ZipFile(bridge) as archive:
    if archive.testzip() is not None:
        raise SystemExit("MCPFabric ZIP is corrupt")
run = root / ("run-fabric-client" if role == "pilot" else "run-fabric-observer")
(run / "mods").mkdir(parents=True, exist_ok=True)
(run / "config").mkdir(exist_ok=True)
for source in (bridge, root / "build/mcpfabric-adapter/myriad-acceptance-fabric26.3.jar"):
    target = run / "mods" / source.name
    if source.resolve() != target.resolve():
        shutil.copy2(source, target)
config = run / "config/mcpfabric.config.json"
if not config.exists():
    config.write_text(json.dumps({
        "host": "127.0.0.1", "port": 25599 if role == "pilot" else 25600,
        "requireAuth": True, "token": uuid.uuid4().hex, "readToken": uuid.uuid4().hex,
        "worldIdKey": uuid.uuid4().hex, "writeDiscoveryFile": False, "auditLog": True,
        "enableWorldWrite": False, "enableCommands": True, "enablePlayerControl": True,
        "enableVision": True, "enableUiControl": True, "enableSupervisedControl": False,
        "requireApproval": [],
    }, indent=2))
    config.chmod(0o600)
print(f"Prepared isolated {role}: {run}")
