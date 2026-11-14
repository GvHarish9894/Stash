#!/usr/bin/env bash
# Build, install and run the Maestro suite on one device configuration.
#   scripts/e2e.sh <target> [extra maestro args, e.g. --include-tags smoke]
# Targets: android-phone | android-foldable | android-tablet | android-desktop | android-resize
#          ios-iphone | ios-ipad
# Env: ANDROID_SERIAL (default: the first running emulator), IOS_DEVICE (simulator name or UDID),
#      ORIENTATION=PORTRAIT|LANDSCAPE_LEFT (default PORTRAIT), SKIP_BUILD=1 to reuse the last build.
# Results: build/e2e/<target>/ (junit report, screenshots, device.txt).
set -euo pipefail
cd "$(dirname "$0")/.."

target="${1:?target required, see the header of this script}"
shift
out="build/e2e/$target"
rm -rf "$out" && mkdir -p "$out"

run() { # run <device> <flow path> [maestro args]
  local device="$1" flows="$2"
  shift 2
  maestro --device "$device" test -e ORIENTATION="${ORIENTATION:-PORTRAIT}" --format junit --output "$out/report.xml" \
    --test-output-dir "$out/artifacts" "$@" "$flows"
}

case "$target" in
  android-*)
    # Emulators only: never install on or drive a phone that happens to be plugged in.
    serial="${ANDROID_SERIAL:-$(adb devices | awk '/^emulator-.*device$/ {print $1; exit}')}"
    [[ "$serial" == emulator-* ]] || { echo "No running emulator. Start the Resizable_Experimental AVD." >&2; exit 2; }
    resize() { adb -s "$serial" emu resize-display "$1" >/dev/null; sleep 5; } # the console gives no completion signal
    [[ -n "${SKIP_BUILD:-}" ]] || ./gradlew :androidApp:assembleDebug
    adb -s "$serial" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk >/dev/null
    {
      echo "avd: $(adb -s "$serial" emu avd name | head -1)"
      echo "android: $(adb -s "$serial" shell getprop ro.build.version.release) (API $(adb -s "$serial" shell getprop ro.build.version.sdk))"
    } > "$out/device.txt"
    case "$target" in
      android-phone) mode=0 ;;
      android-foldable) mode=1 ;;
      android-tablet) mode=2 ;;
      android-desktop) mode=3 ;;
      android-resize) mode=0 ;;
      *) echo "Unknown target $target" >&2; exit 2 ;;
    esac
    trap 'resize 0' EXIT # leave the emulator in phone mode
    resize "$mode"
    echo "orientation: ${ORIENTATION:-PORTRAIT}" >> "$out/device.txt"
    echo "display: $(adb -s "$serial" shell wm size | tail -1), $(adb -s "$serial" shell wm density | tail -1)" >> "$out/device.txt"
    if [[ "$target" == android-resize ]]; then
      # State must survive each display-mode change (activity recreation).
      for next in 2 1 0; do
        run "$serial" .maestro/android/resize-before.yaml "$@"
        resize "$next"
        run "$serial" .maestro/android/resize-after.yaml "$@"
      done
    else
      run "$serial" .maestro --exclude-tags ios "$@"
    fi
    ;;
  ios-*)
    export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
    case "$target" in
      ios-iphone) device="${IOS_DEVICE:-iPhone 17 Pro}" ;;
      ios-ipad) device="${IOS_DEVICE:-iPad Pro 11-inch (M5)}" ;;
      *) echo "Unknown target $target" >&2; exit 2 ;;
    esac
    xcrun simctl boot "$device" 2>/dev/null || true # already booted is fine
    xcrun simctl bootstatus "$device" >/dev/null
    udid="$(xcrun simctl list devices booted | grep -F "$device" | grep -oE '[0-9A-F-]{36}' | head -1)"
    [[ -n "$udid" ]] || { echo "Simulator '$device' is not booted." >&2; exit 2; }
    [[ -n "${SKIP_BUILD:-}" ]] || xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
      -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' -derivedDataPath build/ios ARCHS=arm64 -quiet build
    xcrun simctl install "$udid" build/ios/Build/Products/Debug-iphonesimulator/Stash.app
    xcrun simctl list devices booted | grep -B40 -F "$udid" | grep -E '^--|'"$udid" | tail -2 > "$out/device.txt"
    echo "orientation: ${ORIENTATION:-PORTRAIT}" >> "$out/device.txt"
    run "$udid" .maestro --exclude-tags android "$@"
    ;;
  *) echo "Unknown target $target" >&2; exit 2 ;;
esac
