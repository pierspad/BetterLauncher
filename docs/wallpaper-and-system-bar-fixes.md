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
