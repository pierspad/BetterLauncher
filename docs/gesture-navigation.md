# Gesture navigation on Xiaomi: feasibility investigation

## Current implementation and recommendation

BetterLauncher now has an experimental, explicitly opt-in gesture controller on
Xiaomi/Redmi/Poco devices running Android 10 or later. Other devices do not see
this panel. The controller shares BetterLauncher’s existing accessibility service
with screen locking and app limits; no companion app or second accessibility
entry is required. Updated, versioned consent describes all three purposes.

This provides accessibility Back, Home and Recents actions, not Xiaomi’s native
Quickstep integration or its animations. BetterLauncher never writes navigation
settings or hides the system buttons. The navigation settings shortcut is always
available, including when the experiment is disabled. Keep three-button mode as
the reliable fallback until a longer physical-device test has passed.

HyperOS can report an old 130px three-button inset after `navigation_mode`
changes to gestures. The old zone therefore ended above the physical bottom.
`GestureNavigationMode` now resolves the selected mode independently of those
insets. Gesture mode ignores stale navigation insets entirely, while three-button
mode keeps touch zones clear of the bar. A content observer handles mode changes
and the geometry cache includes that mode. Back swipes show a separate,
non-touchable blue circle/arrow window that is removed on release or cancellation.

Physical Xiaomi tests passed Home from the display edge, swipe-and-hold for
Recents, side Back with visual feedback, and live transitions between system
buttons and gestures. These checks do not establish compatibility with every app
or long-term process survival. See the [work status](settings-and-navigation-status.md)
for the exact cases and remaining checks.

## Sources inspected

Local upstream clones are deliberately ignored under `Olauncher-Ogesture/`:

- Ogesture `main`, commit `a0001e7` (18 September 2026).
- Olauncher `master`, commit `f8e8d64`. Upstream has `master`, not `main`; the
  existing sibling checkout was moved here and fast-forward checked, including
  its preexisting untracked `.directory` file.

The current Olauncher source contains home-screen swipe listeners, not Ogesture's
system-wide navigation engine. Importing newer Olauncher code would not provide
this feature. The web-indexed Ogesture README described an older architecture;
the current checkout is the source of truth for this investigation.

## How current Ogesture works

`EdgeGestureAccessibilityService` owns `EdgeOverlayController`. Thin
`TYPE_ACCESSIBILITY_OVERLAY` windows cover the left/right edges (16dp thick,
80% of the height) and bottom (12dp thick, 80% of width). `SwipeDetector` turns
inward swipes into Back, an upward swipe into Home, and upward swipe plus hold
into Recents via `performGlobalAction`.

The current version does **not** request `SYSTEM_ALERT_WINDOW`, use a foreground
service or require a boot receiver. Its accessibility configuration reads only
window-state events and cannot retrieve window content. It uses package names
for app exclusions and ignores keyboard package changes. Its battery requirement
checks Android's doze whitelist; this is not proof that all Xiaomi battery or
autostart restrictions are disabled.

The overlay is offset above/beside the navigation bar. There is no setting write
for `force_fsg_nav_bar`, privileged navigation API or native Quickstep integration.
Thus the three buttons remain available as a fallback. Merely making an app the
HOME role holder does not grant system launcher animation/overview privileges.

## User experience and maintenance costs

- An overlay must consume an edge touch before it knows whether it is a swipe.
  Unused touches are replayed through `dispatchGesture`. This adds latency and
  changes touch semantics; replayed taps have a 50–60ms duration floor/cap, side
  indicator overlap may add 65ms, and held touches can be capped at 600ms (or a
  longer system long-press timeout). This matters for drawing, drag-and-drop,
  games and press-to-record controls near edges.
- Some apps reject injected touches. The exclusion list disables all gesture
  zones in those apps, returning to the native three-button fallback. It is not
  a promise of native-touch equivalence or compatibility with every secure app.
- Multi-touch and cancelled streams have their own handling; the first touch
  intercepted by a zone cannot always be reconstructed perfectly for an app.
- Rotation, keyboard, landscape navigation-bar placement, full-screen games,
  lock screen, process death, permission revocation and switching default
  launcher need device tests. Existing upstream unit tests cover zone mapping,
  not these runtime behaviours.
- The dispatcher currently ignores the Boolean returned by `performGlobalAction`.
  A successful gesture detection is therefore not proof the system action ran.
- This is an accessibility service kept alive across apps; CPU/battery cost and
  HyperOS process survival require a longer soak test, not just swipe checks.

## Integrated implementation

`XiaomiGestureController` owns the overlay lifecycle within
`MyAccessibilityService`. It checks the device family, explicit preference,
default launcher, interactive state and keyguard before attaching windows.
Switching launchers, locking the screen or disabling the option removes them.
The service checks consent before connecting. Backup import always disables the
experiment so restoring data cannot silently enable global touch interception.
The controller does not read window contents; the shared service still has that
capability for existing lock/app-limit behaviour, as explained by its disclosure.

The settings guide checks the accessibility permission directly. Battery optimization is checked automatically with the public Android
API on non-Xiaomi devices. Xiaomi battery and autostart steps use explicit user
confirmation after opening their vendor settings: Android’s Doze allowlist does
not certify Xiaomi’s separate restriction policy. These confirmations are reset on backup import. The guide must not
claim that opening a system page proves a restriction has been removed.

Per-app exclusions and separate edge controls are not implemented. Touch replay,
keyboard, rotation, secure apps and recovery after process death still need
extended physical-device validation before promoting the experiment to stable.
Any store release also requires reviewing the published privacy policy and Play
accessibility declaration for the expanded purpose. This research branch is not
being published to Play.

## Physical-device verification

Olauncher and Ogesture were uninstalled at the user’s request. BetterLauncher’s
settings and app assignments were restored during the earlier signing-key
replacement. Subsequent installations update in place with the same debug key.
The old separate gesture accessibility entry has been removed in favor of the
shared BetterLauncher service.

The latest validation used the user’s gesture mode and tested the physical bottom
at 2398px on a 2400px screen. Home, Recents and visual Back feedback passed. A
three-button transition confirmed that the touch zone ended above its 130px bar,
then switching back restored the physical-edge zone. Test cleanup restored the
starting navigation mode; later user choices are not overwritten.

PowerKeeper’s exported battery-profile provider on this firmware requires the
signature/system `miui.permission.powerkeeper.HIDDEN_MODE_PROVIDER` permission.
A regular app cannot use its direct profile state. The guide explains the manual
fallback and unknown vendor status is shown neutrally instead of claiming a
permission is missing.

## Release isolation

`codex/gesture-research` runs CI on push and does not trigger Release.
Explicit job guards also prevent manual Release/Publish Play runs from this
branch. `dev` already maps prereleases to Play's `beta` open-testing track, while
`main` maps stable releases to production. Testing-track enrolment must be set up
in Play Console; an app's beta programme is separate from the Android beta
programme. See [release setup](google-play-release.md).

## References

- [Ogesture source](https://github.com/tanujnotes/Ogesture)
- [Olauncher source](https://github.com/tanujnotes/Olauncher)
- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)
- [Play AccessibilityService requirements](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Play testing tracks](https://developers.google.com/android-publisher/tracks)
- [GPL and AGPL combination FAQ](https://www.gnu.org/licenses/gpl-faq.en.html)
