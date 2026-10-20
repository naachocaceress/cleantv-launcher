#!/usr/bin/env bash
# Build the CleanTV launcher APK, install it on the TV and make it the default home.
#
# Android TV / Google TV only. The stock launcher is left alone unless you ask for it to be disabled:
#   DISABLE_STOCK=1 TV_HOST=192.168.1.50 ./build-and-install.sh      (STOCK_LAUNCHER defaults to
#   com.google.android.tvlauncher; set it for other boxes). Install first, disable second: with no launcher the
#   Home button lands on a factory test app. `tools/restore.sh` or `pm enable <package>` undoes it.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
PY=../tools/venv/bin/python
APK=app/build/outputs/apk/debug/app-debug.apk
"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk
MAKER=$("$PY" -I ../tools/tv.py 'getprop ro.product.manufacturer' | tr -d '\r\n')
if [ "$MAKER" = "Amazon" ]; then
  echo "This is a Fire TV. CleanTV no longer supports Fire OS (it guards its home screen); nothing was installed." >&2
  exit 1
fi
printf '%s\n' \
  "pm install -r /data/local/tmp/tvhome.apk" \
  "rm -f /data/local/tmp/tvhome.apk" \
  "cmd package set-home-activity --user 0 com.gathin.tvhome" \
  "pm grant com.gathin.tvhome com.android.providers.tv.permission.READ_EPG_DATA" \
  "cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.MediaHistoryService" \
  | "$PY" -I ../tools/tv.py --batch
if [ "${DISABLE_STOCK:-0}" = "1" ]; then
  "$PY" -I ../tools/tv.py "pm disable-user --user 0 $STOCK_LAUNCHER"
  echo "Installed, and $STOCK_LAUNCHER is disabled. Press Home on the remote."
else
  echo "Installed. Press Home and choose CleanTV (the stock launcher was not touched)."
fi