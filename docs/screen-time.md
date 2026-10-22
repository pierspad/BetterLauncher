# Screen time

The home screen counts foreground app use since local midnight, excluding
BetterLauncher itself. Time is counted once, even if app sessions overlap.
Screen-off (including ambient display), keyguard and reboot intervals are excluded.
The most recently resumed activity is tracked, so a delayed pause/stop from a
previous activity cannot close the current activity. Duplicate resumes do not
reset an active interval.

The previous 24 hours of usage events seed the midnight state. Only the portion
inside today is counted. Missing history produces no inferred usage: running
processes are not evidence of historical screen time. Live intervals end at the
query timestamp, and the existing refresh interval is one minute.

Previously, app intervals were merged only within each package and then added
across packages, without checking screen/keyguard state. Missing lifecycle events
could leave overlapping sessions. A fallback also assigned the entire period
from midnight to currently visible processes when no app events were found.
These paths could greatly overestimate usage.

The total is not guaranteed to match Digital Wellbeing exactly: system apps,
launchers, split-screen activity and manufacturer event omissions may be treated
differently. Diagnosing a specific user's discrepancy requires their device's
usage events; the reported 3h versus 10h alone does not identify which path ran.

## Regression checks

`ScreenTimeRegression.kt` runs in the Android regression suite, or standalone:

```sh
kotlinc app/src/main/java/app/olauncher/helper/usageStats/ScreenTimeCounter.kt app/src/androidTest/java/app/olauncher/ScreenTimeRegression.kt -include-runtime -d /tmp/screen-time-tests.jar
java -jar /tmp/screen-time-tests.jar
```

Android event semantics: https://developer.android.com/reference/android/app/usage/UsageEvents.Event
