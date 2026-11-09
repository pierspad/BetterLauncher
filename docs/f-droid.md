# F-Droid

[BetterLauncher is available on F-Droid](https://f-droid.org/packages/com.pierspad.betterlauncher/). The [official build recipe](https://gitlab.com/fdroid/fdroiddata/-/blob/master/metadata/com.pierspad.betterlauncher.yml) uses `AutoUpdateMode: Version` and checks stable `vX.Y.Z` tags for the version fields in `gradle.properties`. F-Droid detects new stable releases automatically, then builds, verifies and publishes them on its own schedule.

Descriptions and images live in `fastlane/metadata/android/`. F-Droid imports them from the source revision of the latest release it knows about. Include listing changes in a stable release to make them available for import.

The `Binaries` URL points to `betterlauncher-release.apk` in the matching GitHub release. The locally built unsigned APK must reproduce that signed APK after signature copying. Keep the release signing key and avoid build timestamps, changing VCS metadata or machine paths in release artifacts.
