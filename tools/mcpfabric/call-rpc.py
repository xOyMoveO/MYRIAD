"""Call the selected isolated MCPFabric bridge without printing its bearer token."""
import argparse
import json
import os
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("method")
parser.add_argument("params", nargs="?", default="{}")
parser.add_argument("--run-dir", default=os.environ.get("MYRIAD_MCP_RUN_DIR", "run-fabric-client"))
parser.add_argument("--record", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
run = (root / args.run_dir).resolve()
if run not in {(root / name).resolve() for name in ("run-fabric-client", "run-fabric-observer")}:
    raise SystemExit("Select run-fabric-client or run-fabric-observer only")
config = json.loads((run / "config/mcpfabric.config.json").read_text())
if config["host"] != "127.0.0.1" or config["requireAuth"] is not True:
    raise SystemExit("The test bridge must require authentication on 127.0.0.1")
params = json.loads(args.params)
request = urllib.request.Request(
    f"http://127.0.0.1:{config['port']}/rpc",
    data=json.dumps({"method": args.method, "params": params}).encode(),
    headers={"Authorization": "Bearer " + config["token"], "Content-Type": "application/json"},
)
with urllib.request.urlopen(request, timeout=20) as response:
    result = json.load(response)
if args.record:
    args.record.parent.mkdir(parents=True, exist_ok=True)
    with args.record.open("a") as stream:
        stream.write(json.dumps({"time": datetime.now(timezone.utc).isoformat(), "run": run.name,
                                 "method": args.method, "params": params, "response": result}) + "\n")
print(json.dumps(result))
if not result.get("ok"):
    raise SystemExit(1)
