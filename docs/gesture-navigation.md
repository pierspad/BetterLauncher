# Gesture navigation on Xiaomi: feasibility investigation

## Recommendation

Try Ogesture as a separate companion before integrating its engine into
BetterLauncher. The engine can provide Back, Home and Recents on a Xiaomi with
three-button navigation, but it does not restore Xiaomi's native launcher/gesture
integration or remove the three-button bar. For a user whose primary requirement
is reclaiming that bar's screen space, Ogesture alone does not solve the problem.
There is no reason to enable this on devices whose native gestures already work.

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

## A suitable integrated design, if later pursued

Keep the feature explicitly off by default and do not auto-enable it based only
on the manufacturer. Offer it as an experimental navigation aid for users who
choose it. Preserve native navigation, provide an immediate disable option,
per-app exclusions and separate left/right/bottom controls. Do not bundle a
navigation-bar-hiding ADB command as an ordinary setup step.

Prefer an isolated gesture service/controller to extending the current
`MyAccessibilityService`: BetterLauncher's existing service reads window content
for app limits and lock behaviour, whereas gesture navigation can use much less
access. Lifecycle and user consent must be independent, and switching to another
launcher should remove BetterLauncher's gesture overlays. Reuse the controller
concept without adding upstream's entire Compose UI/DataStore stack solely for
this feature. BetterLauncher supports API 24; upstream requires API 29, so all
entry points and declarations need an appropriate compatibility design.

An integration changes the declared accessibility purpose: update in-app consent,
privacy policy, store listing and Play accessibility declaration before release.
Ogesture is AGPL-3.0 and BetterLauncher GPL-3.0; direct code reuse must preserve
upstream notices/source obligations and account for the combined-work provisions.
No upstream code has been copied into BetterLauncher's app in this branch.

## Connected Xiaomi verification

The connected Xiaomi 11T Pro (`vili`, model 2107113SG) reports Android 14 and
HyperOS build `V816.0.26.0.UKDEUXM`. BetterLauncher debug is the HOME role holder;
`force_fsg_nav_bar=0` and `navigation_mode=0` confirm three-button mode.

The unmodified Ogesture checkout built successfully with `assembleDebug` and
passed `testDebugUnitTest` (8 tests, no failures). Installation on the phone was
rejected by Xiaomi's USB installation confirmation with
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. No gesture action
was tested on-device, and no Ogesture package remains installed. Accessibility
settings were checked/restored; the original default launcher and navigation
mode remain in place. Do not interpret the build/unit-test success as evidence
that Home, Back, Recents, touch replay or process recovery work on this device.

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
