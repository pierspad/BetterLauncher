# Issues #3–#7: findings and regression checks

Assessment completed on 30 October 2026. The reports describe real failures;
closed status alone was not used as evidence of a fix.

## Issue status

| Issue | Finding | Closure evidence and limits |
| --- | --- | --- |
| [#3](https://github.com/pierspad/BetterLauncher/issues/3) — contacts denial | Previously fixed; keep closed. | The reporter's 1.5.0 retest confirms two denials, working drawer/search and process restart with access still denied. A fresh optimized-release retest also passes two denials (including USER_FIXED), process restart and installed-app search opening Android Settings. Secondary Android-settings search was disabled to make `Set` a unique installed-app match. |
| [#4](https://github.com/pierspad/BetterLauncher/issues/4) — file backup/restore | Reopened during investigation, then eligible for closure after the new full round trip. | Picker handling was fixed in `527e72d6`; the subsequent SettingsFragment lifecycle failure was fixed in `7425f409`. The 1.6.0 settings crash prevented verification of that second fix. With #7 corrected, both the reporter's original backup and a newly exported file restore successfully, including activity recreation and return Home. |
| [#5](https://github.com/pierspad/BetterLauncher/issues/5) — invalid text size | Previously fixed; keep closed. | Reporter confirmed rejection of the invalid string and numeric 2.0 without changing stored preferences in 1.5.0. Current Android instrumentation also checks invalid types/ranges against the complete configuration. A new optimized-release invalid-string check retains size 1.5 and all exported settings except the normal hint counter. |
| [#6](https://github.com/pierspad/BetterLauncher/issues/6) — cooldown retry | Previously fixed; keep closed. | Reporter's 1.5.0 retest confirms retry 0 on exit, retry 1 without extending the deadline, natural expiry and reopening. Current instrumentation checks duplicate session exits, first/second retry and simulated deadline expiry. Natural five-minute expiry was not repeated in this review. |
| [#7](https://github.com/pierspad/BetterLauncher/issues/7) — settings crash | Reproduced and corrected in this change. | Same fatal focus stack reproduced on the original code. New attached-window regression fails before the change and passes afterwards; settings search and file restoration work in the locally signed optimized release build. |

## Downloaded evidence

Both ZIP attachments were downloaded from the issue comments:

- [1.5.0 retest and evidence](https://gitlab.com/-/project/36528/uploads/60076d3baff588939be65088511065a1/betterlauncher-1.5.0-retest-report-and-evidence.zip): 92 manifest checksums verified.
- [1.6.0 settings crash and evidence](https://gitlab.com/-/project/36528/uploads/b1be1c65d5ea574e4853588aeecfd7ae/betterlauncher-1.6.0-settings-crash-report-and-evidence.zip): 13 manifest checksums verified.

The 1.5.0 logs identify `SettingsFragment` in the uninitialized ViewModel failure.
The 1.6.0 logs instead show framework focus/accessibility traversal failing with
`parameter must be a descendant of this view`. These are separate failures.
The [F-Droid discussion](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/44511)
correctly distinguishes the blocked restore retest from a recurrence of the old
restore exception.

## Root cause and fix for #7

`SettingsSection` moves the title and rows into a new header/body. Some XML cards
have `animateLayoutChanges` enabled. Removing their children schedules disappearing
animations; after the children join their new containers, animation completion
clears their parent references. A container then lists a child whose parent chain
no longer leads back to it. Both focus sorting and accessibility node collection
can throw when converting that child's coordinates.

Disable the card's layout transition while moving its children, then restore it
once the new structure is complete. Search, collapse state, listeners and later
layout animations remain available.

The old settings regression inflated a detached layout, which did not exercise
this asynchronous animation completion. The new regression attaches the layout
to an activity, builds sections after a layout pass, waits past animation completion,
and verifies every child/parent relationship, accessibility node creation, focus
collection and the editor input connection in both layout orientations.

## Local verification

Base source: `bd3b9849` (1.6.0 / 139), plus this change.
Target: Android 17 / API 37 x86_64 emulator, 1080 × 2400, density 420.
Animations were enabled for the before/after reproduction.

- Before: tapping settings search reproduced the fatal `FocusFinder` stack.
- Before: new test failed with `Broken settings parent for AppCompatTextView in landscape=false`.
- After: all 14 Android instrumentation groups pass, including the attached hierarchy check in portrait and landscape, invalid backup rejection, cooldown state and fragment destruction before view creation.
- Debug build/lint and optimized release build/lint pass.
- Release-version Node tests and all 16 publishing Python tests pass.
- [Remote CI](https://github.com/pierspad/BetterLauncher/actions/runs/40975905275) verifies the fix commit `7e48441d`: build/lint and all 14 instrumentation groups also pass on Android 10 / API 29. CI disables system animations; the failing-before/passing-after animation reproduction was performed locally on API 37 with animations enabled.

Commands:

```sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
bash scripts/run-android-regressions.sh
./gradlew assembleRelease lintRelease
node --test scripts/test-bump-version.js
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

For release UI testing the unsigned optimized APK was signed with a local Android
debug key and installed under the production package name. This is a local test
artifact, not an official release or an independently built F-Droid artifact.
SHA-256: `f802fe68374e8379886a18c22d192bf6e25c15038cd10a336f99389ed8f2e7ce`.

### File picker and restore checks

1. Debug: save through DocumentsUI produced a 3,827-byte valid JSON file, exactly
   matching the editor. Load the reporter's actual 5,748-byte backup through
   DocumentsUI and verify the complete editor text before applying.
2. Apply, allow recreation, return Home: stored light theme and text size 1.5
   restored; no crash; launcher remained the preferred Home activity.
3. Optimized release: the original reporter backup also loaded and applied without
   a crash. Its AOSP Files/Clock package names are absent on this Google emulator;
   their unavailable Home entries are not evidence of a restore failure.
4. For a functional control, adapt those package identities to installed Google
   Files/Clock/Photos, retaining the activity class names. Restore that fixture,
   then export through DocumentsUI. The resulting 8,064-byte file exactly matches
   the editor.
5. Apply a different baseline, then load that actual 8,064-byte export through
   DocumentsUI, verify the complete editor text, and apply it. Settings, Files and
   Clock return to Home; the restored left swipe opens Google Clock.

6. Cancel Save with Back: complete editor text remains identical. Load a copy of
   the actual release export with only `TEXT_SIZE_SCALE.v` changed to `"invalid"`,
   verify that editor input, and apply. Reopen the editor to export actual stored
   settings: text size remains 1.5; the only difference from the pre-attempt export
   is the normal hint counter (10 to 11).

### Official 1.6.1 release confirmation

The [complete release workflow](https://github.com/pierspad/BetterLauncher/actions/runs/40975905275)
finished successfully, including verification, GitHub release and Google Play
publishing. The generated release is [1.6.1 / 140](https://github.com/pierspad/BetterLauncher/releases/tag/v1.6.1).

After uninstalling the locally signed production-package test app, install the
actual official GitHub release APK on the emulator, select it as default Home and
decline optional accessibility. Open settings, focus search, search for Import,
load the previously exported 8,064-byte file through DocumentsUI and verify its
complete editor text. Apply, allow recreation and return Home: Settings, Files
and Clock are present; the restored left swipe opens Google Clock. The preferred
Home activity remains BetterLauncher and the crash buffer is empty.

Official APK SHA-256:
`1f1d5ffe245892d03bb0283ba443d677b4db9fc4279ce2fca669179effb9fb00`.
This confirms the shipped GitHub binary; it remains separate from an F-Droid build.

## Boundaries

These are bounded emulator results operated through ADB and UI Automator. They do
not establish a new physical Pixel/GrapheneOS or Redmi/HyperOS result, every Android
version, every storage provider, or every possible imported setting. No current
F-Droid-built APK was tested and F-Droid acceptance/reproducibility is a separate
question. The previously resolved issues also retain their reporter-confirmed
results; none of this evidence should be misrepresented as a fresh physical-device
retest by this change.
