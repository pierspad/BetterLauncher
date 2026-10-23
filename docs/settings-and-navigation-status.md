# Settings and Xiaomi navigation work status

## Delivered

- Screen-time overcount fixed and released as v1.5.4 (overlapping foreground
  sessions, screen-off/keyguard and missing-history handling); regression suite
  covers these cases. A specific 3h/10h report still needs that device’s events
  to establish which path occurred.
- Drawer app actions use two aligned rows with readable labels and localized
  home-slot labels (commit 8bad673b).
- Local ignored upstream checkouts and an isolated research branch are in place.
  Research pushes cannot publish Play releases. Dev releases target beta, and
  manual publication from dev is now also forced to beta.
- Settings have a fixed branded header with cutout spacing and a 48dp Close
  button. Close also dismisses the search keyboard and returns to Home.
- Search has a magnifying glass and themed rounded background. It indexes the
  current app language plus English, preserving translated display labels and
  conditional row visibility. The newly added settings/help labels are present
  in all 23 language resource sets.
- The battery/accessibility panel has icons, explanations, restricted-settings
  guidance and direct links to accessibility, battery, autostart and app info.
  Help uses the app’s own dialog layout and restores the shield icon.
- Setup indicators track the accessibility permission. Vendor battery/autostart
  steps require explicit confirmation, rather than falsely assuming that opening
  their pages completes them.
- The opacity preview clips both its transparent hole and its labels/icons to
  the scrolling viewport, keeping the fixed header and search unobscured.
- Experimental gestures are off by default and shown only on supported Xiaomi,
  Redmi and Poco devices. The switch uses the same blue checked tint as other
  switches, with no redundant enabled label. One BetterLauncher accessibility
  service handles gestures, screen locking and app limits, with updated consent.
- Navigation-mode settings remain reachable with the experiment off. The bottom
  gesture zone no longer intercepts system navigation buttons. Display changes
  rebuild gesture zones. Backup import disables the experiment and resets
  vendor-setting confirmations.
- The phone’s original app assignments/settings were restored after the signing
  change. Later updates use the same debug key and install in place; no fresh
  uninstall is needed. Olauncher and Ogesture were removed as requested.

## Verification for this update

`assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` passed. All 14 Android
regression groups passed on the emulator, including screen time, translated
settings search, portrait/landscape layouts, backup and drawer regressions.

The updated APK was installed on the Xiaomi with `adb install -r`. Preference
comparison against the fresh pre-update snapshot found no changed keys. System
three-button navigation was confirmed active and experimental gestures off.
The fresh backup and original recovery files remain in ignored local storage.
An emulator UI check confirmed that English search finds the localized battery
row, the Xiaomi panel stays hidden on a non-Xiaomi device, and Close returns to
Home even while the search keyboard is open.

The new integrated service also passed a short physical Xiaomi check with system
buttons visible: Home from three vertical points in the bottom zone, Back from
the left edge and swipe-and-hold for Recents. Window geometry confirmed that the
bottom zone ends above the system button bar. Cleanup confirmed the overlays
were removed, experimental gestures were off and three-button mode remained
active. BetterLauncher’s accessibility service and other service selections were
preserved after the test.

## Still requires physical-device follow-up

Reliable Home gestures in daily use are **not yet demonstrated**. Earlier tests
on the old build exercised Back/Home/Recents, but subsequent user reports showed
that Home and navigation recovery were unreliable. The new zone geometry passed the three-button-mode smoke check above, but still
needs physical tests of the display edge in gesture mode, keyboard, rotation,
lock/unlock and service restart. Keep system buttons available during these tests. No further attempt
to hide navigation buttons was made in this update.

Before any store release of the gesture feature, review the public privacy
policy and Play accessibility declaration for the expanded service purpose.
This update is a local development commit, not a Play release.
