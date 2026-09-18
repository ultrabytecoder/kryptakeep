#!/usr/bin/env bash
# Build every KryptaKeep artifact that can be built on THIS host (Linux x86_64),
# copy them into the website's assets/dists/, and deploy the website.
#
# Builds:
#   - Android release APKs (mainnet + testnet), keystore-signed via signing/sign.sh
#   - Desktop portable uber .jar
#   - Desktop .deb   (needs dpkg-deb; present on most Debian/Ubuntu)
#   - Desktop .rpm   (needs rpmbuild; run `sudo apt-get install -y rpm` to enable)
#
# NOT built here (require other host OSes) -- build in CI instead:
#   - Windows .msi   (windows-latest)
#   - macOS   .dmg   (macos-latest)
#
# Usage:
#   ./build-and-deploy.sh               # build + sign (prompts keystore pw) + collect + deploy
#   ./build-and-deploy.sh --no-deploy   # everything except the final deploy
#   ./build-and-deploy.sh --skip-sign   # reuse existing signed APKs in signing/output/
#   ./build-and-deploy.sh --no-build    # skip Gradle; collect existing outputs + deploy
set -euo pipefail

cd "$(dirname "$0")"
PROJECT_DIR="$(pwd)"
WEBSITE_DIR="$(cd "$PROJECT_DIR/.." && pwd)/kryptakeep-website"
DIST_DIR="$WEBSITE_DIR/assets/dists"
JAR_SUBDIR="composeApp/build/compose/jars"
BIN_SUBDIR="composeApp/build/compose/binaries/main"

NO_DEPLOY=0
SKIP_SIGN=0
NO_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --no-deploy) NO_DEPLOY=1 ;;
    --skip-sign) SKIP_SIGN=1 ;;
    --no-build)  NO_BUILD=1 ;;
    -h|--help)   grep '^# ' "$0" | sed 's/^# //'; exit 0 ;;
    *) echo "Unknown arg: $arg" >&2; exit 2 ;;
  esac
done

