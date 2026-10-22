#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
adb install -r app/build/outputs/apk/debug/betterlauncher-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/betterlauncher-debug-androidTest.apk
result=$(adb shell am instrument -w com.pierspad.betterlauncher.debug.test/app.olauncher.FeedbackRegressionInstrumentation)
printf '%s\n' "$result"
# am instrument may exit 0 even when the custom runner reports a failed check.
[[ "$result" != *"FAIL:"* && "$result" != *"INSTRUMENTATION_FAILED"* ]]
[[ $(printf '%s\n' "$result" | grep -c '^PASS:') -eq 12 ]]
