#!/usr/bin/env bash
# Runs inside the emulator: instrumented tests, then a smoke test of the minified release build.
set -u
status=0

./gradlew connectedDebugAndroidTest --continue 2>&1 | tee instrumented.log
[ "${PIPESTATUS[0]}" -eq 0 ] || status=1

# Release smoke test: install the R8-minified APK, launch it, make sure it is still alive and didn't crash.
APK=app/build/outputs/apk/release/app-release.apk
PKG=com.appname.notes
adb logcat -c || true
adb install -r "$APK" 2>&1 | tee -a instrumented.log
adb shell am start -W -n "$PKG/.MainActivity" 2>&1 | tee -a instrumented.log
sleep 8
PID=$(adb shell pidof "$PKG" | tr -d '\r')
CRASH=$(adb logcat -d -b crash)
if [ -z "$PID" ] || [ -n "$CRASH" ]; then
  echo "RELEASE SMOKE TEST FAILED (pid='$PID')" | tee -a instrumented.log
  echo "$CRASH" | tee -a instrumented.log
  echo "BUILD FAILED" >> instrumented.log
  status=1
else
  echo "Release smoke test passed: process $PID alive, no crashes." | tee -a instrumented.log
fi
exit $status
