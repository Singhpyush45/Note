#!/usr/bin/env bash
# Runs inside the emulator: instrumented tests, then a smoke test of the minified release build.
set -u
status=0

# A test that fails without a message usually means the app process died (native crash, killed for memory).
# Put the device's crash buffer, low-memory kills and the test runner's last lines into the CI annotations.
crash_report() {
  local text
  text=$( {
    echo "== crash buffer (Java frames first) =="; adb logcat -d -b crash | grep -E "Abort message|name:|  at |#[0-9]+ pc .*(\.apk|\.oat|\.odex|\.vdex|base\.|androidx|ink|hwui)" | head -n 120
    echo "== process deaths / low memory =="; adb logcat -d | grep -E "lowmemorykiller|Low on memory|has died|Process .* \(pid|FATAL|SIGSEGV|SIGABRT|OutOfMemory" | tail -n 30
    echo "== TestRunner =="; adb logcat -d -s TestRunner | tail -n 20
  } 2>/dev/null )
  text=${text//'%'/'%25'}; text=${text//$'\r'/}; text=${text//$'\n'/'%0A'}
  echo "::error title=$1: device logs::${text:0:60000}"
}

# In-app instrumented tests, then black-box lifecycle tests (process death, force-stop) from :e2e.
# Two separate Gradle runs on purpose: in one run Gradle executes them in parallel on the same emulator,
# and the e2e tests' `pm clear` / app launches would collide with the other suite.
adb logcat -c || true
./gradlew connectedDebugAndroidTest -x :e2e:connectedDebugAndroidTest --continue 2>&1 | tee instrumented.log
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
  status=1
  crash_report "In-app instrumented tests"
fi
adb logcat -c || true
./gradlew :e2e:connectedCheck --continue 2>&1 | tee -a instrumented.log
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
  status=1
  crash_report "e2e tests"
fi

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
