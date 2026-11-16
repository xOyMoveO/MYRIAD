#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$project_root"
role="${1:-pilot}"
case "$role" in
    pilot) run_dir=run-fabric-client; run_task=runClient ;;
    observer) run_dir=run-fabric-observer; run_task=runObserver ;;
    *) echo 'Usage: launch-client.sh [pilot|observer] [path-to-mcpfabric-0.10.0+26.3.jar]' >&2; exit 1 ;;
esac
bridge_jar="${2:-$project_root/$run_dir/mods/mcpfabric-0.10.0+26.3.jar}"
if [[ ! -f "$bridge_jar" ]]; then
    echo "Missing verified Fabric 26.3 test mod: $bridge_jar; see tools/mcpfabric/installation.json" >&2
    exit 1
fi
python tools/mcpfabric/prepare-assets.py
bash tools/mcpfabric/build-fabric-adapter.sh "$bridge_jar"
python tools/mcpfabric/prepare-fabric-client.py "$role" "$bridge_jar"
exec bash ./gradlew --offline -x downloadAssets "$run_task"
