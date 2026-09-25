#!/usr/bin/env bash
# Build + run AgriCareAi on an Android emulator (or a connected device).
#
#   ./run.sh                 build, boot the emulator, install, launch
#   ./run.sh --device        use an already-connected device/emulator
#   ./run.sh --serial <id>   target a specific device (see: adb devices)
#   ./run.sh --phone         target the first non-emulator device
#   ./run.sh --avd <name>    boot a specific AVD
#   ./run.sh --release       build the release variant instead of debug
#   ./run.sh --clean         gradle clean first
set -euo pipefail

cd "$(dirname "$0")"

APP_ID="com.mvx.agriculture"
LAUNCH_ACTIVITY=".HomeActivity"
VARIANT="debug"
AVD=""
USE_CONNECTED=0
CLEAN=0
SERIAL=""
PHONE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)  USE_CONNECTED=1; shift ;;
    --serial)  SERIAL="$2"; USE_CONNECTED=1; shift 2 ;;
    --phone)   PHONE=1; USE_CONNECTED=1; shift ;;
    --avd)     AVD="$2"; shift 2 ;;
    --release) VARIANT="release"; shift ;;
    --clean)   CLEAN=1; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

die() { echo "error: $*" >&2; exit 1; }

# ---------------------------------------------------------------- Android SDK
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
[[ -d "$SDK" ]] || die "Android SDK not found at $SDK. Install it via Android Studio, or set ANDROID_HOME."
export ANDROID_HOME="$SDK"
export PATH="$SDK/platform-tools:$SDK/emulator:$SDK/cmdline-tools/latest/bin:$PATH"
command -v adb >/dev/null || die "adb not on PATH (expected $SDK/platform-tools/adb)"

# ------------------------------------------------------------------ Java 17-21
# AGP 8.3 / Gradle 8.4 need JDK 17-21. A newer JDK (22+) will fail the build,
# so prefer Android Studio's bundled JBR when the current JAVA_HOME is too new.
pick_java() {
  local candidates=(
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "$HOME/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
    "$HOME/.sdkman/candidates/java/21.0.11-tem"
  )
  local c
  for c in "${candidates[@]}"; do
    [[ -x "$c/bin/java" ]] && { echo "$c"; return; }
  done
  if command -v /usr/libexec/java_home >/dev/null; then
    /usr/libexec/java_home -v 21 2>/dev/null && return
    /usr/libexec/java_home -v 17 2>/dev/null && return
  fi
}

java_major() { "$1/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/'; }

cur_major=""
[[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME:-}/bin/java" ]] && cur_major="$(java_major "$JAVA_HOME")"
if [[ -z "$cur_major" ]] || [[ "$cur_major" -gt 21 ]] || [[ "$cur_major" -lt 17 ]]; then
  picked="$(pick_java || true)"
  [[ -n "$picked" ]] || die "need a JDK 17-21 for AGP 8.3 (current JAVA_HOME=${JAVA_HOME:-unset}). Install Android Studio or Temurin 21."
  export JAVA_HOME="$picked"
fi
echo "==> JAVA_HOME=$JAVA_HOME ($( "$JAVA_HOME/bin/java" -version 2>&1 | head -1 ))"

# ------------------------------------------------------------ local.properties
if [[ ! -f local.properties ]]; then
  echo "sdk.dir=$SDK" > local.properties
  echo "==> wrote local.properties"
fi

# -------------------------------------------------------------------- emulator
boot_wait() {
  echo "==> waiting for device to finish booting..."
  adb wait-for-device
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 3
  done
  adb shell input keyevent 82 >/dev/null 2>&1 || true   # dismiss lockscreen
  echo "==> device ready"
}

list_devices() { adb devices | awk '$2=="device" {print $1}'; }

if (( PHONE )); then
  SERIAL="$(list_devices | grep -v '^emulator-' | head -1 || true)"
  [[ -n "$SERIAL" ]] || die "no physical device connected. Plug it in, enable USB debugging, and accept the RSA prompt."
fi

if (( USE_CONNECTED )); then
  [[ -n "$(list_devices)" ]] || die "no device connected (drop --device to boot an emulator)"
elif [[ -n "$(list_devices)" ]]; then
  echo "==> reusing already-running device(s)"
else
  if [[ -z "$AVD" ]]; then
    AVD="$(emulator -list-avds | head -1)"
    [[ -n "$AVD" ]] || die "no AVD found. Create one in Android Studio (Device Manager), e.g. a Pixel 6 on API 34."
  fi
  echo "==> booting AVD: $AVD"
  nohup emulator -avd "$AVD" -no-snapshot-load >/tmp/agricare-emulator.log 2>&1 &
  boot_wait
fi

# With more than one device attached, adb needs an explicit target.
if [[ -z "$SERIAL" ]]; then
  count="$(list_devices | wc -l | tr -d ' ')"
  if [[ "$count" -gt 1 ]]; then
    echo "Multiple devices attached:"
    list_devices | sed 's/^/  /'
    die "pick one with --serial <id>, or --phone for the physical device"
  fi
  SERIAL="$(list_devices | head -1)"
fi
ADB=(adb -s "$SERIAL")
echo "==> target: $SERIAL"

# ----------------------------------------------------------------------- build
if [[ "$VARIANT" == "release" ]]; then GRADLE_TASK="assembleRelease"; else GRADLE_TASK="assembleDebug"; fi
chmod +x ./gradlew
(( CLEAN )) && ./gradlew clean
echo "==> ./gradlew $GRADLE_TASK"
./gradlew "$GRADLE_TASK"

APK="app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
[[ -f "$APK" ]] || die "APK not found at $APK"

# --------------------------------------------------------------- install + run
echo "==> installing $APK"
"${ADB[@]}" install -r "$APK"
echo "==> launching $APP_ID/$LAUNCH_ACTIVITY"
"${ADB[@]}" shell am start -n "$APP_ID/$LAUNCH_ACTIVITY"

echo
echo "Done. Useful follow-ups:"
echo "  adb -s $SERIAL logcat --pid=\$(adb -s $SERIAL shell pidof -s $APP_ID)"
echo "  adb -s $SERIAL exec-out screencap -p > screen.png"
echo "  adb -s $SERIAL uninstall $APP_ID"
