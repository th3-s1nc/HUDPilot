#!/bin/bash
# Baut updater/limits/ (Klassen-Ordner) aus dem App-Code (PbfImport, CameraImport, ...) + LimitsCli.kt plus der Kotlin-Laufzeit.
# Braucht nur kotlinc (hier beim Bauen, nicht auf dem Server). Auf dem Server läuft: java -cp limits io.github.th3s1nc.osmandhudbridge.limit.LimitsCliKt ...
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
L="$HERE/../../hudpilot/app/src/main/java/io/github/th3s1nc/osmandhudbridge/limit"
KOTLINC="${KOTLINC:-/home/claude/tools/kotlinc/bin/kotlinc}"
STDLIB="${STDLIB:-$(dirname "$KOTLINC")/../lib/kotlin-stdlib.jar}"
export JAVA_TOOL_OPTIONS=
OUT="$HERE/../updater/limits"
rm -rf "$OUT" "$HERE/../updater/limits.jar"; mkdir -p "$OUT"
"$KOTLINC" "$L/PbfImport.kt" "$L/ServerLimits.kt" "$L/CameraImport.kt" "$L/SpeedLimitLogic.kt" "$L/SpeedLimitMatcher.kt" "$L/PointWarn.kt" "$L/Tx.kt" "$HERE/LimitsCli.kt" \
  -d "$OUT" 2>&1 | grep -v "^warning" || true
python3 - "$STDLIB" "$OUT" <<'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
for n in z.namelist():
    if n.endswith('/') or n.startswith('META-INF/'): continue
    z.extract(n, sys.argv[2])
PY
echo "limits: $(find "$OUT" -type f | wc -l) Dateien, $(du -sh "$OUT" | cut -f1)"
