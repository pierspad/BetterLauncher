# Wallpaper contrast and system-bar regressions

Follow-up to the [1.6.1 external retest](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/44511#note_4121995945).
That retest confirmed the earlier settings-search and file-restore fixes on an
Android 16 emulator and Pixel 9a/GrapheneOS Android 17. It also recorded controls
under the navigation strip in landscape and dark Home text on dark wallpaper
when Light theme was selected.

## Changes

- Apply stable navigation-bar and cutout insets to the whole settings screen,
  including cards and section toggles. Keep the existing top spacing for the
  header. The same horizontal/bottom protection keeps Home shortcuts and widgets
  away from system navigation; widget placement accounts for the inset content
  height.
- Select black or white Home text and shortcut tint using the system wallpaper
  colors composited with the user's chosen opacity scrim. Choose the foreground
  with the best worst-case contrast across the supplied palette colors. Refresh
  when wallpaper colors change and when Home starts. Rebuild app/folder icons
  with the current foreground tint.
- Preserve the selected app theme, stored opacity and themed dialogs. Do not
  recolor widgets or the reorder button's opaque surface. If wallpaper colors
  are unavailable, including Android 7, retain the theme foreground.

Wallpaper palette colors are representative samples, not per-pixel background
measurements. A mixed image can contain both bright and dark regions; this change
does not promise a contrast ratio at every screen coordinate. The existing
opposite-color text shadow remains in use.

## Verification

`WallpaperAndInsetsRegression` exercises Light and Dark configurations against
solid black, white and gray backgrounds at transparent, 5%, half and full scrim
opacity. The selected opaque label foreground meets 4.5:1 against each solid
composite. It verifies rendered text/hint colors and shortcut tint, the missing-
wallpaper fallback, and preservation of the reorder button's theme colors.

Geometry checks inflate both settings orientations, construct their collapsible
sections and apply left/right three-button navigation, bottom navigation and
cutout insets. Focusable settings controls and the scroll view remain inside the
safe horizontal bounds; the scroll viewport stays above the bottom bar. A Home
shortcut is also checked against both side bars. Synthetic fixtures populate both
legacy system/stable inset fields and typed inset fields: the compat builder
does not implement `setInsetsIgnoringVisibility` before API 30. The test asserts
that the intended insets are actually present before checking geometry.

Manual emulator checks use Android 17 / API 37 with three-button navigation,
including a 1280 × 720 / 240 dpi landscape viewport. Settings Close and the
section toggle sit left of the right navigation strip; settings search works and
the Close action returns Home. A Light-theme Home on the retained dark wallpaper
now displays white labels and shortcut icons. No physical-device retest of these
new visual changes is claimed.

Build and regression commands:

```sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug assembleRelease lintRelease
bash scripts/run-android-regressions.sh
```

F-Droid inclusion remains separate: its recipe must point to the stable release
containing these changes, then pass that MR's build/reproducibility checks and
maintainer review. GitHub release automation does not update the GitLab branch.

## Release and F-Droid completion

Published [v1.6.2 / 141](https://github.com/pierspad/BetterLauncher/releases/tag/v1.6.2),
source `52f1f8287978cee7055f56a1ab0e6913721eab96`. Both local API 29 and API 37
instrumentation runs pass all 14 groups. The corrected legacy-insets fixture also
passes [GitHub CI](https://github.com/pierspad/BetterLauncher/actions/runs/41108558260);
release creation and automatic Google Play publication succeeded.

The official APK was installed and checked on the API 37 emulator: Home shortcuts
and Settings Close remain outside the landscape navigation strip; search works;
Close returns Home. On the small landscape viewport, entering search opened the
IME in fullscreen mode; Back dismissed that IME before checking Close. This input
condition is separate from the safe-area geometry. An exact 8,064-byte backup
was then imported in portrait, restoring Settings/Files/Clock. The restored Settings
entry opened Android Settings and the crash buffer remained empty.

[fdroiddata !44511](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/44511)
was updated through commit `bd994d4794b9720d4a7c8a00ad5b1fe2a602ebb8`. Its build
and CurrentVersion fields now select 1.6.2 / 141 and the full release commit. The
upstream signing certificate is unchanged. All nine jobs in
[the new F-Droid pipeline](https://gitlab.com/pierspad/fdroiddata/-/pipelines/3009896422)
passed, including source checks, metadata formatting/lint, update detection,
`fdroid build` and `check apk`.

The unsigned APK was downloaded from the successful F-Droid build job. Copying
the official APK's signature onto that build with F-Droid's `apksigcopier` produced
an APK byte-identical to the official release, and `apksigner verify` succeeded.

- Official/reproduced signed APK SHA-256:
  `e666ec197528bde8c3f5a01213c943fd1d407a1660eb325be0b9358371053108`.
- F-Droid unsigned build SHA-256:
  `5b5c1a097bc8c134bb257410bda9ae8aec3d494c1cf01b1a9e556908b2e99890`.
- Signing certificate SHA-256:
  `215b0f09b8334911f8e283fa9cce3d3c400199d4ccb22bdf01ac2836c6a97ae8`.

The MR remains open for maintainer review and merge. Its `waiting-on-response`
label was not automatically changed by the push; green CI is not an inclusion
approval. No new physical-device runtime test of 1.6.2 is claimed here.
