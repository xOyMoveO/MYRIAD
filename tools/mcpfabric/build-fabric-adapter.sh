#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root"
bridge_jar="${1:?Usage: build-fabric-adapter.sh /path/to/mcpfabric-0.10.0+26.3.jar}"
bridge_jar="$(cd "$(dirname "$bridge_jar")" && pwd)/$(basename "$bridge_jar")"
python - "$bridge_jar" <<'PY'
import hashlib,sys,zipfile
from pathlib import Path
p=Path(sys.argv[1])
assert p.stat().st_size == 317405, 'Wrong MCPFabric artifact size'
assert hashlib.sha512(p.read_bytes()).hexdigest() == '4db052e21fda03eae05aef973a53a87db3fdf44d0291a5639f2635f82d71f2136aa3d35987bd6fe9ffb9e8538cea53d7b30be18f8c6b8ffa8357b14834dbccd2', 'Wrong MCPFabric SHA512'
with zipfile.ZipFile(p) as archive: assert archive.testzip() is None
PY
mkdir -p build/mcpfabric-adapter/classes
cat > build/mcpfabric-adapter/classpath.init.gradle <<'GRADLE'
allprojects {
    afterEvaluate {
        tasks.register('printMyriadAcceptanceClasspath') {
            dependsOn tasks.named('classes')
            doLast { println sourceSets.main.compileClasspath.asPath + File.pathSeparator + sourceSets.main.output.asPath }
        }
    }
}
GRADLE
bash ./gradlew --offline --console=plain -q -I build/mcpfabric-adapter/classpath.init.gradle printMyriadAcceptanceClasspath > build/mcpfabric-adapter/classpath.txt
adapter_cp="$(tail -n 1 build/mcpfabric-adapter/classpath.txt)"
find tools/mcpfabric/client-adapter/java -name '*.java' > build/mcpfabric-adapter/sources.txt
javac -encoding UTF-8 -proc:none --release 25 -cp "$adapter_cp:$bridge_jar" -d build/mcpfabric-adapter/classes @build/mcpfabric-adapter/sources.txt
cp -R tools/mcpfabric/client-adapter/resources/. build/mcpfabric-adapter/classes/
jar --create --file build/mcpfabric-adapter/myriad-acceptance-fabric26.3.jar -C build/mcpfabric-adapter/classes .
python - <<'PY'
import hashlib
from pathlib import Path
p=Path('build/mcpfabric-adapter/myriad-acceptance-fabric26.3.jar')
print(p.resolve(), hashlib.sha256(p.read_bytes()).hexdigest())
PY
