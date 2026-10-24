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
  in all 22 language resource sets.
- The battery/accessibility panel has icons, explanations, restricted-settings
  guidance and direct links to accessibility, battery, autostart and app info.
  Help uses the app’s own dialog layout and restores the shield icon.
- Setup indicators track the accessibility permission. Battery optimization is
  checked automatically using Android’s public API on non-Xiaomi devices. Xiaomi
  battery/autostart steps retain explicit confirmation, because the Android Doze
  allowlist does not certify Xiaomi’s separate policy. Opening a system page
  alone never marks a step complete.
- The opacity preview clips both its transparent hole and its labels/icons to
  the scrolling viewport, keeping the fixed header and search unobscured.
- Experimental gestures are off by default and shown only on supported Xiaomi,
  Redmi and Poco devices. The switch uses the same blue checked tint as other
  switches, with no redundant enabled label. One BetterLauncher accessibility
  service handles gestures, screen locking and app limits, with updated consent.
- Enabling gestures reuses the existing accessibility grant and requests setup
  only if consent or the service permission is missing. Granting the shared
  permission after opting in activates gestures on return. Gesture labels no
  longer include the experimental suffix; step numbers use `1)` notation.
- Navigation-mode settings open directly, with no confirmation or “Open link”
  action, and remain reachable with the experiment off. The bottom
  gesture zone no longer intercepts system navigation buttons. Display changes
  rebuild gesture zones. Backup import disables the experiment and resets
  vendor-setting confirmations.
- The phone’s original app assignments/settings were restored after the signing
  change. Later updates use the same debug key and install in place; no fresh
  uninstall is needed. Olauncher and Ogesture were removed as requested.

## Verification for this update

`assembleDebug`, `assembleDebugAndroidTest` and `lintDebug` passed. All 14 Android
regression groups passed on the emulator, including the new stale-navigation-
inset regression, screen time, search, backup and drawer checks. The installed
phone APK hash matches this validated build.

The Xiaomi reported `navigation_mode=2` with the gestural overlay enabled, but
still supplied a 130px three-button inset. The old bottom touch zone ended at
2270px on its 2400px display, missing swipes starting at the physical edge.
The controller now uses the selected navigation mode, ignores stale navigation
insets in gesture mode, and observes mode changes without restarting the app.

Physical checks with hidden buttons passed:

- Home swipes starting at y=2398, 2380 and 2336px, with the zone ending at 2400px.
- An upward swipe followed by a stationary hold opened Xiaomi Recents.
- A side swipe displayed the new blue back indicator, returned Back, and removed
  its indicator on release. Visual inspection confirmed the bubble and arrow.
- Switching to three buttons moved the bottom zone above their bar at 2270px;
  switching back to gestures restored the physical-edge zone automatically.

Hold tests inject a continuous pointer sequence with one shared down-time.
Separate `adb input motionevent` commands do not reproduce the same real-finger
stream reliably; they previously gave a false negative for Recents.
Test cleanup restored the navigation mode selected before the test. Subsequent
user changes to navigation mode are preserved. No data reset or signing-key
replacement was needed; recovery snapshots remain in ignored local storage.

The accessibility guide is rewritten and localized, the navigation-mode button
has no setup number or status dot, and the redundant About/GitHub link is removed.
The guide remains searchable in the selected language and English. Unknown vendor
battery/autostart status uses a gray question mark instead of a yellow missing-
permission indicator; explicit manual confirmations remain available.

## Setup simplification follow-up

Gesture activation now reuses the shared accessibility permission and current
consent. Missing setup is requested only when needed; a pending opt-in remains
ready to activate when the user grants the shared service through either route.
The gesture help was updated in all 22 language resource sets to describe this.
Navigation settings open directly without a separate confirmation dialog.
Status refreshes restore and reapply the current search filter so conditional
setup controls cannot reappear in unrelated results.

A live emulator check changed Android’s battery allowlist off/on/off and verified
that the battery indicator followed all three transitions automatically. The
manual confirmation remained hidden even after visiting app settings.
Xiaomi’s vendor policy still uses an explicitly manual fallback: Android’s public
[PowerManager API](https://developer.android.com/reference/android/os/PowerManager#isIgnoringBatteryOptimizations(java.lang.String))
reports the power allowlist, not a verified Xiaomi battery profile.

## Xiaomi battery evidence

The connected phone’s PowerKeeper manifest exports
`com.miui.powerkeeper.configure` behind
`miui.permission.powerkeeper.HIDDEN_MODE_PROVIDER`, with protection level `0x3`
(signature/system). BetterLauncher’s normal installation does not hold this
permission. Accessibility permission does not grant access to that provider.
The direct Xiaomi battery profile cannot therefore be certified using the public
Android power allowlist check. Unknown is kept distinct from restricted, and a
manual confirmation is explicitly presented as a user observation.

## Still requires physical-device follow-up

Physical-edge Home, Back and Recents are verified in gesture mode, and mode
transitions are covered. Longer daily-use checks with keyboard, rotation,
lock/unlock and service restart remain useful before promoting the feature to
stable. Per-app gesture exclusions would also help games and edge-heavy apps.

Before any store release of the gesture feature, review the public privacy
policy and Play accessibility declaration for the expanded service purpose.
This update is a local development commit, not a Play release.
