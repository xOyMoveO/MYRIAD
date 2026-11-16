#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$project_root"
mod_jar='run-mcpfabric/mods/mcpfabric-neoforge-0.10.0+26.2.jar'
if [[ ! -f "$mod_jar" ]]; then
    echo "Missing project test mod: $mod_jar" >&2
    exit 1
fi
python tools/mcpfabric/prepare-assets.py
# Override the loader for this invocation; keep the normal project version unchanged.
exec bash ./gradlew --gradle-user-home "$project_root/.gradle-user-home" -Pneo_version=26.2.0.88 -PmyriadMcpClient=true -x downloadAssets runMcpClient "$@"
