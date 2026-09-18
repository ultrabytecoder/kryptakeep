#!/usr/bin/env bash
# Build the desktop (JVM) uber jar for KryptaKeep.
#
# Unlike run-desktop.sh (which skips the build when a jar already exists and can
# therefore run a stale jar), this ALWAYS invokes the packaging task. Gradle is
# input-aware, so the produced jar reflects the current sources — no stale builds.
#
# Usage:
#   ./build-desktop.sh          # build (fast up-to-date check; repackages on change)
#   ./build-desktop.sh --clean  # also delete the previous jar(s) before building
set -euo pipefail

cd "$(dirname "$0")"
JAR_DIR="composeApp/build/compose/jars"

CLEAN=0
if [[ "${1:-}" == "--clean" || "${1:-}" == "-c" ]]; then
    CLEAN=1
fi

if [[ "$CLEAN" -eq 1 ]]; then
    echo "Removing previous desktop jar(s)..."
    rm -f "$JAR_DIR"/KryptaKeep-*.jar
fi

echo "Building desktop uber jar..."
./gradlew :composeApp:packageUberJarForCurrentOS

JAR="$(find "$JAR_DIR" -maxdepth 1 -name 'KryptaKeep-*.jar' 2>/dev/null | head -n1)"
if [[ -z "$JAR" ]]; then
    echo "ERROR: build did not produce a jar in $JAR_DIR" >&2
    exit 1
fi

# Strip stale signature files from signed dependencies (e.g. BouncyCastle)
# that would otherwise cause SecurityException at runtime. Same step as
# run-desktop.sh so the freshly built jar is immediately runnable.
if unzip -l "$JAR" 2>/dev/null | grep -qE 'META-INF/.*\.(SF|DSA|RSA)$'; then
    echo "Stripping stale dependency signatures..."
    zip -d "$JAR" "META-INF/*.SF" "META-INF/*.DSA" "META-INF/*.RSA" >/dev/null 2>&1 || true
fi

echo ""
echo "Built: $JAR ($(du -h "$JAR" | cut -f1))"
echo "Run it with: ./run-desktop.sh"