log(){ printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn(){ printf '\033[1;33mWARN: %s\033[0m\n' "$*"; }
die(){ printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# ---------------- Preflight ----------------
log "Preflight checks"
command -v java   >/dev/null 2>&1 || die "java not found on PATH"
command -v zip    >/dev/null 2>&1 || die "zip not found (needed to strip jar signatures)"
command -v unzip  >/dev/null 2>&1 || die "unzip not found"
[[ -x ./gradlew ]]        || die "./gradlew not found or not executable"
[[ -f local.properties ]] || die "local.properties missing (needs sdk.dir + etherscan keys)"
grep -q 'etherscan.testnet.api.key' local.properties || warn "no etherscan.testnet.api.key in local.properties"
grep -q 'etherscan.mainnet.api.key' local.properties || warn "no etherscan.mainnet.api.key in local.properties"

AVAIL_GB=$(( $(df -Pk . | awk 'NR==2{print $4}') / 1024 / 1024 ))
(( AVAIL_GB < 2 )) && die "only ${AVAIL_GB}GB free; need ~2GB for builds"
echo "free disk: ${AVAIL_GB}GB"

BUILD_DEB=0; command -v dpkg-deb >/dev/null 2>&1 && BUILD_DEB=1
BUILD_RPM=0; command -v rpmbuild >/dev/null 2>&1 && BUILD_RPM=1
(( BUILD_DEB )) || warn "dpkg-deb missing -> .deb will be skipped (sudo apt-get install -y dpkg-dev fakeroot)"
(( BUILD_RPM )) || warn "rpmbuild missing -> .rpm will be skipped (sudo apt-get install -y rpm)"

# ---------------- Build ----------------
if (( NO_BUILD )); then
  log "Skipping Gradle build (--no-build)"
else
  GRADLE_TASKS=( :composeApp:assembleRelease :composeApp:packageUberJarForCurrentOS )
  (( BUILD_DEB )) && GRADLE_TASKS+=( :composeApp:packageDeb )
  (( BUILD_RPM )) && GRADLE_TASKS+=( :composeApp:packageRpm )
  log "Gradle build: ${GRADLE_TASKS[*]}"
  ./gradlew "${GRADLE_TASKS[@]}"
fi

# ---------------- Sign Android APKs ----------------
if (( SKIP_SIGN )); then
  log "Skipping signing (--skip-sign); reusing signing/output/"
else
  MAINNET_APK="composeApp/build/outputs/apk/productionMainnet/release/composeApp-productionMainnet-release-unsigned.apk"
  TESTNET_APK="composeApp/build/outputs/apk/productionTestnet/release/composeApp-productionTestnet-release-unsigned.apk"
  [[ -f "$MAINNET_APK" ]] || die "mainnet unsigned APK not found: $MAINNET_APK"
  [[ -f "$TESTNET_APK" ]] || die "testnet unsigned APK not found: $TESTNET_APK"
  log "Signing Android release APKs (signing/sign.sh will prompt for the keystore password)"
  ./signing/sign.sh "$MAINNET_APK" "$TESTNET_APK"
fi
[[ -f signing/output/kryptakeep.apk ]]         || die "signed mainnet APK missing: signing/output/kryptakeep.apk"
[[ -f signing/output/kryptakeep-testnet.apk ]] || die "signed testnet APK missing: signing/output/kryptakeep-testnet.apk"

# ---------------- Collect into website assets/dists ----------------
log "Collecting artifacts into $DIST_DIR"
mkdir -p "$DIST_DIR"

cp -f signing/output/kryptakeep.apk         "$DIST_DIR/kryptakeep.apk"
cp -f signing/output/kryptakeep-testnet.apk "$DIST_DIR/kryptakeep-testnet.apk"
echo "  + kryptakeep.apk, kryptakeep-testnet.apk"

JAR="$(find "$JAR_SUBDIR" -maxdepth 1 -name 'KryptaKeep-*.jar' 2>/dev/null | head -n1)"
[[ -n "$JAR" ]] || die "desktop jar not found under $JAR_SUBDIR"
cp -f "$JAR" "$DIST_DIR/kryptakeep-linux-x64.jar"
echo "  + kryptakeep-linux-x64.jar (from $(basename "$JAR"))"

DEB="$(find "$BIN_SUBDIR" -name 'KryptaKeep-*.deb' 2>/dev/null | head -n1)"
if [[ -n "$DEB" ]]; then
  cp -f "$DEB" "$DIST_DIR/kryptakeep-x86_64.deb"
  echo "  + kryptakeep-x86_64.deb (from $(basename "$DEB"))"
else
  (( BUILD_DEB )) && warn ".deb not produced (expected under $BIN_SUBDIR/deb)"
fi

RPM="$(find "$BIN_SUBDIR" -name 'KryptaKeep-*.rpm' 2>/dev/null | head -n1)"
if [[ -n "$RPM" ]]; then
  cp -f "$RPM" "$DIST_DIR/kryptakeep-x86_64.rpm"
  echo "  + kryptakeep-x86_64.rpm (from $(basename "$RPM"))"
else
  (( BUILD_RPM )) && warn ".rpm not produced (expected under $BIN_SUBDIR/rpm)"
fi

# One-time Windows .msi: if a CI-downloaded copy was staged next to the project, include it.
if [[ -f "$PROJECT_DIR/dist-staging/kryptakeep-x86_64.msi" ]]; then
  cp -f "$PROJECT_DIR/dist-staging/kryptakeep-x86_64.msi" "$DIST_DIR/kryptakeep-x86_64.msi"
  echo "  + kryptakeep-x86_64.msi (staged from CI)"
else
  warn "no staged kryptakeep-x86_64.msi (Windows build runs in CI; drop it in dist-staging/ to include)"
fi

log "Staged artifacts in $DIST_DIR:"
ls -la "$DIST_DIR"

# ---------------- Deploy ----------------
if (( NO_DEPLOY )); then
  log "Skipping deploy (--no-deploy)"
else
  [[ -x "$WEBSITE_DIR/deploy.sh" ]] || die "deploy.sh not found/executable at $WEBSITE_DIR/deploy.sh"
  log "Deploying website"
  "$WEBSITE_DIR/deploy.sh"
fi

log "Done."
